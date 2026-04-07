package compose.project.click.click.notifications

import compose.project.click.click.data.repository.PushTokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSNotificationCenter

private const val IOS_REQUEST_PUSH_PERMISSION_NOTIFICATION = "ClickRequestNotificationPermission"

/** Swift AppDelegate observes this name and calls `UIApplication.shared.registerForRemoteNotifications()`. */
private const val IOS_REGISTER_REMOTE_NOTIFICATIONS_NOTIFICATION = "ClickRegisterForRemoteNotifications"

private fun postRegisterForRemoteNotificationsRequest() {
    NSNotificationCenter.defaultCenter.postNotificationName(
        aName = IOS_REGISTER_REMOTE_NOTIFICATIONS_NOTIFICATION,
        `object` = null,
    )
}

private class IosPushNotificationService : PushNotificationService {
    override fun registerToken(userId: String) {
        // Idempotent: ask APNs again whenever we have a user id so [didRegisterForRemoteNotifications] can fire
        // after login or permission changes, then [savePushToken] routes to [PushTokenRepository].
        postRegisterForRemoteNotificationsRequest()

        val pendingTokens = consumePendingPushTokens()
        if (pendingTokens.isEmpty()) return
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val repository = PushTokenRepository()
            pendingTokens.forEach { pendingToken ->
                repository.savePushToken(
                    userId = userId,
                    token = pendingToken.token,
                    platform = pendingToken.platform,
                    tokenType = pendingToken.tokenType,
                )
            }
        }
    }

    override fun requestPermission() {
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = IOS_REQUEST_PUSH_PERMISSION_NOTIFICATION,
            `object` = null,
        )
    }
}

actual fun createPushNotificationService(): PushNotificationService = IosPushNotificationService()