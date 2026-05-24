package compose.project.click.click.notifications

import androidx.core.app.NotificationManagerCompat

actual object ChatNotificationDismisser {
    actual fun dismissForThread(chatId: String, connectionId: String) {
        val context = AndroidPushNotificationRuntime.requireContext() ?: return
        val manager = NotificationManagerCompat.from(context)
        listOf(chatId, connectionId)
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { threadId ->
                manager.cancel(chatNotificationTag(threadId), 0)
            }
    }
}
