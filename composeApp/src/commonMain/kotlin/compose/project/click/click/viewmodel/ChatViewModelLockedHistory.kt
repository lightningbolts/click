package compose.project.click.click.viewmodel // pragma: allowlist secret

import compose.project.click.click.data.models.isLockedHistory // pragma: allowlist secret
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Locked history unlocks by itself (sheet 10 A3, iOS #71 `ConversationModel.scheduleHistoryUnlockRetryIfNeeded`).
 *
 * Approved history is shared device-to-device, so a newly approved device can briefly show locked
 * rows while another device uploads the wrapped epoch keys. After each successful load, while the
 * chat is visible and any row is locked, reload with force after 2, 5, 10 and 20 s (at most four
 * tries). The count resets once nothing is locked or the chat goes away; going away also cancels
 * the pending retry. Normal message and realtime sync are unchanged.
 */
class LockedHistoryRetryScheduler(
    private val scope: CoroutineScope,
    private val delaysMs: List<Long> = DELAYS_MS,
    private val reload: suspend () -> Unit,
) {
    private var job: Job? = null

    /** Tries used since the last reset. */
    var attempt: Int = 0
        private set

    /** True while a retry is waiting to run. */
    val isPending: Boolean get() = job?.isActive == true

    /** Call after every successful load of the open chat. */
    fun onLoaded(
        visible: Boolean,
        anyLocked: Boolean,
    ) {
        job?.cancel()
        job = null
        if (!visible || !anyLocked) {
            attempt = 0
            return
        }
        if (attempt >= delaysMs.size) return
        val wait = delaysMs[attempt]
        attempt += 1
        job =
            scope.launch {
                delay(wait)
                reload()
            }
    }

    /** The chat stopped being visible: cancel the pending retry and start over next time. */
    fun cancel() {
        job?.cancel()
        job = null
        attempt = 0
    }

    companion object {
        val DELAYS_MS: List<Long> = listOf(2_000L, 5_000L, 10_000L, 20_000L)
    }
}

/** After a load applied to the open chat: schedule (or reset) the locked-history retry. */
internal fun ChatViewModel.scheduleLockedHistoryRetryIfNeeded() {
    val state = _chatMessagesState.value as? ChatMessagesState.Success
    val visible =
        state != null &&
            currentConnectionId != null &&
            state.chatDetails.connection.id == currentConnectionId
    lockedHistoryRetry.onLoaded(
        visible = visible,
        anyLocked = state?.messages?.any { it.message.isLockedHistory() } == true,
    )
}

/** A forced reload of the open chat (the retry's action); a no-op once the chat has closed. */
internal fun ChatViewModel.reloadOpenChatForLockedHistory() {
    val state = _chatMessagesState.value as? ChatMessagesState.Success ?: return
    val connectionId = currentConnectionId ?: return
    val apiChatId = currentApiChatId ?: return
    val userId = _currentUserId.value ?: return
    if (state.chatDetails.connection.id != connectionId) return
    scheduleBackgroundChatPayloadRefresh(
        hydratedChatDetails = state.chatDetails,
        apiChatId = apiChatId,
        userId = userId,
        connectionId = connectionId,
    )
}
