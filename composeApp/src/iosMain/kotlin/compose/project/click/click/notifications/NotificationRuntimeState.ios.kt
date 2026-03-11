package compose.project.click.click.notifications

import platform.Foundation.NSUserDefaults

private const val CLICK_RUNTIME_PREFS_SUITE = "click_auth_prefs"
private const val KEY_RUNTIME_MESSAGE_NOTIFICATIONS = "runtime_message_notifications_enabled"
private const val KEY_RUNTIME_CALL_NOTIFICATIONS = "runtime_call_notifications_enabled"
private const val KEY_RUNTIME_ACTIVE_CHAT_ID = "runtime_active_chat_id"

actual object NotificationRuntimeState {
    private val defaults = NSUserDefaults(suiteName = CLICK_RUNTIME_PREFS_SUITE) ?: NSUserDefaults.standardUserDefaults

    actual fun setNotificationPreferences(messageEnabled: Boolean, callEnabled: Boolean) {
        defaults.setBool(messageEnabled, KEY_RUNTIME_MESSAGE_NOTIFICATIONS)
        defaults.setBool(callEnabled, KEY_RUNTIME_CALL_NOTIFICATIONS)
        defaults.synchronize()
    }

    actual fun getNotificationPreferences(): LocalNotificationPreferences {
        return LocalNotificationPreferences(
            messageNotificationsEnabled = defaults.objectForKey(KEY_RUNTIME_MESSAGE_NOTIFICATIONS)?.let {
                defaults.boolForKey(KEY_RUNTIME_MESSAGE_NOTIFICATIONS)
            } ?: true,
            callNotificationsEnabled = defaults.objectForKey(KEY_RUNTIME_CALL_NOTIFICATIONS)?.let {
                defaults.boolForKey(KEY_RUNTIME_CALL_NOTIFICATIONS)
            } ?: true,
        )
    }

    actual fun setActiveChatId(chatId: String?) {
        if (chatId.isNullOrBlank()) {
            defaults.removeObjectForKey(KEY_RUNTIME_ACTIVE_CHAT_ID)
        } else {
            defaults.setObject(chatId, KEY_RUNTIME_ACTIVE_CHAT_ID)
        }
        defaults.synchronize()
    }

    actual fun getActiveChatId(): String? {
        return defaults.stringForKey(KEY_RUNTIME_ACTIVE_CHAT_ID)
    }
}