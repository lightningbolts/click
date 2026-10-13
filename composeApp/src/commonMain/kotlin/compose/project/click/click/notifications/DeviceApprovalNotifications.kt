package compose.project.click.click.notifications

/**
 * `device_approval` notifications are posted with this tag and id 0, so a re-sent push for the
 * same request replaces the first and different requests never merge (sheet 10 §A4).
 */
fun deviceApprovalNotificationTag(requestId: String): String = "device_approval:$requestId"

/** Removes [requestId]'s approval notification once it's decided here (iOS `reconcileDeviceApprovalNotifications`). */
expect fun cancelDeviceApprovalNotification(requestId: String)
