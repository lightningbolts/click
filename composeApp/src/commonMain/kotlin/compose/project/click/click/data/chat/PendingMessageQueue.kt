package compose.project.click.click.data.chat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * Foundation for a chat outbox / pending message queue.
 *
 * R1.6 — Minimal foundation only. This queue is not yet wired into the live send flow;
 * it exists as an architectural seam so future offline-send / retry work can build on top
 * without disturbing the existing real-time path.
 *
 * Design notes:
 * - In-memory only for now. A future revision can persist via [TokenStorage]-style secure
 *   storage or a small SQLDelight table, but the failure modes (e.g. key rotation, clearing
 *   caches on sign-out) first need product sign-off.
 * - Identity is the client-generated [PendingMessage.clientId]. The server's message id is
 *   attached once the send succeeds, but the client id remains stable so the UI can reconcile
 *   optimistic rows with server rows.
 * - All mutations are serialized via [mutex] because [update] on nested maps with many chats
 *   is not atomic at the entry level.
 */
class PendingMessageQueue {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<Map<String, List<PendingMessage>>>(emptyMap())

    /** Observe all pending messages grouped by chat id. */
    val state: StateFlow<Map<String, List<PendingMessage>>> = _state.asStateFlow()

    /** Observe pending messages for a single chat id. */
    fun observeFor(chatId: String): Flow<List<PendingMessage>> =
        _state.map { it[chatId].orEmpty() }.distinctUntilChanged()

    /** Enqueue a new pending message. If a duplicate [PendingMessage.clientId] already exists,
     * returns the existing entry without modification. */
    suspend fun enqueue(message: PendingMessage): PendingMessage {
        mutex.withLock {
            val forChat = _state.value[message.chatId].orEmpty()
            forChat.firstOrNull { it.clientId == message.clientId }?.let { return it }
            _state.update { map ->
                map + (message.chatId to (forChat + message))
            }
            return message
        }
    }

    /** Mark a pending message as in-flight (network call started). */
    suspend fun markInFlight(chatId: String, clientId: String) {
        updateEntry(chatId, clientId) { it.copy(status = PendingMessageStatus.IN_FLIGHT, lastAttemptAtEpochMs = Clock.System.now().toEpochMilliseconds()) }
    }

    /** Mark a pending message as sent (server acknowledged). Keeps the row for one tick so
     * observers can reconcile optimistic UI; callers should call [remove] after reconciliation. */
    suspend fun markSent(chatId: String, clientId: String, serverMessageId: String) {
        updateEntry(chatId, clientId) {
            it.copy(status = PendingMessageStatus.SENT, serverMessageId = serverMessageId)
        }
    }

    /** Mark a pending message as failed. [errorKind] is a short, non-PII tag for retry policy. */
    suspend fun markFailed(chatId: String, clientId: String, errorKind: String) {
        updateEntry(chatId, clientId) {
            it.copy(
                status = PendingMessageStatus.FAILED,
                retryCount = it.retryCount + 1,
                lastErrorKind = errorKind,
            )
        }
    }

    /** Remove a pending message by id. No-op when missing. */
    suspend fun remove(chatId: String, clientId: String) {
        mutex.withLock {
            _state.update { map ->
                val remaining = (map[chatId].orEmpty()).filterNot { it.clientId == clientId }
                if (remaining.isEmpty()) map - chatId else map + (chatId to remaining)
            }
        }
    }

    /** Remove all entries. Intended for sign-out. */
    suspend fun clear() {
        mutex.withLock { _state.value = emptyMap() }
    }

    /** Snapshot all pending messages across chats. */
    fun snapshot(): List<PendingMessage> = _state.value.values.flatten()

    private suspend fun updateEntry(
        chatId: String,
        clientId: String,
        transform: (PendingMessage) -> PendingMessage,
    ) {
        mutex.withLock {
            _state.update { map ->
                val list = map[chatId] ?: return@update map
                val idx = list.indexOfFirst { it.clientId == clientId }
                if (idx < 0) return@update map
                val updated = list.toMutableList().also { it[idx] = transform(it[idx]) }
                map + (chatId to updated.toList())
            }
        }
    }
}

/**
 * A single pending (outbox) message. This is wire-format-agnostic on purpose: the repository
 * knows how to encrypt + send, this record only tracks enough metadata to reconcile with the
 * server row once it lands.
 */
data class PendingMessage(
    val clientId: String,
    val chatId: String,
    val senderId: String,
    val plaintext: String?,
    val mediaKind: PendingMediaKind = PendingMediaKind.NONE,
    val mediaRef: String? = null,
    val createdAtEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
    val status: PendingMessageStatus = PendingMessageStatus.QUEUED,
    val retryCount: Int = 0,
    val lastAttemptAtEpochMs: Long? = null,
    val lastErrorKind: String? = null,
    val serverMessageId: String? = null,
)

enum class PendingMessageStatus {
    /** Sitting in the outbox, ready to send. */
    QUEUED,

    /** Repository has an in-flight request to the backend. */
    IN_FLIGHT,

    /** Last send attempt failed; will be retried per policy. */
    FAILED,

    /** Server acknowledged. Row is kept briefly for optimistic reconciliation. */
    SENT,
}

enum class PendingMediaKind {
    NONE,
    IMAGE,
    AUDIO,
}
