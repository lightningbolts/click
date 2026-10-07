package compose.project.click.click.viewmodel

import compose.project.click.click.data.api.ActivityRecapDto // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the Home "Your recap" module shows. A recap is never faked as zeros (spec §20.3). */
sealed interface RecapState {
    data object Loading : RecapState

    /** [stale] = showing the last saved value because this session's refresh failed. */
    data class Loaded(
        val recap: ActivityRecapDto,
        val stale: Boolean,
    ) : RecapState

    /** Nothing cached and the request failed: offer Retry. */
    data object Failed : RecapState
}

/** iOS order and labels; only non-zero rows are shown. */
fun ActivityRecapDto.visibleRows(): List<Pair<String, Int>> =
    listOf(
        "New Clicks" to connectionsFormed,
        "Messages sent" to messagesSent,
        "Messages received" to messagesReceived,
        "Events RSVP’d" to eventsRsvped,
        "Check-ins" to eventsCheckedIn,
        "Events saved" to eventsSaved,
        "Beacons dropped" to beaconsCreated,
    ).filter { it.second > 0 }

/** Per-user on-disk recap cache (both windows), so a relaunch shows real numbers immediately. */
interface RecapDiskCache {
    suspend fun load(userId: String): Map<String, ActivityRecapDto>

    suspend fun save(
        userId: String,
        recaps: Map<String, ActivityRecapDto>,
    )
}

@Serializable
private data class StoredRecaps(
    val userId: String,
    val recaps: Map<String, ActivityRecapDto>,
)

class TokenStorageRecapCache(
    private val storage: TokenStorage,
) : RecapDiskCache {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(userId: String): Map<String, ActivityRecapDto> =
        storage
            .getHomeRecapCache()
            ?.let { runCatching { json.decodeFromString(StoredRecaps.serializer(), it) }.getOrNull() }
            ?.takeIf { it.userId == userId }
            ?.recaps
            .orEmpty()

    override suspend fun save(
        userId: String,
        recaps: Map<String, ActivityRecapDto>,
    ) {
        storage.saveHomeRecapCache(json.encodeToString(StoredRecaps.serializer(), StoredRecaps(userId, recaps)))
    }
}

/**
 * Loads the recap like iOS `HomeFeedModel`: seed both windows from disk, fetch only the selected
 * window, fetch the other window only when it is first selected this session, and on refresh reload
 * only the selected window (the other keeps its cached value).
 */
class ActivityRecapController(
    private val scope: CoroutineScope,
    private val fetch: suspend (window: String) -> Result<ActivityRecapDto>,
    private val cache: RecapDiskCache,
) {
    private val _window = MutableStateFlow(WEEK)
    val window: StateFlow<String> = _window.asStateFlow()

    private val _state = MutableStateFlow<RecapState>(RecapState.Loading)
    val state: StateFlow<RecapState> = _state.asStateFlow()

    private var userId: String? = null
    private var generation = 0L
    private val values = mutableMapOf<String, ActivityRecapDto>()
    private val loadedThisSession = mutableSetOf<String>()
    private val failedThisSession = mutableSetOf<String>()
    private val jobs = mutableMapOf<String, Job>()

    /** Account switch or first load. Idempotent for the same user. */
    fun start(newUserId: String) {
        if (userId == newUserId) return
        reset()
        userId = newUserId
        val gen = generation
        scope.launch {
            val cached = runCatching { cache.load(newUserId) }.getOrDefault(emptyMap())
            if (gen != generation) return@launch
            cached.forEach { (w, dto) -> values.putIfAbsent(w, dto) }
            publish()
            load(_window.value)
        }
    }

    fun reset() {
        generation++
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        userId = null
        values.clear()
        loadedThisSession.clear()
        failedThisSession.clear()
        _state.value = RecapState.Loading
    }

    fun select(window: String) {
        val normalized = normalize(window)
        _window.value = normalized
        publish()
        if (normalized !in loadedThisSession) load(normalized)
    }

    /** Pull-to-refresh: reload only the selected window. */
    fun refresh() {
        val w = _window.value
        loadedThisSession.remove(w)
        load(w)
    }

    fun retry() = load(_window.value)

    private fun load(window: String) {
        val uid = userId ?: return
        if (jobs[window]?.isActive == true) return
        val gen = generation
        failedThisSession.remove(window)
        publish()
        jobs[window] =
            scope.launch {
                val result = fetch(window)
                if (gen != generation || uid != userId) return@launch
                result
                    .onSuccess { dto ->
                        values[window] = dto
                        loadedThisSession += window
                        runCatching { cache.save(uid, values.toMap()) }
                    }.onFailure { failedThisSession += window }
                publish()
            }
    }

    private fun publish() {
        val w = _window.value
        val value = values[w]
        _state.value =
            when {
                value != null -> RecapState.Loaded(value, stale = w in failedThisSession)
                w in failedThisSession -> RecapState.Failed
                else -> RecapState.Loading
            }
    }

    companion object {
        const val DAY = "day"
        const val WEEK = "week"

        fun normalize(window: String): String = if (window == DAY) DAY else WEEK
    }
}
