package compose.project.click.click.notifications

private class IosPushNotificationService : PushNotificationService {
    override fun registerToken(userId: String) {
    }

    override fun requestPermission() {
    }
}

actual fun createPushNotificationService(): PushNotificationService = IosPushNotificationService()