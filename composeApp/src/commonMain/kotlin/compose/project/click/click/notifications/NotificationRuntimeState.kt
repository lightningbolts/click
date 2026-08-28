package compose.project.click.click.notifications

data class LocalNotificationPreferences(
    val messageNotificationsEnabled: Boolean = true,
    val callNotificationsEnabled: Boolean = true,
    val eventReminderNotificationsEnabled: Boolean = true,
    val availabilityMatchNotificationsEnabled: Boolean = true,
    val hubMessageNotificationsEnabled: Boolean = true,
    val eventTeaserNotificationsEnabled: Boolean = true,
    val reconnectNudgeNotificationsEnabled: Boolean = true,
)

expect object NotificationRuntimeState {
    fun setNotificationPreferences(
        messageEnabled: Boolean,
        callEnabled: Boolean,
        eventReminderEnabled: Boolean = true,
        availabilityMatchEnabled: Boolean = true,
        hubMessageEnabled: Boolean = true,
        eventTeaserEnabled: Boolean = true,
        reconnectNudgeEnabled: Boolean = true,
    )

    fun getNotificationPreferences(): LocalNotificationPreferences

    fun setActiveChatId(chatId: String?)

    fun getActiveChatId(): String?
}
