package compose.project.click.click.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
                post(key.first, key.second).onSuccess { lastSaved = key }
            }
    }

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
