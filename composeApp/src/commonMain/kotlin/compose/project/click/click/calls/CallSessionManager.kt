package compose.project.click.click.calls

import compose.project.click.click.PlatformHapticsPolicy
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.repository.AuthRepository
import compose.project.click.click.data.repository.SupabaseChatRepository
import compose.project.click.click.data.storage.createTokenStorage
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

@Serializable
data class GroupCallInvite(
    val callId: String,
    val groupId: String,
    val chatId: String,
    val roomName: String,
    val callerId: String,
    val callerName: String,
    val memberIds: List<String>,
    val videoEnabled: Boolean,
    val createdAt: Long,
)

@Serializable
data class CallInvite(
    val callId: String,
    val connectionId: String,
    val roomName: String,
    val callerId: String,
    val callerName: String,
    val calleeId: String,
    val calleeName: String,
    val videoEnabled: Boolean,
    val createdAt: Long,
) {
    fun counterpartName(currentUserId: String?): String {
        return if (currentUserId == callerId) calleeName else callerName
    }
}

@Serializable
private data class CallResponse(
    val callId: String,
    val connectionId: String,
    val responderId: String,
    val accepted: Boolean,
    val busy: Boolean = false,
)

@Serializable
private data class CallCancel(
    val callId: String,
    val connectionId: String,
    val senderId: String,
    val reason: String,
)

@Serializable
private data class CallRoomConnected(
    val callId: String,
    val connectionId: String,
    val userId: String,
)

sealed class CallOverlayState {
    data object Idle : CallOverlayState()
    data class Outgoing(val invite: CallInvite) : CallOverlayState()
    data class Incoming(val invite: CallInvite) : CallOverlayState()
    data class Connecting(val invite: CallInvite) : CallOverlayState()
    data class Ended(val invite: CallInvite?, val reason: String) : CallOverlayState()
}

object CallSessionManager {
    /** Caller + up to seven other group members (eight total). */
    const val MAX_GROUP_CALL_MEMBERS = 8

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val authRepository = AuthRepository()
    private val coordinator = CallCoordinator()
    private val callPushNotifier = CallPushNotifier()
    private val internalCallManager = createCallManager()
    private val outboundChannels = mutableMapOf<String, RealtimeChannel>()
    private val subscribedOutboundUserIds = mutableSetOf<String>()
    private val lazyOutboundUserIds = mutableSetOf<String>()

    private var inboundChannel: RealtimeChannel? = null
    private var inviteJob: Job? = null
    private var responseJob: Job? = null
    private var cancelJob: Job? = null
    private var connectedJob: Job? = null
    private var timeoutJob: Job? = null
    private var realtimeWatchJob: Job? = null

    private var currentUserId: String? = null
    private var currentUserName: String? = null
    private var pendingSystemInvite: CallInvite? = null
    private var pendingSystemAction: SystemIncomingCallAction? = null
    private val _activeInvite = MutableStateFlow<CallInvite?>(null)
    val activeInvite: StateFlow<CallInvite?> = _activeInvite.asStateFlow()
    private var activeInviteValue: CallInvite? = null
        set(value) {
            field = value
            _activeInvite.value = value
        }

    private val _overlayState = MutableStateFlow<CallOverlayState>(CallOverlayState.Idle)
    val overlayState: StateFlow<CallOverlayState> = _overlayState.asStateFlow()

    val callState: StateFlow<CallState> = internalCallManager.callState
    val callManager: CallManager
        get() = internalCallManager

    private enum class SystemIncomingCallAction {
        Accept,
        Decline,
    }

    private val chatRepository by lazy { SupabaseChatRepository(tokenStorage = createTokenStorage()) }

    /** Wall-clock ms when LiveKit reached [CallState.Connected]; used for call_log duration. */
    private var callConnectedAtMs: Long? = null

    /** Ensures we only insert one `completed` call_log per connected session. */
    private var completedCallLogInserted: Boolean = false

    /** Avoids parallel token fetch / LiveKit start from response + repeated "connected" signals. */
    private val joinMutex = Mutex()
    private var joinStartedCallId: String? = null

    /** Peer "connected" broadcast must fire once per call, not on every Connected state refresh. */
    private var roomConnectedNotifiedCallId: String? = null

    /** Set when callee accepts via Realtime; lets early-joined callers leave ringback before peer is in-room. */
    private var acceptedOutgoingCallId: String? = null

    private var previousCallState: CallState = CallState.Idle
    private var previousOverlayState: CallOverlayState = CallOverlayState.Idle

    /** When true, [CallState.Ended] must not replace a deliberate Idle overlay (user cancel while ringing). */
    private var suppressEndedOverlay: Boolean = false

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
                                _overlayState.value = CallOverlayState.Ended(
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

    private fun firstConnectedTransition(previous: CallState): Boolean =
        previous !is CallState.Connected

    private fun resetJoinGuards() {
        joinStartedCallId = null
        roomConnectedNotifiedCallId = null
        acceptedOutgoingCallId = null
    }

    /**
     * Caller-only: inserts a `call_log` row when the in-room session ends (covers Android hang-up → Idle
     * without [CallState.Ended], and iOS / disconnect paths that emit Ended).
     */
    private fun tryInsertCompletedCallLog() {
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

    private fun insertCallChatLogAsync(connectionId: String, callStateKey: String, durationSeconds: Int) {
        val uid = resolvedCurrentUserId() ?: return
        scope.launch {
            insertCallChatLog(connectionId, uid, callStateKey, durationSeconds)
        }
    }

    private suspend fun insertCallChatLog(
        connectionId: String,
        userId: String,
        callStateKey: String,
        durationSeconds: Int,
    ) {
        val metadata = buildJsonObject {
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

    fun bindUser(userId: String?, userName: String?) {
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
        val invite = CallInvite(
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
            callPushNotifier.notifyIncomingCall(invite)
                .onFailure { println("CallSessionManager: Failed to dispatch incoming call push: ${it.message}") }
        }
        // Join LiveKit while ringing so accept does not depend solely on Realtime "response".
        scope.launch {
            joinCall(invite)
        }

        timeoutJob?.cancel()
        timeoutJob = scope.launch {
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
        val groupInvite = GroupCallInvite(
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
        val invite = CallInvite(
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
                callPushNotifier.notifyIncomingCall(memberInvite)
                    .onFailure {
                        println("CallSessionManager: Failed to dispatch group call push to $calleeId: ${it.message}")
                    }
            }
        }
        scope.launch {
            joinCall(invite)
        }

        timeoutJob?.cancel()
        timeoutJob = scope.launch {
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
                    is CallOverlayState.Outgoing -> runCatching {
                        sendCancel(invite, invite.calleeId, "cancelled")
                    }
                    is CallOverlayState.Incoming -> runCatching {
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
        // Tear down media immediately so video hangup cannot freeze on last TextureView frame.
        internalCallManager.endCall()
        resetJoinGuards()
        scope.launch {
            if (invite != null) {
                peerUserId(invite)?.let { peerId ->
                    runCatching { sendCancel(invite, peerId, "ended") }
                }
            }
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

    fun receiveIncomingPush(invite: CallInvite, autoAnswer: Boolean = false, autoDecline: Boolean = false) {
        pendingSystemInvite = invite
        pendingSystemAction = when {
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

    private fun releaseLazyOutboundChannels() {
        val toRelease = lazyOutboundUserIds.toList()
        lazyOutboundUserIds.clear()
        for (userId in toRelease) {
            outboundChannels.remove(userId)?.let { channel ->
                scope.launch {
                    runCatching { SupabaseConfig.client.realtime.removeChannel(channel) }
                }
            }
            subscribedOutboundUserIds.remove(userId)
        }
    }

    private fun cleanupAfterCall() {
        resetJoinGuards()
        releaseLazyOutboundChannels()
    }

    /**
     * After a websocket drop, supabase-kt can leave channels marked SUBSCRIBED while the
     * server has forgotten them (pushes then return "unmatched topic"). Force a fresh
     * inbound join so subsequent call invites still arrive.
     */
    private fun watchRealtimeConnection(userId: String) {
        realtimeWatchJob?.cancel()
        realtimeWatchJob = scope.launch {
            var everConnected = false
            var lostConnection = false
            SupabaseConfig.client.realtime.status.collect { status ->
                when (status) {
                    Realtime.Status.DISCONNECTED,
                    Realtime.Status.CONNECTING,
                    -> {
                        if (everConnected) lostConnection = true
                    }

                    Realtime.Status.CONNECTED -> {
                        if (lostConnection && currentUserId == userId) {
                            lostConnection = false
                            // Let the SDK's rejoinChannels attempt finish, then force a clean join.
                            // Channel status can stay SUBSCRIBED after a drop while the server forgot
                            // the topic (pushes then fail with "unmatched topic").
                            delay(750)
                            if (currentUserId != userId) return@collect
                            println("CallSessionManager: Realtime reconnected — resubscribing call invites")
                            resubscribeIncoming(userId)
                        }
                        everConnected = true
                    }
                }
            }
        }
    }

    private fun resubscribeIncoming(userId: String) {
        inviteJob?.cancel()
        responseJob?.cancel()
        cancelJob?.cancel()
        connectedJob?.cancel()
        inviteJob = null
        responseJob = null
        cancelJob = null
        connectedJob = null

        val previous = inboundChannel
        inboundChannel = null
        scope.launch {
            previous?.let { ch ->
                runCatching { SupabaseConfig.client.realtime.removeChannel(ch) }
            }
            if (currentUserId == userId) {
                subscribeToIncoming(userId)
            }
        }
    }

    private fun subscribeToIncoming(userId: String) {
        val channel = SupabaseConfig.client.channel("calls:user:$userId")
        inboundChannel = channel

        inviteJob = scope.launch {
            channel.broadcastFlow<CallInvite>("invite").collectLatest { invite ->
                handleInvite(invite)
            }
        }

        responseJob = scope.launch {
            channel.broadcastFlow<CallResponse>("response").collectLatest { response ->
                handleResponse(response)
            }
        }

        cancelJob = scope.launch {
            channel.broadcastFlow<CallCancel>("cancel").collectLatest { cancel ->
                handleCancel(cancel)
            }
        }

        connectedJob = scope.launch {
            channel.broadcastFlow<CallRoomConnected>("connected").collectLatest { connected ->
                handleRoomConnected(connected)
            }
        }

        val inbound = channel
        scope.launch {
            try {
                inbound.subscribe(blockUntilSubscribed = true)
            } catch (_: Exception) {
                runCatching { inbound.subscribe() }
            }
        }
    }

    private fun clearSubscriptions() {
        realtimeWatchJob?.cancel()
        realtimeWatchJob = null
        inviteJob?.cancel()
        responseJob?.cancel()
        cancelJob?.cancel()
        connectedJob?.cancel()
        timeoutJob?.cancel()
        inviteJob = null
        responseJob = null
        cancelJob = null
        connectedJob = null
        timeoutJob = null

        inboundChannel?.let { channel ->
            scope.launch {
                runCatching { SupabaseConfig.client.realtime.removeChannel(channel) }
            }
        }
        inboundChannel = null

        outboundChannels.values.forEach { channel ->
            scope.launch {
                runCatching { SupabaseConfig.client.realtime.removeChannel(channel) }
            }
        }
        outboundChannels.clear()
        subscribedOutboundUserIds.clear()
        lazyOutboundUserIds.clear()
    }

    private fun handleInvite(invite: CallInvite) {
        val userId = resolvedCurrentUserId() ?: return
        if (invite.calleeId != userId) return

        when (
            CallInviteAdmissionPolicy.decide(
                invite = invite,
                activeInvite = activeInviteValue,
                overlayState = _overlayState.value,
                callState = callState.value,
            )
        ) {
            CallInviteAdmissionPolicy.Decision.SameCall -> {
                processPendingSystemInviteIfPossible()
                return
            }
            CallInviteAdmissionPolicy.Decision.Busy -> {
                scope.launch {
                    sendResponse(invite, accepted = false, busy = true)
                }
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

    private fun handleResponse(response: CallResponse) {
        val invite = activeInviteValue ?: return
        if (invite.callId != response.callId) return
        val overlay = _overlayState.value
        if (overlay !is CallOverlayState.Outgoing && overlay !is CallOverlayState.Connecting) return

        timeoutJob?.cancel()
        CallRingtonePlayer.stop()
        PlatformIncomingCallUi.dismissIncomingCall(invite.callId)

        when {
            response.accepted -> {
                acceptedOutgoingCallId = invite.callId
                when {
                    callState.value is CallState.Connected -> {
                        // Early-joined caller: leave ringback UI immediately.
                        _overlayState.value = CallOverlayState.Idle
                    }
                    _overlayState.value is CallOverlayState.Outgoing -> {
                        _overlayState.value = CallOverlayState.Connecting(invite)
                    }
                }
                scope.launch {
                    joinCall(invite)
                }
            }

            response.busy -> failCall(invite, "${invite.calleeName} is busy")
            else -> {
                insertCallChatLogAsync(invite.connectionId, "declined", 0)
                failCall(invite, "${invite.calleeName} declined the call")
            }
        }
    }

    private fun handleRoomConnected(connected: CallRoomConnected) {
        val invite = activeInviteValue ?: return
        if (invite.callId != connected.callId) return
        val uid = resolvedCurrentUserId() ?: return
        if (connected.userId == uid) return

        val overlay = _overlayState.value
        if (overlay !is CallOverlayState.Outgoing && overlay !is CallOverlayState.Connecting) return
        if (callState.value is CallState.Connected) return
        if (joinStartedCallId == invite.callId) return

        timeoutJob?.cancel()
        CallRingtonePlayer.stop()
        if (callState.value is CallState.Connected) {
            _overlayState.value = CallOverlayState.Idle
        } else if (_overlayState.value is CallOverlayState.Outgoing) {
            _overlayState.value = CallOverlayState.Connecting(invite)
        }
        scope.launch {
            joinCall(invite)
        }
    }

    private fun handleCancel(cancel: CallCancel) {
        val invite = activeInviteValue
        if (invite == null) {
            if (callState.value is CallState.Connected || callState.value is CallState.Connecting) {
                timeoutJob?.cancel()
                CallRingtonePlayer.stop()
                internalCallManager.endCall()
                _overlayState.value = CallOverlayState.Ended(null, "Call ended")
                cleanupAfterCall()
            }
            return
        }
        if (invite.callId != cancel.callId) return

        timeoutJob?.cancel()
        CallRingtonePlayer.stop()
        PlatformIncomingCallUi.dismissIncomingCall(invite.callId, cancel.reason)

        if (callState.value is CallState.Connected || callState.value is CallState.Connecting) {
            internalCallManager.endCall()
            activeInviteValue = invite
            _overlayState.value = when (cancel.reason) {
                "ended" -> CallOverlayState.Ended(invite, "Call ended")
                "missed" -> CallOverlayState.Ended(invite, "No answer")
                else -> CallOverlayState.Ended(invite, "Call ended")
            }
            cleanupAfterCall()
            return
        }

        when (_overlayState.value) {
            is CallOverlayState.Incoming,
            is CallOverlayState.Outgoing,
            is CallOverlayState.Connecting,
            CallOverlayState.Idle, // active call UI clears overlay to Idle while Connected
            -> {
                if (callState.value !is CallState.Idle) {
                    internalCallManager.endCall()
                }
                activeInviteValue = invite
                _overlayState.value = when (cancel.reason) {
                    "missed" -> CallOverlayState.Ended(invite, "No answer")
                    "ended" -> CallOverlayState.Ended(invite, "Call ended")
                    else -> CallOverlayState.Idle
                }
                cleanupAfterCall()
            }

            else -> Unit
        }
    }

    private fun groupIdFromInvite(invite: CallInvite): String? {
        val prefix = "click-group-${invite.connectionId}-"
        return if (invite.roomName.startsWith(prefix)) invite.connectionId else null
    }

    private suspend fun joinCall(invite: CallInvite) {
        joinMutex.withLock {
            if (joinStartedCallId == invite.callId) return
            if (callState.value is CallState.Connected) {
                joinStartedCallId = invite.callId
                return
            }
            joinStartedCallId = invite.callId
        }

        val userId = resolvedCurrentUserId() ?: return failCall(invite, "You need to be signed in to start a call")
        val participantName = currentUserName ?: "Click User"
        val tokenResult = coordinator.fetchCallToken(
            connectionId = invite.connectionId,
            roomName = invite.roomName,
            participantName = participantName,
            groupId = groupIdFromInvite(invite),
        )

        tokenResult.fold(
            onSuccess = { response ->
                CallRingtonePlayer.stop()
                internalCallManager.startCall(
                    roomName = invite.roomName,
                    token = response.token,
                    wsUrl = response.wsUrl,
                    videoEnabled = invite.videoEnabled,
                )
            },
            onFailure = {
                resetJoinGuards()
                val peerId = if (userId == invite.callerId) invite.calleeId else invite.callerId
                sendCancel(invite, peerId, "cancelled")
                failCall(invite, it.message ?: "Failed to create call token")
            },
        )
    }

    private suspend fun acceptAndJoinIncomingCall(invite: CallInvite) {
        val userId = resolvedCurrentUserId() ?: return failCall(invite, "You need to be signed in to start a call")

        joinMutex.withLock {
            if (joinStartedCallId == invite.callId) return
            if (callState.value is CallState.Connected) {
                joinStartedCallId = invite.callId
                return
            }
            joinStartedCallId = invite.callId
        }

        val participantName = currentUserName ?: "Click User"
        // Overlap accept signaling with token fetch so Android join is not gated on Realtime RTT.
        scope.launch {
            runCatching { sendResponse(invite, accepted = true, busy = false) }
        }

        val tokenResult = coordinator.fetchCallToken(
            connectionId = invite.connectionId,
            roomName = invite.roomName,
            participantName = participantName,
            groupId = groupIdFromInvite(invite),
        )

        tokenResult.fold(
            onSuccess = { response ->
                internalCallManager.startCall(
                    roomName = invite.roomName,
                    token = response.token,
                    wsUrl = response.wsUrl,
                    videoEnabled = invite.videoEnabled,
                )
            },
            onFailure = {
                resetJoinGuards()
                sendCancel(invite, invite.callerId, "cancelled")
                failCall(invite, it.message ?: "Failed to create call token")
            },
        )
    }

    private fun notifyPeerRoomConnected(invite: CallInvite) {
        if (roomConnectedNotifiedCallId == invite.callId) return
        roomConnectedNotifiedCallId = invite.callId
        val uid = resolvedCurrentUserId() ?: return
        val peerId = if (uid == invite.callerId) invite.calleeId else invite.callerId
        scope.launch {
            sendRoomConnected(invite, peerId, uid)
        }
    }

    private suspend fun sendInvite(invite: CallInvite) {
        outboundChannel(invite.calleeId).broadcast(
            event = "invite",
            message = buildJsonObject {
                put("callId", invite.callId)
                put("connectionId", invite.connectionId)
                put("roomName", invite.roomName)
                put("callerId", invite.callerId)
                put("callerName", invite.callerName)
                put("calleeId", invite.calleeId)
                put("calleeName", invite.calleeName)
                put("videoEnabled", invite.videoEnabled)
                put("createdAt", invite.createdAt)
            }
        )
    }

    private suspend fun sendResponse(invite: CallInvite, accepted: Boolean, busy: Boolean) {
        val responderId = resolvedCurrentUserId() ?: return
        outboundChannel(invite.callerId).broadcast(
            event = "response",
            message = buildJsonObject {
                put("callId", invite.callId)
                put("connectionId", invite.connectionId)
                put("responderId", responderId)
                put("accepted", accepted)
                put("busy", busy)
            }
        )
    }

    private suspend fun sendCancel(invite: CallInvite, targetUserId: String, reason: String) {
        val senderId = resolvedCurrentUserId() ?: return
        outboundChannel(targetUserId).broadcast(
            event = "cancel",
            message = buildJsonObject {
                put("callId", invite.callId)
                put("connectionId", invite.connectionId)
                put("senderId", senderId)
                put("reason", reason)
            }
        )
    }

    private suspend fun sendRoomConnected(invite: CallInvite, targetUserId: String, userId: String) {
        outboundChannel(targetUserId).broadcast(
            event = "connected",
            message = buildJsonObject {
                put("callId", invite.callId)
                put("connectionId", invite.connectionId)
                put("userId", userId)
            }
        )
    }

    private suspend fun outboundChannel(userId: String): RealtimeChannel {
        val outbound = outboundChannels.getOrPut(userId) {
            SupabaseConfig.client.channel("calls:user:$userId")
        }
        if (userId !in subscribedOutboundUserIds) {
            try {
                outbound.subscribe(blockUntilSubscribed = true)
                subscribedOutboundUserIds.add(userId)
                lazyOutboundUserIds.add(userId)
            } catch (_: Exception) {
                runCatching { outbound.subscribe() }
                lazyOutboundUserIds.add(userId)
            }
        }
        return outbound
    }

    private fun failCall(invite: CallInvite?, reason: String) {
        CallRingtonePlayer.stop()
        invite?.let { PlatformIncomingCallUi.dismissIncomingCall(it.callId, reason) }
        internalCallManager.endCall()
        activeInviteValue = invite
        _overlayState.value = CallOverlayState.Ended(invite, reason)
        cleanupAfterCall()
    }

    private fun resolvedCurrentUserId(): String? {
        return currentUserId ?: authRepository.getCurrentUser()?.id
    }

    private fun peerUserId(invite: CallInvite): String? {
        val uid = resolvedCurrentUserId() ?: return null
        return if (uid == invite.callerId) invite.calleeId else invite.callerId
    }

    private fun processPendingSystemInviteIfPossible() {
        val userId = resolvedCurrentUserId() ?: return
        val invite = pendingSystemInvite ?: return
        if (invite.calleeId != userId) return

        if (activeInviteValue?.callId != invite.callId) {
            activeInviteValue = invite
            _overlayState.value = CallOverlayState.Incoming(invite)
            CallRingtonePlayer.startIncoming()
            PlatformIncomingCallUi.showIncomingCall(invite)
        }

        when (pendingSystemAction) {
            SystemIncomingCallAction.Accept -> {
                pendingSystemInvite = null
                pendingSystemAction = null
                acceptIncomingCall()
            }

            SystemIncomingCallAction.Decline -> {
                pendingSystemInvite = null
                pendingSystemAction = null
                declineIncomingCall()
            }

            null -> Unit
        }
    }
}