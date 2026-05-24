package compose.project.click.click.notifications

import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNUserNotificationCenter

@Suppress("UNCHECKED_CAST")
actual object ChatNotificationDismisser {
    actual fun dismissForThread(chatId: String, connectionId: String) {
        val targets = listOf(chatId, connectionId).filter { it.isNotBlank() }.toSet()
        if (targets.isEmpty()) return

        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.getDeliveredNotificationsWithCompletionHandler { rawNotifications ->
            val deliveredList = rawNotifications as? List<*> ?: return@getDeliveredNotificationsWithCompletionHandler
            val ids = buildList {
                for (item in deliveredList) {
                    val delivered = item as? UNNotification ?: continue
                    val info = delivered.request.content.userInfo
                    val payloadChatId = info["chat_id"] as? String
                    val payloadConnectionId = info["connection_id"] as? String
                    val matches = targets.any { target ->
                        target == payloadChatId || target == payloadConnectionId
                    }
                    if (matches) {
                        delivered.request.identifier?.let { add(it) }
                    }
                }
            }
            if (ids.isNotEmpty()) {
                center.removeDeliveredNotificationsWithIdentifiers(ids)
            }
        }
    }
}
