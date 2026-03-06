package compose.project.click.click.notifications

actual fun savePendingPushToken(token: String, platform: String) {
    AndroidPushNotificationRuntime.savePendingToken(token, platform)
}

actual fun consumePendingPushToken(): PendingPushToken? {
    return AndroidPushNotificationRuntime.consumePendingToken()
}