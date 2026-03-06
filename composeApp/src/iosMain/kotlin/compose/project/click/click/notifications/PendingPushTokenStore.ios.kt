package compose.project.click.click.notifications

import platform.Foundation.NSUserDefaults

private const val PendingPushTokenKey = "pending_push_token"
private const val PendingPushPlatformKey = "pending_push_platform"

actual fun savePendingPushToken(token: String, platform: String) {
    val defaults = NSUserDefaults.standardUserDefaults
    defaults.setObject(token, forKey = PendingPushTokenKey)
    defaults.setObject(platform, forKey = PendingPushPlatformKey)
}

actual fun consumePendingPushToken(): PendingPushToken? {
    val defaults = NSUserDefaults.standardUserDefaults
    val token = defaults.stringForKey(PendingPushTokenKey) ?: return null
    val platform = defaults.stringForKey(PendingPushPlatformKey) ?: return null
    defaults.removeObjectForKey(PendingPushTokenKey)
    defaults.removeObjectForKey(PendingPushPlatformKey)
    return PendingPushToken(token = token, platform = platform)
}