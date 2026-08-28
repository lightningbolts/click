package compose.project.click.click.notifications // pragma: allowlist secret

import android.content.Context

private const val CLICK_RUNTIME_PREFS = "click_runtime_prefs"
private const val KEY_RUNTIME_MESSAGE_NOTIFICATIONS = "runtime_message_notifications_enabled"
private const val KEY_RUNTIME_CALL_NOTIFICATIONS = "runtime_call_notifications_enabled"
private const val KEY_RUNTIME_EVENT_REMINDER_NOTIFICATIONS = "runtime_event_reminder_notifications_enabled"
private const val KEY_RUNTIME_AVAILABILITY_MATCH_NOTIFICATIONS = "runtime_availability_match_notifications_enabled"
private const val KEY_RUNTIME_HUB_MESSAGE_NOTIFICATIONS = "runtime_hub_message_notifications_enabled"
private const val KEY_RUNTIME_EVENT_TEASER_NOTIFICATIONS = "runtime_event_teaser_notifications_enabled"
private const val KEY_RUNTIME_RECONNECT_NUDGE_NOTIFICATIONS = "runtime_reconnect_nudge_notifications_enabled"
private const val KEY_RUNTIME_ACTIVE_CHAT_ID = "runtime_active_chat_id"

actual object NotificationRuntimeState {
    private fun prefs() =
        AndroidPushNotificationRuntime
            .requireContext()
            ?.getSharedPreferences(CLICK_RUNTIME_PREFS, Context.MODE_PRIVATE)

    actual fun setNotificationPreferences(
        messageEnabled: Boolean,
        callEnabled: Boolean,
        eventReminderEnabled: Boolean,
        availabilityMatchEnabled: Boolean,
        hubMessageEnabled: Boolean,
        eventTeaserEnabled: Boolean,
        reconnectNudgeEnabled: Boolean,
    ) {
        prefs()
            ?.edit()
            ?.putBoolean(KEY_RUNTIME_MESSAGE_NOTIFICATIONS, messageEnabled)
            ?.putBoolean(KEY_RUNTIME_CALL_NOTIFICATIONS, callEnabled)
            ?.putBoolean(KEY_RUNTIME_EVENT_REMINDER_NOTIFICATIONS, eventReminderEnabled)
            ?.putBoolean(KEY_RUNTIME_AVAILABILITY_MATCH_NOTIFICATIONS, availabilityMatchEnabled)
            ?.putBoolean(KEY_RUNTIME_HUB_MESSAGE_NOTIFICATIONS, hubMessageEnabled)
            ?.putBoolean(KEY_RUNTIME_EVENT_TEASER_NOTIFICATIONS, eventTeaserEnabled)
            ?.putBoolean(KEY_RUNTIME_RECONNECT_NUDGE_NOTIFICATIONS, reconnectNudgeEnabled)
            ?.apply()
    }

    actual fun getNotificationPreferences(): LocalNotificationPreferences {
        val prefs = prefs()
        return LocalNotificationPreferences(
            messageNotificationsEnabled = prefs?.getBoolean(KEY_RUNTIME_MESSAGE_NOTIFICATIONS, true) ?: true,
            callNotificationsEnabled = prefs?.getBoolean(KEY_RUNTIME_CALL_NOTIFICATIONS, true) ?: true,
            eventReminderNotificationsEnabled = prefs?.getBoolean(KEY_RUNTIME_EVENT_REMINDER_NOTIFICATIONS, true) ?: true,
            availabilityMatchNotificationsEnabled = prefs?.getBoolean(KEY_RUNTIME_AVAILABILITY_MATCH_NOTIFICATIONS, true) ?: true,
            hubMessageNotificationsEnabled = prefs?.getBoolean(KEY_RUNTIME_HUB_MESSAGE_NOTIFICATIONS, true) ?: true,
            eventTeaserNotificationsEnabled = prefs?.getBoolean(KEY_RUNTIME_EVENT_TEASER_NOTIFICATIONS, true) ?: true,
            reconnectNudgeNotificationsEnabled = prefs?.getBoolean(KEY_RUNTIME_RECONNECT_NUDGE_NOTIFICATIONS, true) ?: true,
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

    actual fun getActiveChatId(): String? = prefs()?.getString(KEY_RUNTIME_ACTIVE_CHAT_ID, null)
}
