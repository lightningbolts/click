package compose.project.click.click.notifications

interface PushNotificationService {
    fun registerToken(userId: String)
    fun requestPermission()
}

expect fun createPushNotificationService(): PushNotificationService