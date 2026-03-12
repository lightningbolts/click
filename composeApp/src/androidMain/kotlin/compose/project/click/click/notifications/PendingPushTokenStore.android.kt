package compose.project.click.click.notifications

actual fun savePendingPushToken(token: String, platform: String, tokenType: String) {
    AndroidPushNotificationRuntime.savePendingToken(token, platform, tokenType)
}

actual fun consumePendingPushTokens(): List<PendingPushToken> {
    return AndroidPushNotificationRuntime.consumePendingTokens()
}