package compose.project.click.click.calls // pragma: allowlist secret

import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun CallSessionManager.releaseLazyOutboundChannels() {
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

internal fun CallSessionManager.cleanupAfterCall() {
    resetJoinGuards()
    releaseLazyOutboundChannels()
}

/**
 * After a websocket drop, supabase-kt can leave channels marked SUBSCRIBED while the
 * server has forgotten them (pushes then return "unmatched topic"). Force a fresh
 * inbound join so subsequent call invites still arrive.
 */
internal fun CallSessionManager.watchRealtimeConnection(userId: String) {
    realtimeWatchJob?.cancel()
    realtimeWatchJob =
        scope.launch {
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

internal fun CallSessionManager.resubscribeIncoming(userId: String) {
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

internal fun CallSessionManager.subscribeToIncoming(userId: String) {
    val channel = SupabaseConfig.client.channel("calls:user:$userId")
    inboundChannel = channel

    inviteJob =
        scope.launch {
            channel.broadcastFlow<CallInvite>("invite").collectLatest { invite ->
                handleInvite(invite)
            }
        }

    responseJob =
        scope.launch {
            channel.broadcastFlow<CallResponse>("response").collectLatest { response ->
                handleResponse(response)
            }
        }

    cancelJob =
        scope.launch {
            channel.broadcastFlow<CallCancel>("cancel").collectLatest { cancel ->
                handleCancel(cancel)
            }
        }

    connectedJob =
        scope.launch {
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

internal fun CallSessionManager.clearSubscriptions() {
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

internal fun CallSessionManager.handleInvite(invite: CallInvite) {
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

internal fun CallSessionManager.handleResponse(response: CallResponse) {
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

internal fun CallSessionManager.handleRoomConnected(connected: CallRoomConnected) {
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

internal fun CallSessionManager.handleCancel(cancel: CallCancel) {
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
        _overlayState.value =
            when (cancel.reason) {
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
            _overlayState.value =
                when (cancel.reason) {
                    "missed" -> CallOverlayState.Ended(invite, "No answer")
                    "ended" -> CallOverlayState.Ended(invite, "Call ended")
                    else -> CallOverlayState.Idle
                }
            cleanupAfterCall()
        }

        else -> Unit
    }
}

internal fun CallSessionManager.groupIdFromInvite(invite: CallInvite): String? {
    val prefix = "click-group-${invite.connectionId}-"
    return if (invite.roomName.startsWith(prefix)) invite.connectionId else null
}

internal suspend fun CallSessionManager.joinCall(invite: CallInvite) {
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
    val tokenResult =
        coordinator.fetchCallToken(
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

internal suspend fun CallSessionManager.acceptAndJoinIncomingCall(invite: CallInvite) {
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

    val tokenResult =
        coordinator.fetchCallToken(
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

internal fun CallSessionManager.notifyPeerRoomConnected(invite: CallInvite) {
    if (roomConnectedNotifiedCallId == invite.callId) return
    roomConnectedNotifiedCallId = invite.callId
    val uid = resolvedCurrentUserId() ?: return
    val peerId = if (uid == invite.callerId) invite.calleeId else invite.callerId
    scope.launch {
        sendRoomConnected(invite, peerId, uid)
    }
}

internal suspend fun CallSessionManager.sendInvite(invite: CallInvite) {
    outboundChannel(invite.calleeId).broadcast(
        event = "invite",
        message =
            buildJsonObject {
                put("callId", invite.callId)
                put("connectionId", invite.connectionId)
                put("roomName", invite.roomName)
                put("callerId", invite.callerId)
                put("callerName", invite.callerName)
                put("calleeId", invite.calleeId)
                put("calleeName", invite.calleeName)
                put("videoEnabled", invite.videoEnabled)
                put("createdAt", invite.createdAt)
            },
    )
}

internal suspend fun CallSessionManager.sendResponse(
    invite: CallInvite,
    accepted: Boolean,
    busy: Boolean,
) {
    val responderId = resolvedCurrentUserId() ?: return
    outboundChannel(invite.callerId).broadcast(
        event = "response",
        message =
            buildJsonObject {
                put("callId", invite.callId)
                put("connectionId", invite.connectionId)
                put("responderId", responderId)
                put("accepted", accepted)
                put("busy", busy)
            },
    )
}

internal suspend fun CallSessionManager.sendCancel(
    invite: CallInvite,
    targetUserId: String,
    reason: String,
) {
    val senderId = resolvedCurrentUserId() ?: return
    outboundChannel(targetUserId).broadcast(
        event = "cancel",
        message =
            buildJsonObject {
                put("callId", invite.callId)
                put("connectionId", invite.connectionId)
                put("senderId", senderId)
                put("reason", reason)
            },
    )
}

internal suspend fun CallSessionManager.sendRoomConnected(
    invite: CallInvite,
    targetUserId: String,
    userId: String,
) {
    outboundChannel(targetUserId).broadcast(
        event = "connected",
        message =
            buildJsonObject {
                put("callId", invite.callId)
                put("connectionId", invite.connectionId)
                put("userId", userId)
            },
    )
}

internal suspend fun CallSessionManager.outboundChannel(userId: String): RealtimeChannel {
    val outbound =
        outboundChannels.getOrPut(userId) {
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

internal fun CallSessionManager.failCall(
    invite: CallInvite?,
    reason: String,
) {
    CallRingtonePlayer.stop()
    invite?.let { PlatformIncomingCallUi.dismissIncomingCall(it.callId, reason) }
    internalCallManager.endCall()
    activeInviteValue = invite
    _overlayState.value = CallOverlayState.Ended(invite, reason)
    cleanupAfterCall()
}

internal fun CallSessionManager.resolvedCurrentUserId(): String? = currentUserId ?: authRepository.getCurrentUser()?.id

internal fun CallSessionManager.peerUserId(invite: CallInvite): String? {
    val uid = resolvedCurrentUserId() ?: return null
    return if (uid == invite.callerId) invite.calleeId else invite.callerId
}

internal fun CallSessionManager.processPendingSystemInviteIfPossible() {
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
        CallSessionManager.SystemIncomingCallAction.Accept -> {
            pendingSystemInvite = null
            pendingSystemAction = null
            acceptIncomingCall()
        }

        CallSessionManager.SystemIncomingCallAction.Decline -> {
            pendingSystemInvite = null
            pendingSystemAction = null
            declineIncomingCall()
        }

        null -> Unit
    }
}
