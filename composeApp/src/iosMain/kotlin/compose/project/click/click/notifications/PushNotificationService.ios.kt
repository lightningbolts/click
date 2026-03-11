package compose.project.click.click.notifications

import compose.project.click.click.data.repository.PushTokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSNotificationCenter

private const val IOS_REQUEST_PUSH_PERMISSION_NOTIFICATION = "ClickRequestNotificationPermission"

private class IosPushNotificationService : PushNotificationService {
    override fun registerToken(userId: String) {
        val pendingToken = consumePendingPushToken() ?: return
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            PushTokenRepository().savePushToken(
                userId = userId,
                token = pendingToken.token,
                platform = pendingToken.platform
            )
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