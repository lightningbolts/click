package compose.project.click.click.data.flags

import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * Server-driven feature flags (`GET /api/me/features`, click-web `lib/server/featureFlags.ts`).
 * Every post-launch feature ships dark and is turned on per cohort on the server. Mirrors iOS
 * `Click/Core/Config/FeatureFlags.swift`:
 * - an unknown, unloaded or failed flag is off;
 * - the last flags each user got are kept on device, so flagged screens paint on the first frame;
 * - a failed refresh keeps the last known flags;
 * - signing out turns everything off.
 */
enum class FeatureFlag(
    val wireKey: String,
) {
    DROPS_DEVELOP("drops_develop"),
    ALERT_CONFIRMATIONS("alert_confirmations"),
    SOUNDTRACK_PRESENCE("soundtrack_presence"),
    EVENT_DROPS("event_drops"),
    EVENT_HISTORY("event_history"),
    SHARED_DROPS("shared_drops"),
    RECONNECT_NEARBY("reconnect_nearby"),
    CLICK_PLACES("click_places"),

    /** Buying and organizing tickets (server: ticketing environment switch). */
    TICKET_SALES("ticket_sales"),

    /** Your own tickets; stays on for ticket holders while sales are off. */
    TICKET_WALLET("ticket_wallet"),

    /**
     * Kill switch (one release): when on, QR / manual connects fall back to the legacy direct
     * PostgREST inserts instead of `POST /api/connections` (G4-10). Off unless the server sends it.
     */
    CONNECT_POSTGREST_FALLBACK("connect_postgrest_fallback"),
}

/** One user's resolved flags: wire key → that flag's `config` (present only when enabled). */
data class FeatureFlagsSnapshot(
    val userId: String? = null,
    val enabled: Map<String, JsonObject> = emptyMap(),
) {
    fun isEnabled(flag: FeatureFlag): Boolean = flag.wireKey in enabled

    fun config(flag: FeatureFlag): JsonObject = enabled[flag.wireKey] ?: EMPTY_CONFIG

    /**
     * A numeric config value (numbers or numeric strings, like click-web `configNumber`), falling
     * back to [default] when missing or invalid.
     */
    fun configNumber(
        flag: FeatureFlag,
        name: String,
        default: Double,
    ): Double {
        val raw = config(flag)[name] as? JsonPrimitive ?: return default
        val value = raw.doubleOrNull ?: raw.contentOrNull?.toDoubleOrNull()
        return value?.takeIf { it.isFinite() } ?: default
    }

    companion object {
        private val EMPTY_CONFIG = JsonObject(emptyMap())
        val OFF = FeatureFlagsSnapshot()
    }
}

/**
 * Parses `{features: {key: {enabled, config}}}`. Returns null when the body isn't that shape (keep
 * the last known flags). A flag whose `enabled` isn't literally `true` is off.
 */
internal fun parseFeatureFlagsResponse(body: JsonElement): Map<String, JsonObject>? {
    val features = (body as? JsonObject)?.get("features") as? JsonObject ?: return null
    return features
        .mapNotNull { (key, value) ->
            val row = value as? JsonObject ?: return@mapNotNull null
            val enabled = row["enabled"] as? JsonPrimitive
            // Only a JSON boolean counts; a quoted "true" is malformed, so off.
            if (enabled == null || enabled.isString || enabled.booleanOrNull != true) return@mapNotNull null
            key to ((row["config"] as? JsonObject) ?: JsonObject(emptyMap()))
        }.toMap()
}

private val cacheJson = Json { ignoreUnknownKeys = true }

/** Stored as `{user_id, features: {key: config}}` so another account's flags are never restored. */
internal fun encodeFeatureFlagsCache(snapshot: FeatureFlagsSnapshot): String? {
    val userId = snapshot.userId ?: return null
    return buildJsonObject {
        put("user_id", userId)
        put("features", JsonObject(snapshot.enabled))
    }.toString()
}

internal fun decodeFeatureFlagsCache(
    raw: String?,
    userId: String,
): FeatureFlagsSnapshot? {
    val root = raw?.let { runCatching { cacheJson.parseToJsonElement(it) }.getOrNull() } as? JsonObject ?: return null
    if ((root["user_id"] as? JsonPrimitive)?.contentOrNull != userId) return null
    val features = root["features"] as? JsonObject ?: return null
    return FeatureFlagsSnapshot(
        userId = userId,
        enabled = features.mapNotNull { (k, v) -> (v as? JsonObject)?.let { k to it } }.toMap(),
    )
}

object FeatureFlags {
    private val _state = MutableStateFlow(FeatureFlagsSnapshot.OFF)
    val state: StateFlow<FeatureFlagsSnapshot> = _state.asStateFlow()
    private val mutex = Mutex()

    fun isEnabled(flag: FeatureFlag): Boolean = _state.value.isEnabled(flag)

    /** Switches to [userId]'s last known flags (all off when there are none or [userId] is blank). */
    suspend fun restore(
        userId: String,
        storage: TokenStorage,
    ) = mutex.withLock {
        val uid = userId.trim()
        if (uid.isEmpty()) {
            _state.value = FeatureFlagsSnapshot.OFF
            return@withLock
        }
        if (_state.value.userId == uid) return@withLock
        _state.value =
            decodeFeatureFlagsCache(runCatching { storage.getFeatureFlagsCache() }.getOrNull(), uid)
                ?: FeatureFlagsSnapshot(userId = uid)
    }

    /** Fetches [userId]'s flags; on failure or a malformed body the last known flags stay. */
    suspend fun refresh(
        userId: String,
        storage: TokenStorage,
        fetch: suspend () -> Result<JsonElement>,
    ) {
        val uid = userId.trim()
        if (uid.isEmpty()) return
        restore(uid, storage)
        val parsed = fetch().getOrNull()?.let(::parseFeatureFlagsResponse) ?: return
        mutex.withLock {
            // Signed out or switched account while the request was in flight.
            if (_state.value.userId != uid) return@withLock
            val next = FeatureFlagsSnapshot(userId = uid, enabled = parsed)
            _state.value = next
            runCatching { storage.saveFeatureFlagsCache(encodeFeatureFlagsCache(next)) }
        }
    }

    /** Signed out: nothing is on, and the cached flags are dropped. */
    suspend fun reset(storage: TokenStorage?) =
        mutex.withLock {
            _state.value = FeatureFlagsSnapshot.OFF
            runCatching { storage?.saveFeatureFlagsCache(null) }
        }
}
