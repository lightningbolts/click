package compose.project.click.click.notifications

import android.content.Context

private const val CLICK_RUNTIME_PREFS = "click_runtime_prefs"
private const val KEY_RUNTIME_MESSAGE_NOTIFICATIONS = "runtime_message_notifications_enabled"
private const val KEY_RUNTIME_CALL_NOTIFICATIONS = "runtime_call_notifications_enabled"
private const val KEY_RUNTIME_ACTIVE_CHAT_ID = "runtime_active_chat_id"

actual object NotificationRuntimeState {
    private fun prefs() = AndroidPushNotificationRuntime.requireContext()
        ?.getSharedPreferences(CLICK_RUNTIME_PREFS, Context.MODE_PRIVATE)

    actual fun setNotificationPreferences(messageEnabled: Boolean, callEnabled: Boolean) {
        prefs()?.edit()
            ?.putBoolean(KEY_RUNTIME_MESSAGE_NOTIFICATIONS, messageEnabled)
            ?.putBoolean(KEY_RUNTIME_CALL_NOTIFICATIONS, callEnabled)
            ?.apply()
    }

    actual fun getNotificationPreferences(): LocalNotificationPreferences {
        val prefs = prefs()
        return LocalNotificationPreferences(
            messageNotificationsEnabled = prefs?.getBoolean(KEY_RUNTIME_MESSAGE_NOTIFICATIONS, true) ?: true,
            callNotificationsEnabled = prefs?.getBoolean(KEY_RUNTIME_CALL_NOTIFICATIONS, true) ?: true,
        )
    }

    actual fun setActiveChatId(chatId: String?) {
        val editor = prefs()?.edit() ?: return
        if (chatId.isNullOrBlank()) {
            editor.remove(KEY_RUNTIME_ACTIVE_CHAT_ID)
        } else {
            editor.putString(KEY_RUNTIME_ACTIVE_CHAT_ID, chatId)
        }
        editor.apply()
    }

    actual fun getActiveChatId(): String? {
        return prefs()?.getString(KEY_RUNTIME_ACTIVE_CHAT_ID, null)
    }
}