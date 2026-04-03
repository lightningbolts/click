package compose.project.click.click

import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.repository.AuthRepository
import compose.project.click.click.data.repository.PushTokenRepository
import compose.project.click.click.calls.CallInvite
import compose.project.click.click.calls.CallSessionManager
import compose.project.click.click.notifications.ChatDeepLinkManager
import compose.project.click.click.notifications.savePendingPushToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val pushTokenScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private val pushTokenRepository = PushTokenRepository()

fun savePushToken(token: String, platform: String) {
    savePushToken(token, platform, "standard")
}

fun savePushToken(token: String, platform: String, tokenType: String) {
    pushTokenScope.launch {
        val currentUserId = AppDataManager.currentUser.value?.id ?: AuthRepository().getCurrentUser()?.id
        if (currentUserId.isNullOrBlank()) {
            savePendingPushToken(token, platform, tokenType)
            println("savePushToken: Cached token because no authenticated user is available yet")
            return@launch
        }
        pushTokenRepository.savePushToken(
            userId = currentUserId,
            token = token,
            platform = platform,
            tokenType = tokenType,
        )
    }
}

fun setChatDeepLink(chatId: String) {
    ChatDeepLinkManager.setPendingChat(chatId)
}

/** iOS (and tests): open ephemeral hub from `click://hub/{id}` or universal link. */
fun setCommunityHubDeepLink(hubId: String) {
    ChatDeepLinkManager.setPendingCommunityHub(hubId)
}

fun handleIncomingCallPush(
    callId: String,
    connectionId: String,
    roomName: String,
    callerId: String,
    callerName: String,
    calleeId: String,
    calleeName: String,
    videoEnabled: Boolean,
    createdAt: Long,
    autoAnswer: Boolean = false,
    autoDecline: Boolean = false,
) {
    CallSessionManager.receiveIncomingPush(
        invite = CallInvite(
            callId = callId,
            connectionId = connectionId,
            roomName = roomName,
            callerId = callerId,
            callerName = callerName,
            calleeId = calleeId,
            calleeName = calleeName,
            videoEnabled = videoEnabled,
            createdAt = createdAt,
        ),
        autoAnswer = autoAnswer,
        autoDecline = autoDecline,
    )
}