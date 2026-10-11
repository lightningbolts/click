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
 * On sign-in, launch and every foreground: register this device with its label (an additional
 * device asks the account's other devices to approve it; a repeat marks it active), load the
 * approvals this device is part of, then share historical chat keys with any of the account's
 * newer devices whose history sharing was approved.
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
                DeviceApprovals.refresh()
                chatRepository.shareHistoryWithApprovedDevicesImpl(userId)
            }.onFailure { e -> println("AppDataManager: device history sync failed: ${e.redactedRestMessage()}") }
        }
    }
}

/** Sign-out: the next account on this device must sync immediately. */
internal fun resetDeviceHistorySyncThrottle() {
    lastDeviceHistorySyncMs = 0L
    DeviceApprovals.clear()
}

/** Right after approving a device here: share this device's history with it now. */
internal suspend fun AppDataManager.shareHistoryWithApprovedDevicesNow(userId: String) {
    if (userId.isBlank()) return
    runCatching { chatRepository.shareHistoryWithApprovedDevicesImpl(userId) }
        .onFailure { e -> println("AppDataManager: history share after approval failed: ${e.redactedRestMessage()}") }
}
