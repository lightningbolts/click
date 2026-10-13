package compose.project.click.click.notifications

import androidx.core.app.NotificationManagerCompat

actual fun cancelDeviceApprovalNotification(requestId: String) {
    val context = AndroidPushNotificationRuntime.requireContext() ?: return
    runCatching { NotificationManagerCompat.from(context).cancel(deviceApprovalNotificationTag(requestId), 0) }
}
