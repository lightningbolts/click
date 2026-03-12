package compose.project.click.click.notifications

import platform.Foundation.NSUserDefaults

private fun pendingPushTokenKey(tokenType: String): String = "pending_push_token_$tokenType"

private fun pendingPushPlatformKey(tokenType: String): String = "pending_push_platform_$tokenType"

actual fun savePendingPushToken(token: String, platform: String, tokenType: String) {
    val defaults = NSUserDefaults.standardUserDefaults
    defaults.setObject(token, forKey = pendingPushTokenKey(tokenType))
    defaults.setObject(platform, forKey = pendingPushPlatformKey(tokenType))
}

actual fun consumePendingPushTokens(): List<PendingPushToken> {
    val defaults = NSUserDefaults.standardUserDefaults
    return listOf("standard", "voip").mapNotNull { tokenType ->
        val token = defaults.stringForKey(pendingPushTokenKey(tokenType)) ?: return@mapNotNull null
        val platform = defaults.stringForKey(pendingPushPlatformKey(tokenType)) ?: return@mapNotNull null
        defaults.removeObjectForKey(pendingPushTokenKey(tokenType))
        defaults.removeObjectForKey(pendingPushPlatformKey(tokenType))
        PendingPushToken(token = token, platform = platform, tokenType = tokenType)
    }
}