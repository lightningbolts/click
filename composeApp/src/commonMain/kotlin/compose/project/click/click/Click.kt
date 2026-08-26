package compose.project.click.click

import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.repository.AuthRepository
import compose.project.click.click.data.repository.PushTokenRepository
import compose.project.click.click.deeplink.ConnectionDeepLinkRouter
import compose.project.click.click.deeplink.EventDeepLinkRouter
import compose.project.click.click.notifications.ChatDeepLinkManager
import compose.project.click.click.notifications.ChatNotificationDismisser
import compose.project.click.click.notifications.ChatPushInboxBridge
import compose.project.click.click.notifications.savePendingPushToken
import compose.project.click.click.telemetry.TelemetryBatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val pushTokenScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private val pushTokenRepository = PushTokenRepository()

/**
 * Called from iOS `AppDelegate.applicationDidBecomeActive` so Compose re-reads
 * `CLLocationManager.authorizationStatus()` and notification settings after returning from the system UI.
 */
fun onApplicationDidBecomeActive() {
    notifyPlatformApplicationForeground()
    AppDataManager.handleApplicationForegrounded()
}

/** Called from platform lifecycle when the app moves to background. */
fun onApplicationDidEnterBackground() {
    TelemetryBatcher.onAppBackgrounded()
}

fun savePushToken(
    token: String,
    platform: String,
) {
    savePushToken(token, platform, "standard")
}

fun savePushToken(
    token: String,
    platform: String,
    tokenType: String,
) {
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

fun setChatDeepLink(
    chatId: String,
    connectionId: String = "",
) {
    val resolvedConnectionId = connectionId.trim().ifBlank { chatId.trim() }
    if (resolvedConnectionId.isEmpty()) return
    ChatNotificationDismisser.dismissForThread(chatId, resolvedConnectionId)
    ChatDeepLinkManager.setPendingChat(resolvedConnectionId)
}

/** iOS background/foreground chat push — updates inbox previews without opening the thread. */
fun applyChatMessagePushFromNotification(
    chatId: String,
    connectionId: String,
    senderUserId: String,
    previewText: String,
    messageId: String? = null,
) {
    ChatPushInboxBridge.applyChatMessagePush(
        chatId = chatId,
        connectionId = connectionId,
        senderUserId = senderUserId,
        previewText = previewText,
        messageId = messageId,
    )
}

/** iOS (and tests): open ephemeral hub from `click://hub/{id}` or universal link. */
fun setCommunityHubDeepLink(hubId: String) {
    ChatDeepLinkManager.setPendingCommunityHub(hubId)
}

/** Universal Link / deep link for `/c/{userId}` — queues connection handshake in [App]. */
fun setConnectionDeepLink(userId: String) {
    ConnectionDeepLinkRouter.setPendingConnectionUserId(userId)
}

/** Parse and queue a connection URL. Returns true when recognized. */
fun handleConnectionUniversalLink(url: String): Boolean = ConnectionDeepLinkRouter.handleIncomingUrl(url)

/** Universal Link / deep link for `/e/{beaconId}` — queues Map event focus in [App]. */
fun setEventDeepLink(beaconId: String) {
    EventDeepLinkRouter.setPendingBeaconId(beaconId)
}

/** Parse and queue an event URL. Returns true when recognized. */
fun handleEventUniversalLink(url: String): Boolean = EventDeepLinkRouter.handleIncomingUrl(url)
