package compose.project.click.click.data // pragma: allowlist secret

import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.getChatMutesImpl // pragma: allowlist secret
import compose.project.click.click.data.api.putChatMuteImpl // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** How long to mute a conversation (iOS ChatView "Mute Notifications"). */
enum class ChatMuteDuration(
    val label: String,
    /** Null: until turned back on. */
    val durationMs: Long?,
) {
    ONE_HOUR("For 1 hour", 60 * 60 * 1000L),
    EIGHT_HOURS("For 8 hours", 8 * 60 * 60 * 1000L),
    ONE_WEEK("For 1 week", 7 * 24 * 60 * 60 * 1000L),
    FOREVER("Until I turn it back on", null),
}

/**
 * The viewer's per-chat push mutes (chat or hub id → muted-until epoch ms, [FOREVER] for "until
 * turned back on"). Updates are optimistic with rollback. Persisted so the FCM service can drop a
 * muted push even when the server check was skipped (defense in depth).
 */
object ChatMuteStore {
    const val FOREVER: Long = Long.MAX_VALUE

    private val _mutes = MutableStateFlow<Map<String, Long>>(emptyMap())
    val mutes: StateFlow<Map<String, Long>> = _mutes.asStateFlow()

    private val serializer = MapSerializer(String.serializer(), Long.serializer())

    fun isMuted(
        chatId: String?,
        nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
    ): Boolean = isMuted(_mutes.value, chatId, nowEpochMs)

    fun isMuted(
        mutes: Map<String, Long>,
        chatId: String?,
        nowEpochMs: Long,
    ): Boolean {
        val until = chatId?.let { mutes[it] } ?: return false
        return until > nowEpochMs
    }

    suspend fun refresh(
        api: ApiClient = ApiClient(),
        storage: TokenStorage = createTokenStorage(),
    ) {
        api.getChatMutesImpl().onSuccess { rows ->
            val mapped =
                rows.associate { row ->
                    row.chatId to (row.mutedUntil?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: FOREVER)
                }
            _mutes.value = mapped
            persist(storage, mapped)
        }
    }

    /** Mutes (or with [duration] null, unmutes) [chatId]. Returns false and rolls back on failure. */
    suspend fun setMuted(
        chatId: String,
        duration: ChatMuteDuration?,
        api: ApiClient = ApiClient(),
        storage: TokenStorage = createTokenStorage(),
        nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
    ): Boolean {
        val before = _mutes.value
        val until = duration?.let { d -> d.durationMs?.let { nowEpochMs + it } ?: FOREVER }
        _mutes.value = if (until == null) before - chatId else before + (chatId to until)
        val iso = until?.takeIf { it != FOREVER }?.let { Instant.fromEpochMilliseconds(it).toString() }
        val ok = api.putChatMuteImpl(chatId, muted = until != null, mutedUntilIso = iso).isSuccess
        if (!ok) _mutes.value = before else persist(storage, _mutes.value)
        return ok
    }

    /** Reads the persisted mutes (used by the FCM service, which may run without the UI). */
    suspend fun loadPersisted(storage: TokenStorage): Map<String, Long> =
        storage.getChatMutesCache()?.let { runCatching { Json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private suspend fun persist(
        storage: TokenStorage,
        mutes: Map<String, Long>,
    ) {
        runCatching { storage.saveChatMutesCache(Json.encodeToString(serializer, mutes)) }
    }

    fun clear() {
        _mutes.value = emptyMap()
    }
}
