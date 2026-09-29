package compose.project.click.click.data // pragma: allowlist secret

import compose.project.click.click.data.repository.registerThisDeviceImpl // pragma: allowlist secret
import compose.project.click.click.data.repository.shareHistoryWithApprovedDevicesImpl // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

private const val DEVICE_HISTORY_SYNC_MIN_INTERVAL_MS = 60_000L
private val deviceHistorySyncMutex = Mutex()
private var lastDeviceHistorySyncMs = 0L

/**
 * On sign-in and on every foreground: register this device (an additional device makes click-web
 * email the account an approval link), then share historical chat keys with any of the account's
 * newer devices whose history sharing was approved by email.
 */
internal fun AppDataManager.syncDeviceHistory(userId: String) {
    if (userId.isBlank()) return
    scope.launch {
        deviceHistorySyncMutex.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            if (now - lastDeviceHistorySyncMs < DEVICE_HISTORY_SYNC_MIN_INTERVAL_MS) return@withLock
            lastDeviceHistorySyncMs = now
            runCatching {
                chatRepository.registerThisDeviceImpl()
                chatRepository.shareHistoryWithApprovedDevicesImpl(userId)
            }.onFailure { e -> println("AppDataManager: device history sync failed: ${e.redactedRestMessage()}") }
        }
    }
}

/** Sign-out: the next account on this device must sync immediately. */
internal fun resetDeviceHistorySyncThrottle() {
    lastDeviceHistorySyncMs = 0L
}
