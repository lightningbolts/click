package compose.project.click.click.notifications

data class LocalNotificationPreferences(
    val messageNotificationsEnabled: Boolean = true,
    val callNotificationsEnabled: Boolean = true,
)

expect object NotificationRuntimeState {
    fun setNotificationPreferences(messageEnabled: Boolean, callEnabled: Boolean)

    fun getNotificationPreferences(): LocalNotificationPreferences

    fun setActiveChatId(chatId: String?)

    fun getActiveChatId(): String?
}