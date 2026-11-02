package compose.project.click.click.data // pragma: allowlist secret

import compose.project.click.click.data.devices.DeviceApprovalStore // pragma: allowlist secret
import compose.project.click.click.data.repository.deviceApprovalsImpl // pragma: allowlist secret
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
    refreshDeviceApprovals()
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
    DeviceApprovalStore.reset()
}

/**
 * Reloads which of the account's devices wait for approval and this device's own request. Runs on
 * every foreground (cheap: one GET), so a `device_approval` push surfaces the sheet when tapped.
 */
fun AppDataManager.refreshDeviceApprovals() {
    if (_currentUser.value?.id.isNullOrBlank()) return
    scope.launch {
        runCatching { chatRepository.deviceApprovalsImpl() }
            .onSuccess { approvals -> approvals?.let(DeviceApprovalStore::set) }
            .onFailure { e -> println("AppDataManager: device approvals refresh failed: ${e.redactedRestMessage()}") }
    }
}

/** After this device approved another, share the history it holds right away (off the UI path). */
fun AppDataManager.shareHistoryAfterApproval() {
    val userId = _currentUser.value?.id?.takeIf { it.isNotBlank() } ?: return
    scope.launch {
        runCatching { chatRepository.shareHistoryWithApprovedDevicesImpl(userId) }
            .onFailure { e -> println("AppDataManager: history share after approval failed: ${e.redactedRestMessage()}") }
    }
}
