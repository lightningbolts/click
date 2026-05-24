package compose.project.click.click.notifications

/** Clears delivered push notifications for a chat thread when the user opens it. */
expect object ChatNotificationDismisser {
    fun dismissForThread(chatId: String, connectionId: String = "")
}

fun chatNotificationTag(threadId: String): String = "click_chat_$threadId"
