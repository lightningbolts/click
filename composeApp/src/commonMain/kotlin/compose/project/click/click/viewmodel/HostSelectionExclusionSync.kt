package compose.project.click.click.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Saves who this person removed while reviewing a group tap
 * (`POST /api/connections/proximity/selection`), so whichever phone confirms first leaves them out.
 *
 * Quick taps on Remove / Add back are coalesced: only the latest choice is sent, after
 * [debounceMs]. A choice the server already has is not sent again. Failures are not shown: the
 * confirm step still sends this person's own selection, and the next change retries.
 */
internal class HostSelectionExclusionSync(
    private val scope: CoroutineScope,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val post: suspend (pendingHandshakeId: String, excludedMemberIds: List<String>) -> Result<Unit>,
) {
    private var job: Job? = null
    private var lastSaved: Pair<String, List<String>>? = null

    /** One save at a time, in order (iOS `saveExclusions` chains on the previous task). */
    private val sendLock = Mutex()

    /** Queue [excludedMemberIds] (sorted, unique) for [pendingHandshakeId]. */
    fun submit(
        pendingHandshakeId: String,
        excludedMemberIds: List<String>,
    ) {
        val pendingId = pendingHandshakeId.trim()
        if (pendingId.isEmpty()) return
        val key = pendingId to excludedMemberIds.distinct().sorted()
        job?.cancel()
        // Nobody is excluded until someone is removed, so an empty first choice needs no call.
        val known = lastSaved ?: (pendingId to emptyList())
        if (known == key) return
        job =
            scope.launch {
                delay(debounceMs)
                // Once sending, a save is never abandoned half-way: a later one waits for it.
                send(key.first, key.second).onSuccess { lastSaved = key }
            }
    }

    /**
     * Leaving the review: drops a queued save, then saves [excludedMemberIds] after any save
     * already being sent has landed, so an older, partial list can't overwrite it.
     */
    fun leave(
        pendingHandshakeId: String,
        excludedMemberIds: List<String>,
    ) {
        val pendingId = pendingHandshakeId.trim()
        if (pendingId.isEmpty()) return
        val excluded = excludedMemberIds.distinct().sorted()
        reset()
        scope.launch { send(pendingId, excluded) }
    }

    private suspend fun send(
        pendingId: String,
        excluded: List<String>,
    ): Result<Unit> = withContext(NonCancellable) { sendLock.withLock { post(pendingId, excluded) } }

    /** Drop any queued save and forget what was sent (the review ended). */
    fun reset() {
        job?.cancel()
        job = null
        lastSaved = null
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 350L
    }
}
