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
 * email the account an approval link and ask its other devices), reload approvals once that has
 * landed, then share historical chat keys with any of the account's newer approved devices.
 */
internal fun AppDataManager.syncDeviceHistory(userId: String) {
    if (userId.isBlank()) return
    val token = DeviceApprovalStore.beginRefresh()
    scope.launch {
        val due =
            deviceHistorySyncMutex.withLock {
                val now = Clock.System.now().toEpochMilliseconds()
                val due = now - lastDeviceHistorySyncMs >= DEVICE_HISTORY_SYNC_MIN_INTERVAL_MS
                if (due) {
                    lastDeviceHistorySyncMs = now
                    runCatching { chatRepository.registerThisDeviceImpl() }
                        .onFailure { e -> println("AppDataManager: device registration failed: ${e.redactedRestMessage()}") }
                }
                due
            }
        // After registration, so a first sign-in already sees its own pending request.
        loadDeviceApprovals(userId, token)
        if (due) {
            runCatching { chatRepository.shareHistoryWithApprovedDevicesImpl(userId) }
                .onFailure { e -> println("AppDataManager: device history sync failed: ${e.redactedRestMessage()}") }
        }
    }
}

/** Sign-out: the next account on this device must sync immediately. */
internal fun resetDeviceHistorySyncThrottle() {
    lastDeviceHistorySyncMs = 0L
    DeviceApprovalStore.reset()
}

/**
 * Reloads which of the account's devices wait for approval and this device's own request (one GET).
 * [userId] defaults to the signed-in user; pass it when the caller already resolved it.
 */
fun AppDataManager.refreshDeviceApprovals(userId: String? = _currentUser.value?.id) {
    val uid = userId?.takeIf { it.isNotBlank() } ?: return
    // Taken before launching, so a reset or decision that happens meanwhile invalidates the result.
    val token = DeviceApprovalStore.beginRefresh()
    scope.launch { loadDeviceApprovals(uid, token) }
}

private suspend fun AppDataManager.loadDeviceApprovals(
    userId: String,
    token: Long,
) {
    runCatching { chatRepository.deviceApprovalsImpl() }
        .onSuccess { approvals ->
            approvals ?: return@onSuccess
            DeviceApprovalStore.publish(token, approvals) {
                // Null while a first sign-in is still loading; a sign-out already bumped the token.
                _currentUser.value?.id.let { it == null || it == userId }
            }
        }.onFailure { e -> println("AppDataManager: device approvals refresh failed: ${e.redactedRestMessage()}") }
}

/** After this device approved another, share the history it holds right away (off the UI path). */
fun AppDataManager.shareHistoryAfterApproval() {
    val userId = _currentUser.value?.id?.takeIf { it.isNotBlank() } ?: return
    scope.launch {
        runCatching { chatRepository.shareHistoryWithApprovedDevicesImpl(userId) }
            .onFailure { e -> println("AppDataManager: history share after approval failed: ${e.redactedRestMessage()}") }
    }
}
