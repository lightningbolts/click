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

    /** Muted under any of [ids] (a direct chat's mute may be stored under its connection id; iOS `isMuted(_:)`). */
    fun isMuted(
        mutes: Map<String, Long>,
        ids: List<String?>,
        nowEpochMs: Long,
    ): Boolean = ids.any { isMuted(mutes, it, nowEpochMs) }

    /** When a mute picked [nowEpochMs] for [duration] ends; [FOREVER] for "until I turn it back on". */
    fun muteUntilEpochMs(
        duration: ChatMuteDuration,
        nowEpochMs: Long,
    ): Long = duration.durationMs?.let { nowEpochMs + it } ?: FOREVER

    /** The soonest timed mute still running after [nowEpochMs] (when a muted bell should clear), or null. */
    fun nextExpiryEpochMs(
        mutes: Map<String, Long>,
        nowEpochMs: Long,
    ): Long? = mutes.values.filter { it in (nowEpochMs + 1) until FOREVER }.minOrNull()

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

    /**
     * Mutes (or with [duration] null, unmutes) [chatId]. Unmuting also clears a mute stored under
     * any of [aliases] (a direct chat's connection id) so the conversation really turns back on.
     * Returns false and rolls back on failure.
     */
    suspend fun setMuted(
        chatId: String,
        duration: ChatMuteDuration?,
        api: ApiClient = ApiClient(),
        storage: TokenStorage = createTokenStorage(),
        nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
        aliases: List<String?> = emptyList(),
    ): Boolean {
        val before = _mutes.value
        val until = duration?.let { muteUntilEpochMs(it, nowEpochMs) }
        if (until == null) {
            val ids = (listOf(chatId) + aliases.filterNotNull().filter { it.isNotBlank() && it in before }).distinct()
            _mutes.value = before - ids.toSet()
            val ok = ids.all { api.putChatMuteImpl(it, muted = false, mutedUntilIso = null).isSuccess }
            if (!ok) _mutes.value = before else persist(storage, _mutes.value)
            return ok
        }
        _mutes.value = before + (chatId to until)
        val ok = api.putChatMuteImpl(chatId, muted = true, mutedUntilIso = mutedUntilIso(until)).isSuccess
        if (!ok) _mutes.value = before else persist(storage, _mutes.value)
        return ok
    }

    /** The `muted_until` sent to the server: ISO-8601, or null for "until I turn it back on". */
    fun mutedUntilIso(untilEpochMs: Long): String? =
        untilEpochMs.takeIf { it != FOREVER }?.let { Instant.fromEpochMilliseconds(it).toString() }

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
