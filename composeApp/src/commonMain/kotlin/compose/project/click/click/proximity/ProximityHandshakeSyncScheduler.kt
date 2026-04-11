package compose.project.click.click.proximity

/**
 * Android schedules a one-shot WorkManager job; iOS relies on [compose.project.click.click.data.AppDataManager]
 * periodic sync. Invoked after a tap handshake is queued offline.
 */
expect fun scheduleProximityHandshakeSync()
