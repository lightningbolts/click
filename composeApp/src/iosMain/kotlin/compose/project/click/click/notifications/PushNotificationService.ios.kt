package compose.project.click.click.notifications

import compose.project.click.click.data.repository.PushTokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
    }
}

actual fun createPushNotificationService(): PushNotificationService = IosPushNotificationService()