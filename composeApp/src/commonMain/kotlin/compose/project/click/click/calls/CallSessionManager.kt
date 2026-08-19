@file:Suppress("ktlint:standard:backing-property-naming")

package compose.project.click.click.calls // pragma: allowlist secret

import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.data.repository.AuthRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseChatRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

object CallSessionManager {
    /** Caller + up to seven other group members (eight total). */
    const val MAX_GROUP_CALL_MEMBERS = 8

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    internal val authRepository = AuthRepository()
    internal val coordinator = CallCoordinator()
    internal val callPushNotifier = CallPushNotifier()
    internal val internalCallManager = createCallManager()
    internal val outboundChannels = mutableMapOf<String, RealtimeChannel>()
    internal val subscribedOutboundUserIds = mutableSetOf<String>()
    internal val lazyOutboundUserIds = mutableSetOf<String>()

    internal var inboundChannel: RealtimeChannel? = null
    internal var inviteJob: Job? = null
    internal var responseJob: Job? = null
    internal var cancelJob: Job? = null
    internal var connectedJob: Job? = null
    internal var timeoutJob: Job? = null
    internal var realtimeWatchJob: Job? = null

    internal var currentUserId: String? = null
    internal var currentUserName: String? = null
    internal var pendingSystemInvite: CallInvite? = null
    internal var pendingSystemAction: SystemIncomingCallAction? = null
    internal val _activeInvite = MutableStateFlow<CallInvite?>(null)
    val activeInvite: StateFlow<CallInvite?> = _activeInvite.asStateFlow()
    internal var activeInviteValue: CallInvite? = null
        set(value) {
            field = value
            _activeInvite.value = value
        }

    internal val _overlayState = MutableStateFlow<CallOverlayState>(CallOverlayState.Idle)
    val overlayState: StateFlow<CallOverlayState> = _overlayState.asStateFlow()

    val callState: StateFlow<CallState> = internalCallManager.callState
    val callManager: CallManager
        get() = internalCallManager

    internal enum class SystemIncomingCallAction {
        Accept,
        Decline,
    }

    internal val chatRepository by lazy { SupabaseChatRepository(tokenStorage = createTokenStorage()) }

    /** Wall-clock ms when LiveKit reached [CallState.Connected]; used for call_log duration. */
    internal var callConnectedAtMs: Long? = null

    /** Public read of connect timestamp for in-call duration UI. */
    val connectedAtMs: Long?
        get() = callConnectedAtMs

    /** Ensures we only insert one `completed` call_log per connected session. */
    internal var completedCallLogInserted: Boolean = false

    /** Avoids parallel token fetch / LiveKit start from response + repeated "connected" signals. */
    internal val joinMutex = Mutex()
    internal var joinStartedCallId: String? = null

    /** Peer "connected" broadcast must fire once per call, not on every Connected state refresh. */
    internal var roomConnectedNotifiedCallId: String? = null

    /** Set when callee accepts via Realtime; lets early-joined callers leave ringback before peer is in-room. */
    internal var acceptedOutgoingCallId: String? = null

    internal var previousCallState: CallState = CallState.Idle
    internal var previousOverlayState: CallOverlayState = CallOverlayState.Idle

    /** When true, [CallState.Ended] must not replace a deliberate Idle overlay (user cancel while ringing). */
    internal var suppressEndedOverlay: Boolean = false

    init {
        scope.launch {
            // Use collect (not collectLatest): rapid Connecting/Connected/Ended churn must not
            // cancel mid-handler and leave the preview overlay stuck on Outgoing.
            callState.collect { state ->
                val overlayBeforeUpdate = _overlayState.value
                when (state) {
                    is CallState.Connecting -> {
                        // Early-join while still Outgoing: keep ringback + "Starting … ring" UI.
                        if (overlayBeforeUpdate !is CallOverlayState.Outgoing) {
                            CallRingtonePlayer.stop()
                        }
                    }

                    is CallState.Connected -> {
                        val invite = activeInviteValue
                        val waitingForAnswer =
                            overlayBeforeUpdate is CallOverlayState.Outgoing &&
                                !state.hasRemoteParticipant &&
                                (invite == null || acceptedOutgoingCallId != invite.callId)
                        val firstConnected = firstConnectedTransition(previousCallState)

                        if (waitingForAnswer) {
                            // Caller early-joined and is alone — keep ringback, signal readiness.
                            if (firstConnected && invite != null) {
                                notifyPeerRoomConnected(invite)
                            }
                        } else {
                            if (firstConnected || callConnectedAtMs == null) {
                                if (firstConnected) {
                                    PlatformHapticsPolicy.lightImpact()
                                }
                                callConnectedAtMs = Clock.System.now().toEpochMilliseconds()
                                completedCallLogInserted = false
                            }
                            CallRingtonePlayer.stop()
                            invite?.let { active ->
                                PlatformIncomingCallUi.dismissIncomingCall(active.callId)
                                if (firstConnected) {
                                    notifyPeerRoomConnected(active)
                                }
                            }
                            if (_overlayState.value is CallOverlayState.Connecting ||
                                _overlayState.value is CallOverlayState.Outgoing
                            ) {
                                _overlayState.value = CallOverlayState.Idle
                            }
                        }
                    }

                    is CallState.Ended -> {
                        tryInsertCompletedCallLog()
                        // A newer invite can be admitted while callState is still Ended (before Idle).
                        // Do not stop that ring or overwrite its Incoming overlay.
                        val overlayNow = _overlayState.value
                        if (overlayNow is CallOverlayState.Incoming) {
                            resetJoinGuards()
                        } else {
                            CallRingtonePlayer.stop()
                            activeInviteValue?.let { invite ->
                                PlatformIncomingCallUi.dismissIncomingCall(invite.callId, state.reason)
                            }
                            if (
                                !suppressEndedOverlay &&
                                CallOverlayTransitionPolicy.shouldPresentCallEndedOverlay(
                                    previousCallState = previousCallState,
                                    overlayState = overlayBeforeUpdate,
                                )
                            ) {
                                _overlayState.value =
                                    CallOverlayState.Ended(
                                        activeInviteValue,
                                        state.reason ?: "Call ended",
                                    )
                            }
                            resetJoinGuards()
                        }
                    }

                    CallState.Idle -> {
                        suppressEndedOverlay = false
                        if (previousCallState is CallState.Connected) {
                            tryInsertCompletedCallLog()
                        }
                        if (_overlayState.value !is CallOverlayState.Incoming &&
                            _overlayState.value !is CallOverlayState.Outgoing &&
                            _overlayState.value !is CallOverlayState.Connecting &&
                            _overlayState.value !is CallOverlayState.Ended
                        ) {
                            activeInviteValue = null
                        }
                        if (previousCallState is CallState.Ended || previousCallState is CallState.Connected) {
                            resetJoinGuards()
                        }
                    }
                }
                previousCallState = state
                previousOverlayState = _overlayState.value
            }
        }
    }

    internal fun firstConnectedTransition(previous: CallState): Boolean = previous !is CallState.Connected

    internal fun resetJoinGuards() {
        joinStartedCallId = null
        roomConnectedNotifiedCallId = null
        acceptedOutgoingCallId = null
    }

    /**
     * Caller-only: inserts a `call_log` row when the in-room session ends (covers Android hang-up → Idle
     * without [CallState.Ended], and iOS / disconnect paths that emit Ended).
     */
    internal fun tryInsertCompletedCallLog() {
        if (completedCallLogInserted) return
        val invite = activeInviteValue
        val uid = resolvedCurrentUserId()
        val startedAt = callConnectedAtMs
        if (invite == null || uid == null || startedAt == null) return

        if (uid != invite.callerId) {
            completedCallLogInserted = true
            callConnectedAtMs = null
            return
        }

        completedCallLogInserted = true
        callConnectedAtMs = null
        val durationSec =
            ((Clock.System.now().toEpochMilliseconds() - startedAt) / 1000L).toInt().coerceAtLeast(0)
        scope.launch {
            insertCallChatLog(invite.connectionId, uid, "completed", durationSec)
        }
    }

    internal fun insertCallChatLogAsync(
        connectionId: String,
        callStateKey: String,
        durationSeconds: Int,
    ) {
        val uid = resolvedCurrentUserId() ?: return
        scope.launch {
            insertCallChatLog(connectionId, uid, callStateKey, durationSeconds)
        }
    }

    internal suspend fun insertCallChatLog(
        connectionId: String,
        userId: String,
        callStateKey: String,
        durationSeconds: Int,
    ) {
        val metadata =
            buildJsonObject {
                put("call_state", callStateKey)
                put("duration_seconds", durationSeconds)
            }
        runCatching {
            chatRepository.sendMessageForConnection(
                connectionId = connectionId,
                userId = userId,
                content = "",
                messageType = "call_log",
                metadata = metadata,
            )
        }.onFailure {
            println("CallSessionManager: call_log insert failed: ${it.message}")
        }
    }

    fun bindUser(
        userId: String?,
        userName: String?,
    ) {
        if (userId.isNullOrBlank()) {
            clearUser()
            return
        }

        currentUserName = userName ?: "Click User"
        if (currentUserId == userId && inboundChannel != null) {
            return
        }

        clearSubscriptions()
        currentUserId = userId
        subscribeToIncoming(userId)
        watchRealtimeConnection(userId)
        processPendingSystemInviteIfPossible()
    }

    fun clearUser() {
        cancelCurrentCall(notifyPeer = false)
        internalCallManager.endCall()
        currentUserId = null
        currentUserName = null
        pendingSystemInvite = null
        pendingSystemAction = null
        activeInviteValue = null
        _overlayState.value = CallOverlayState.Idle
        clearSubscriptions()
    }

    fun startOutgoingCall(
        connectionId: String,
        otherUserId: String,
        otherUserName: String,
        videoEnabled: Boolean,
    ) {
        val userId = resolvedCurrentUserId() ?: return
        val callerName = currentUserName ?: "Click User"

        if (_overlayState.value !is CallOverlayState.Idle || callState.value !is CallState.Idle) {
            return
        }

        val now = Clock.System.now().toEpochMilliseconds()
        val invite =
            CallInvite(
                callId = "call-$now-${Random.nextInt(1000, 9999)}",
                connectionId = connectionId,
                roomName = "click-$connectionId-$now",
                callerId = userId,
                callerName = callerName,
                calleeId = otherUserId,
                calleeName = otherUserName,
                videoEnabled = videoEnabled,
                createdAt = now,
            )

        activeInviteValue = invite
        _overlayState.value = CallOverlayState.Outgoing(invite)
        CallRingtonePlayer.startOutgoing()

        scope.launch {
            sendInvite(invite)
        }
        scope.launch {
            callPushNotifier
                .notifyIncomingCall(invite)
                .onFailure { println("CallSessionManager: Failed to dispatch incoming call push: ${it.message}") }
        }
        // Join LiveKit while ringing so accept does not depend solely on Realtime "response".
        scope.launch {
            joinCall(invite)
        }

        timeoutJob?.cancel()
        timeoutJob =
            scope.launch {
                delay(30_000)
                if (activeInviteValue?.callId == invite.callId && _overlayState.value is CallOverlayState.Outgoing) {
                    sendCancel(invite, invite.calleeId, "missed")
                    insertCallChatLogAsync(invite.connectionId, "missed", 0)
                    failCall(invite, "No answer")
                }
            }
    }

    fun startOutgoingGroupCall(
        groupId: String,
        chatId: String,
        memberIds: List<String>,
        videoEnabled: Boolean,
    ) {
        val userId = resolvedCurrentUserId() ?: return
        val callerName = currentUserName ?: "Click User"

        if (_overlayState.value !is CallOverlayState.Idle || callState.value !is CallState.Idle) {
            return
        }

        val distinctMembers = memberIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (!distinctMembers.contains(userId)) {
            return
        }
        if (distinctMembers.size > MAX_GROUP_CALL_MEMBERS) {
            failCall(
                invite = null,
                reason = "Group calls are limited to $MAX_GROUP_CALL_MEMBERS people",
            )
            return
        }

        val calleeIds = distinctMembers.filter { it != userId }
        if (calleeIds.isEmpty()) {
            return
        }

        val now = Clock.System.now().toEpochMilliseconds()
        val roomName = "click-group-$groupId-$now"
        val groupInvite =
            GroupCallInvite(
                callId = "call-$now-${Random.nextInt(1000, 9999)}",
                groupId = groupId,
                chatId = chatId,
                roomName = roomName,
                callerId = userId,
                callerName = callerName,
                memberIds = distinctMembers,
                videoEnabled = videoEnabled,
                createdAt = now,
            )

        val primaryCalleeId = calleeIds.first()
        val invite =
            CallInvite(
                callId = groupInvite.callId,
                connectionId = groupId,
                roomName = roomName,
                callerId = userId,
                callerName = callerName,
                calleeId = primaryCalleeId,
                calleeName = "Group call",
                videoEnabled = videoEnabled,
                createdAt = now,
            )

        activeInviteValue = invite
        _overlayState.value = CallOverlayState.Outgoing(invite)
        CallRingtonePlayer.startOutgoing()

        scope.launch {
            for (calleeId in calleeIds) {
                val memberInvite = invite.copy(calleeId = calleeId)
                sendInvite(memberInvite)
                callPushNotifier
                    .notifyIncomingCall(memberInvite)
                    .onFailure {
                        println("CallSessionManager: Failed to dispatch group call push to $calleeId: ${it.message}")
                    }
            }
        }
        scope.launch {
            joinCall(invite)
        }

        timeoutJob?.cancel()
        timeoutJob =
            scope.launch {
                delay(30_000)
                if (activeInviteValue?.callId == invite.callId && _overlayState.value is CallOverlayState.Outgoing) {
                    for (calleeId in calleeIds) {
                        sendCancel(invite.copy(calleeId = calleeId), calleeId, "missed")
                    }
                    insertCallChatLogAsync(groupInvite.groupId, "missed", 0)
                    failCall(invite, "No answer")
                }
            }
    }

    fun acceptIncomingCall() {
        if (_overlayState.value is CallOverlayState.Connecting) return

        val invite = (_overlayState.value as? CallOverlayState.Incoming)?.invite ?: return

        timeoutJob?.cancel()
        CallRingtonePlayer.stop()
        PlatformIncomingCallUi.dismissIncomingCall(invite.callId)
        _overlayState.value = CallOverlayState.Connecting(invite)

        scope.launch {
            acceptAndJoinIncomingCall(invite)
        }
    }

    fun declineIncomingCall() {
        val invite = (_overlayState.value as? CallOverlayState.Incoming)?.invite ?: return

        timeoutJob?.cancel()
        CallRingtonePlayer.stop()
        PlatformIncomingCallUi.dismissIncomingCall(invite.callId, "Declined")
        scope.launch {
            sendResponse(invite, accepted = false, busy = false)
        }
        activeInviteValue = null
        _overlayState.value = CallOverlayState.Idle
        cleanupAfterCall()
    }

    fun cancelCurrentCall(notifyPeer: Boolean = true) {
        val invite = activeInviteValue
        val overlay = _overlayState.value
        timeoutJob?.cancel()
        CallRingtonePlayer.stop()
        suppressEndedOverlay = true
        if (invite != null) {
            PlatformIncomingCallUi.dismissIncomingCall(invite.callId)
        }
        activeInviteValue = null
        _overlayState.value = CallOverlayState.Idle
        internalCallManager.endCall()
        resetJoinGuards()
        // Peer notify after local UI clears — never block Cancel on Realtime.
        scope.launch {
            if (notifyPeer && invite != null) {
                when (overlay) {
                    is CallOverlayState.Outgoing ->
                        runCatching {
                            sendCancel(invite, invite.calleeId, "cancelled")
                        }
                    is CallOverlayState.Incoming ->
                        runCatching {
                            sendResponse(invite, accepted = false, busy = false)
                        }
                    is CallOverlayState.Connecting -> {
                        peerUserId(invite)?.let { peerId ->
                            runCatching { sendCancel(invite, peerId, "cancelled") }
                        }
                    }
                    else -> Unit
                }
            }
            releaseLazyOutboundChannels()
        }
    }

    fun endActiveCall() {
        val invite = activeInviteValue
        CallRingtonePlayer.stop()
        if (invite != null) {
            activeInviteValue = invite
            _overlayState.value = CallOverlayState.Ended(invite, "Call ended")
            PlatformIncomingCallUi.dismissIncomingCall(invite.callId, "ended")
        } else {
            activeInviteValue = null
            _overlayState.value = CallOverlayState.Idle
        }
        resetJoinGuards()
        scope.launch {
            if (invite != null) {
                peerUserId(invite)?.let { peerId ->
                    runCatching { sendCancel(invite, peerId, "ended") }
                }
            }
            delay(280)
            internalCallManager.endCall()
            releaseLazyOutboundChannels()
        }
    }

    fun dismissEndedCall() {
        if (callState.value is CallState.Ended) {
            internalCallManager.endCall()
        }
        activeInviteValue = null
        _overlayState.value = CallOverlayState.Idle
        cleanupAfterCall()
    }

    fun receiveIncomingPush(
        invite: CallInvite,
        autoAnswer: Boolean = false,
        autoDecline: Boolean = false,
    ) {
        pendingSystemInvite = invite
        pendingSystemAction =
            when {
                autoAnswer -> SystemIncomingCallAction.Accept
                autoDecline -> SystemIncomingCallAction.Decline
                else -> pendingSystemAction
            }

        val userId = resolvedCurrentUserId()
        if (userId != null && invite.calleeId != userId) {
            return
        }

        when (
            CallInviteAdmissionPolicy.decide(
                invite = invite,
                activeInvite = activeInviteValue,
                overlayState = _overlayState.value,
                callState = callState.value,
            )
        ) {
            CallInviteAdmissionPolicy.Decision.SameCall -> {
                // Keep pending Accept/Decline; do not send busy for FCM+Realtime duplicates.
                processPendingSystemInviteIfPossible()
                return
            }
            CallInviteAdmissionPolicy.Decision.Busy -> {
                scope.launch {
                    sendResponse(invite, accepted = false, busy = true)
                }
                // Only drop pending system action when this invite is a true conflict
                // (different call). Same-call Accept is handled above.
                pendingSystemInvite = null
                pendingSystemAction = null
                return
            }
            CallInviteAdmissionPolicy.Decision.Admit -> {
                activeInviteValue = invite
                _overlayState.value = CallOverlayState.Incoming(invite)
                CallRingtonePlayer.startIncoming()
                PlatformIncomingCallUi.showIncomingCall(invite)
                processPendingSystemInviteIfPossible()
            }
        }
    }
}
