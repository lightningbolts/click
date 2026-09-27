package compose.project.click.click.data.chat // pragma: allowlist secret

import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A text send the server hasn't confirmed yet (iOS `PendingSendStore`). Plaintext stays on this
 * device only, in the app's encrypted prefs, until the send lands or the user discards it.
 */
@Serializable
data class PendingSend(
    /** The optimistic row id (`temp-…`), stable across restarts. */
    val tempId: String,
    val userId: String,
    /** The chat key the UI opened (connection id or group id). */
    val threadKey: String,
    val apiChatId: String,
    val content: String,
    val metadataJson: String? = null,
    val localSentAtMs: Long,
    /** Reused by every retry so the server can drop a duplicate of a send that did land. */
    val clientMessageId: String,
    val sendConnectionId: String? = null,
    val failed: Boolean = false,
)

/** Per-user outbox persisted in [TokenStorage]; bounded to [MAX] entries (oldest dropped). */
object PendingSendStore {
    const val MAX = 100

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val _items = MutableStateFlow<List<PendingSend>>(emptyList())
    val items: StateFlow<List<PendingSend>> = _items.asStateFlow()
    private var loadedForUser: String? = null

    /** Pure: adds or replaces by [PendingSend.tempId], keeping the newest [max]. */
    fun upserted(
        items: List<PendingSend>,
        item: PendingSend,
        max: Int = MAX,
    ): List<PendingSend> = (items.filterNot { it.tempId == item.tempId } + item).takeLast(max)

    fun forThread(
        threadKey: String,
        userId: String,
    ): List<PendingSend> = _items.value.filter { it.threadKey == threadKey && it.userId == userId }

    fun byTempId(tempId: String): PendingSend? = _items.value.firstOrNull { it.tempId == tempId }

    suspend fun load(
        storage: TokenStorage,
        userId: String,
    ) {
        mutex.withLock {
            if (loadedForUser == userId) return
            val stored =
                storage
                    .getPendingSends()
                    ?.let { raw -> runCatching { json.decodeFromString<List<PendingSend>>(raw) }.getOrNull() }
                    .orEmpty()
            // Another account's rows never show; they stay out of memory until sign-out wipes them.
            _items.value = stored.filter { it.userId == userId }
            loadedForUser = userId
        }
    }

    suspend fun upsert(
        storage: TokenStorage,
        item: PendingSend,
    ) = mutate(storage) { upserted(it, item) }

    suspend fun remove(
        storage: TokenStorage,
        tempId: String,
    ) = mutate(storage) { list -> list.filterNot { it.tempId == tempId } }

    suspend fun markFailed(
        storage: TokenStorage,
        tempId: String,
        failed: Boolean = true,
    ) = mutate(storage) { list -> list.map { if (it.tempId == tempId) it.copy(failed = failed) else it } }

    private suspend fun mutate(
        storage: TokenStorage,
        transform: (List<PendingSend>) -> List<PendingSend>,
    ) {
        mutex.withLock {
            val next = transform(_items.value)
            if (next == _items.value) return
            _items.value = next
            storage.savePendingSends(if (next.isEmpty()) null else json.encodeToString(next))
        }
    }

    /** Sign-out: plaintext drafts never carry over to another account. */
    suspend fun clear(storage: TokenStorage) {
        mutex.withLock {
            _items.value = emptyList()
            loadedForUser = null
            storage.savePendingSends(null)
        }
    }
}
