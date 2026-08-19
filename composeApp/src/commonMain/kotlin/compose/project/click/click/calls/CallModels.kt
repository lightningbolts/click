package compose.project.click.click.calls // pragma: allowlist secret

import kotlinx.serialization.Serializable

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
    fun counterpartName(currentUserId: String?): String = if (currentUserId == callerId) calleeName else callerName
}

@Serializable
internal data class CallResponse(
    val callId: String,
    val connectionId: String,
    val responderId: String,
    val accepted: Boolean,
    val busy: Boolean = false,
)

@Serializable
internal data class CallCancel(
    val callId: String,
    val connectionId: String,
    val senderId: String,
    val reason: String,
)

@Serializable
internal data class CallRoomConnected(
    val callId: String,
    val connectionId: String,
    val userId: String,
)

sealed class CallOverlayState {
    data object Idle : CallOverlayState()

    data class Outgoing(
        val invite: CallInvite,
    ) : CallOverlayState()

    data class Incoming(
        val invite: CallInvite,
    ) : CallOverlayState()

    data class Connecting(
        val invite: CallInvite,
    ) : CallOverlayState()

    data class Ended(
        val invite: CallInvite?,
        val reason: String,
    ) : CallOverlayState()
}
