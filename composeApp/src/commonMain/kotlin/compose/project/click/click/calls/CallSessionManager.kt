package compose.project.click.click.calls

import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.repository.AuthRepository
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
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
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

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

sealed class CallOverlayState {
    data object Idle : CallOverlayState()
    data class Outgoing(val invite: CallInvite) : CallOverlayState()
    data class Incoming(val invite: CallInvite) : CallOverlayState()
    data class Connecting(val invite: CallInvite) : CallOverlayState()
    data class Ended(val invite: CallInvite?, val reason: String) : CallOverlayState()
}

object CallSessionManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val authRepository = AuthRepository()
    private val coordinator = CallCoordinator()
    private val callPushNotifier = CallPushNotifier()
    private val internalCallManager = createCallManager()
    private val outboundChannels = mutableMapOf<String, RealtimeChannel>()

    private var inboundChannel: RealtimeChannel? = null
    private var inviteJob: Job? = null
    private var responseJob: Job? = null
    private var cancelJob: Job? = null
    private var timeoutJob: Job? = null

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

    init {
        scope.launch {
            callState.collectLatest { state ->
                when (state) {
                    CallState.Idle -> {
                        if (_overlayState.value !is CallOverlayState.Incoming &&
                            _overlayState.value !is CallOverlayState.Outgoing &&
                            _overlayState.value !is CallOverlayState.Connecting
                        ) {
                            activeInviteValue = null
                        }
                    }

                    is CallState.Ended -> {
                        CallRingtonePlayer.stop()
                        activeInviteValue?.let { invite ->
                            PlatformIncomingCallUi.dismissIncomingCall(invite.callId, state.reason)
                        }
                        _overlayState.value = CallOverlayState.Ended(activeInviteValue, state.reason ?: "Call ended")
                    }

                    is CallState.Connected -> {
                        CallRingtonePlayer.stop()
                        activeInviteValue?.let { invite ->
                            PlatformIncomingCallUi.dismissIncomingCall(invite.callId)
                        }
                        if (_overlayState.value is CallOverlayState.Connecting) {
                            _overlayState.value = CallOverlayState.Idle
                        }
                    }

                    else -> {
                        CallRingtonePlayer.stop()
                    }
                }
            }
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

        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(30_000)
            if (activeInviteValue?.callId == invite.callId && _overlayState.value is CallOverlayState.Outgoing) {
                sendCancel(invite, invite.calleeId, "missed")
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
    }

    fun cancelCurrentCall(notifyPeer: Boolean = true) {
        val invite = activeInviteValue
        timeoutJob?.cancel()
        CallRingtonePlayer.stop()

        val overlay = _overlayState.value
        if (notifyPeer && invite != null) {
            scope.launch {
                when (overlay) {
                    is CallOverlayState.Outgoing -> sendCancel(invite, invite.calleeId, "cancelled")
                    is CallOverlayState.Incoming -> sendResponse(invite, accepted = false, busy = false)
                    is CallOverlayState.Connecting -> {
                        val peerId = if (currentUserId == invite.callerId) invite.calleeId else invite.callerId
                        sendCancel(invite, peerId, "cancelled")
                    }
                    else -> Unit
                }
            }
        }

        if (invite != null) {
            PlatformIncomingCallUi.dismissIncomingCall(invite.callId)
        }
        activeInviteValue = null
        _overlayState.value = CallOverlayState.Idle
    }

    fun endActiveCall() {
        val invite = activeInviteValue
        if (invite != null) {
            scope.launch {
                val peerId = if (currentUserId == invite.callerId) invite.calleeId else invite.callerId
                sendCancel(invite, peerId, "ended")
            }
            PlatformIncomingCallUi.dismissIncomingCall(invite.callId, "ended")
        }
        internalCallManager.endCall()
        activeInviteValue = null
        _overlayState.value = CallOverlayState.Idle
        CallRingtonePlayer.stop()
    }

    fun dismissEndedCall() {
        if (callState.value is CallState.Ended) {
            internalCallManager.endCall()
        }
        activeInviteValue = null
        _overlayState.value = CallOverlayState.Idle
    }

    fun receiveIncomingPush(invite: CallInvite, autoAnswer: Boolean = false, autoDecline: Boolean = false) {
        pendingSystemInvite = invite
        pendingSystemAction = when {
            autoAnswer -> SystemIncomingCallAction.Accept
            autoDecline -> SystemIncomingCallAction.Decline
            else -> pendingSystemAction
        }

        val activeInvite = activeInviteValue
        if (activeInvite?.callId == invite.callId) {
            processPendingSystemInviteIfPossible()
            return
        }

        val userId = resolvedCurrentUserId()
        if (userId != null && invite.calleeId != userId) {
            return
        }

        if (_overlayState.value !is CallOverlayState.Idle || callState.value !is CallState.Idle) {
            return
        }

        activeInviteValue = invite
        _overlayState.value = CallOverlayState.Incoming(invite)
        CallRingtonePlayer.startIncoming()
        PlatformIncomingCallUi.showIncomingCall(invite)
        processPendingSystemInviteIfPossible()
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

        val inbound = channel
        scope.launch {
            try {
                inbound.subscribe()
            } catch (_: Exception) {
            }
        }
    }

    private fun clearSubscriptions() {
        inviteJob?.cancel()
        responseJob?.cancel()
        cancelJob?.cancel()
        timeoutJob?.cancel()
        inviteJob = null
        responseJob = null
        cancelJob = null
        timeoutJob = null

        inboundChannel?.let { channel ->
            scope.launch {
                runCatching { channel.unsubscribe() }
            }
        }
        inboundChannel = null

        outboundChannels.values.forEach { channel ->
            scope.launch {
                runCatching { channel.unsubscribe() }
            }
        }
        outboundChannels.clear()
    }

    private fun handleInvite(invite: CallInvite) {
        val userId = resolvedCurrentUserId() ?: return
        if (invite.calleeId != userId) return

        val isBusy = _overlayState.value !is CallOverlayState.Idle || callState.value !is CallState.Idle
        if (isBusy) {
            scope.launch {
                sendResponse(invite, accepted = false, busy = true)
            }
            return
        }

        activeInviteValue = invite
        _overlayState.value = CallOverlayState.Incoming(invite)
        CallRingtonePlayer.startIncoming()
        PlatformIncomingCallUi.showIncomingCall(invite)
        processPendingSystemInviteIfPossible()
    }

    private fun handleResponse(response: CallResponse) {
        val invite = activeInviteValue ?: return
        if (invite.callId != response.callId) return
        if (_overlayState.value !is CallOverlayState.Outgoing) return

        timeoutJob?.cancel()
        CallRingtonePlayer.stop()
        PlatformIncomingCallUi.dismissIncomingCall(invite.callId)

        when {
            response.accepted -> {
                _overlayState.value = CallOverlayState.Connecting(invite)
                scope.launch {
                    joinCall(invite)
                }
            }

            response.busy -> failCall(invite, "${invite.calleeName} is busy")
            else -> failCall(invite, "${invite.calleeName} declined the call")
        }
    }

    private fun handleCancel(cancel: CallCancel) {
        val invite = activeInviteValue ?: return
        if (invite.callId != cancel.callId) return

        timeoutJob?.cancel()
        CallRingtonePlayer.stop()
        PlatformIncomingCallUi.dismissIncomingCall(invite.callId, cancel.reason)

        if (callState.value is CallState.Connected) {
            internalCallManager.endCall()
            activeInviteValue = null
            _overlayState.value = when (cancel.reason) {
                "ended" -> CallOverlayState.Ended(invite, "Call ended")
                "missed" -> CallOverlayState.Ended(invite, "No answer")
                else -> CallOverlayState.Idle
            }
            return
        }

        when (_overlayState.value) {
            is CallOverlayState.Incoming,
            is CallOverlayState.Outgoing,
            is CallOverlayState.Connecting,
            -> {
                activeInviteValue = null
                _overlayState.value = when (cancel.reason) {
                    "missed" -> CallOverlayState.Ended(invite, "No answer")
                    "ended" -> CallOverlayState.Ended(invite, "Call ended")
                    else -> CallOverlayState.Idle
                }
            }

            else -> Unit
        }
    }

    private suspend fun joinCall(invite: CallInvite) {
        val userId = resolvedCurrentUserId() ?: return failCall(invite, "You need to be signed in to start a call")
        val participantName = currentUserName ?: "Click User"
        val tokenResult = coordinator.fetchCallToken(
            roomName = invite.roomName,
            participantName = participantName,
            userId = userId,
        )

        tokenResult.onSuccess { response ->
            CallRingtonePlayer.stop()
            internalCallManager.startCall(
                roomName = invite.roomName,
                token = response.token,
                wsUrl = response.wsUrl,
                videoEnabled = invite.videoEnabled,
            )
        }.onFailure {
            val peerId = if (userId == invite.callerId) invite.calleeId else invite.callerId
            sendCancel(invite, peerId, "cancelled")
            failCall(invite, it.message ?: "Failed to create call token")
        }
    }

    private suspend fun acceptAndJoinIncomingCall(invite: CallInvite) {
        val userId = resolvedCurrentUserId() ?: return failCall(invite, "You need to be signed in to start a call")
        val participantName = currentUserName ?: "Click User"
        val tokenResult = coordinator.fetchCallToken(
            roomName = invite.roomName,
            participantName = participantName,
            userId = userId,
        )

        tokenResult.onSuccess { response ->
            sendResponse(invite, accepted = true, busy = false)
            internalCallManager.startCall(
                roomName = invite.roomName,
                token = response.token,
                wsUrl = response.wsUrl,
                videoEnabled = invite.videoEnabled,
            )
        }.onFailure {
            sendCancel(invite, invite.callerId, "cancelled")
            failCall(invite, it.message ?: "Failed to create call token")
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

    private suspend fun outboundChannel(userId: String): RealtimeChannel {
        val outbound = outboundChannels.getOrPut(userId) {
            SupabaseConfig.client.channel("calls:user:$userId")
        }
        try {
            outbound.subscribe()
        } catch (_: Exception) {
        }
        return outbound
    }

    private fun failCall(invite: CallInvite?, reason: String) {
        CallRingtonePlayer.stop()
        invite?.let { PlatformIncomingCallUi.dismissIncomingCall(it.callId, reason) }
        activeInviteValue = invite
        _overlayState.value = CallOverlayState.Ended(invite, reason)
    }

    private fun resolvedCurrentUserId(): String? {
        return currentUserId ?: authRepository.getCurrentUser()?.id
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