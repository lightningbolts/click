package compose.project.click.click.data // pragma: allowlist secret

import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.DISPLAY_NAMES_BATCH_MAX // pragma: allowlist secret
import compose.project.click.click.data.api.DisplayNamesResponseDto // pragma: allowlist secret
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/** A person's display name and avatar as click-web resolves them for the viewer. */
data class UserIdentity(
    val name: String?,
    val avatarUrl: String?,
)

/**
 * Names for people the viewer isn't connected to (group members, hub participants, reactors,
 * blocked people), matching iOS `IdentityCache`: unknown ids are batched after a 100 ms pause into
 * one `POST /api/users/display-names`, and answers are kept for an hour. Connected users are
 * already in [AppDataManager.connectedUsers]; callers check that first.
 */
object IdentityCache {
    const val TTL_MS: Long = 60 * 60 * 1000L
    const val DEBOUNCE_MS: Long = 100L

    internal data class Entry(
        val identity: UserIdentity,
        val fetchedAtMs: Long,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val pending = linkedSetOf<String>()
    private var flushJob: Job? = null

    private val entryCache = MutableStateFlow<Map<String, Entry>>(emptyMap())

    private val _identities = MutableStateFlow<Map<String, UserIdentity>>(emptyMap())

    /** Every resolved identity; recomposes callers as batches land. */
    val identities: StateFlow<Map<String, UserIdentity>> = _identities.asStateFlow()

    /** Swappable for tests. */
    internal var fetcher: suspend (List<String>) -> Result<DisplayNamesResponseDto> = { ids ->
        ApiClient().getDisplayNames(ids)
    }

    internal var now: () -> Long = { Clock.System.now().toEpochMilliseconds() }

    /** Ids that are unknown or older than [TTL_MS]. */
    internal fun idsNeedingFetch(
        ids: Collection<String>,
        cache: Map<String, Entry>,
        nowMs: Long,
        ttlMs: Long = TTL_MS,
    ): List<String> =
        ids
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .filter { id ->
                val entry = cache[id]
                entry == null || nowMs - entry.fetchedAtMs >= ttlMs
            }

    fun name(userId: String?): String? = userId?.let { _identities.value[it]?.name }

    /** Queues [userIds] for the next batch; already-fresh ids are skipped. */
    fun request(userIds: Collection<String>) {
        val missing = idsNeedingFetch(userIds, entryCache.value, now())
        if (missing.isEmpty()) return
        scope.launch {
            mutex.withLock {
                pending += missing
                if (flushJob?.isActive != true) {
                    flushJob =
                        scope.launch {
                            delay(DEBOUNCE_MS)
                            flush()
                        }
                }
            }
        }
    }

    /** Resolves [userIds] now (bypassing the debounce) and returns what is known afterwards. */
    suspend fun resolve(userIds: Collection<String>): Map<String, UserIdentity> {
        val missing = idsNeedingFetch(userIds, entryCache.value, now())
        if (missing.isNotEmpty()) fetch(missing)
        val known = _identities.value
        return userIds.mapNotNull { id -> known[id]?.let { id to it } }.toMap()
    }

    private suspend fun flush() {
        val batch =
            mutex.withLock {
                val ids = pending.toList()
                pending.clear()
                ids
            }
        if (batch.isNotEmpty()) fetch(batch)
    }

    private suspend fun fetch(ids: List<String>) {
        ids.chunked(DISPLAY_NAMES_BATCH_MAX).forEach { chunk ->
            val response = fetcher(chunk).getOrNull() ?: return@forEach
            val fetchedAt = now()
            entryCache.update { current ->
                current +
                    chunk.map { id ->
                        id to
                            Entry(
                                identity =
                                    UserIdentity(
                                        name = response.names[id]?.trim()?.takeIf { it.isNotEmpty() },
                                        avatarUrl = response.images[id]?.trim()?.takeIf { it.isNotEmpty() },
                                    ),
                                fetchedAtMs = fetchedAt,
                            )
                    }
            }
            _identities.value = entryCache.value.mapValues { it.value.identity }
        }
    }

    /** Sign-out: names never carry over to another account. */
    fun clear() {
        entryCache.value = emptyMap()
        _identities.value = emptyMap()
        scope.launch { mutex.withLock { pending.clear() } }
    }
}
