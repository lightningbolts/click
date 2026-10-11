package compose.project.click.click.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Server-driven feature flags (`GET /api/me/features`, web `lib/server/featureFlags.ts`). Every
 * post-9/29 feature ships dark behind one; an unknown, unloaded or failed flag is simply off.
 * The last flags each user got are kept on device (encrypted), so a cold launch paints flagged
 * screens on the first frame and the network refresh only corrects them (iOS `FeatureFlags`).
 */
object FeatureKeys {
    const val DROPS_DEVELOP = "drops_develop"
    const val ALERT_CONFIRMATIONS = "alert_confirmations"
    const val SOUNDTRACK_PRESENCE = "soundtrack_presence"
    const val EVENT_DROPS = "event_drops"
    const val EVENT_HISTORY = "event_history"
    const val SHARED_DROPS = "shared_drops"
    const val RECONNECT_NEARBY = "reconnect_nearby"
    const val CLICK_PLACES = "click_places"

    /** `pilot_analytics` is server-only (emission is gated there). */
    val all: List<String> =
        listOf(
            DROPS_DEVELOP,
            ALERT_CONFIRMATIONS,
            SOUNDTRACK_PRESENCE,
            EVENT_DROPS,
            EVENT_HISTORY,
            SHARED_DROPS,
            RECONNECT_NEARBY,
            CLICK_PLACES,
        )
}

@Serializable
data class ResolvedFeature(
    val enabled: Boolean = false,
    val config: JsonObject = JsonObject(emptyMap()),
)

@Serializable
private data class FeaturesResponse(
    val features: Map<String, ResolvedFeature> = emptyMap(),
)

/** A finite number within [min, max], else the default (web `configNumber`). */
internal fun JsonObject.configNumber(
    name: String,
    fallback: Double,
    min: Double,
    max: Double,
): Double {
    val raw = (this[name] as? JsonPrimitive)?.doubleOrNull ?: return fallback
    if (!raw.isFinite() || raw < min || raw > max) return fallback
    return raw
}

private fun JsonObject.configInt(
    name: String,
    fallback: Int,
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
): Int = configNumber(name, fallback.toDouble(), min.toDouble(), max.toDouble()).toInt()

// Typed config. Code defaults equal the server seeds (click-web supabase/migrations).

data class SharedDropsConfig(
    val developHours: Int = 1,
    val dailyCap: Int = 3,
    val stripWindowHours: Int = 24,
    val stripMin: Int = 25,
    val stripMax: Int = 150,
)

data class EventDropsConfig(
    val perUserCap: Int = 10,
    val revealHourLocal: Int = 10,
    val absenteeLimit: Int = 6,
)

data class EventHistoryConfig(
    val recapCardHours: Int = 48,
)

data class SoundtrackPresenceConfig(
    val heartbeatTtlMinutes: Int = 12,
)

data class AlertConfirmationsConfig(
    val ttlMinutes: Int = 120,
    val radiusMeters: Int = 300,
)

data class ClickPlacesConfig(
    val checkinTtlMinutes: Int = 180,
    val gpsMaxAccuracyMeters: Int = 100,
    val pulseCooldownMinutes: Int = 45,
    val nearbyDefaultRadiusMeters: Int = 5_000,
    val nearbyMaxRadiusMeters: Int = 50_000,
    val nearbyMaxLimit: Int = 200,
)

/** One user's resolved flags. Immutable; the store publishes a new instance per change. */
data class FeatureFlagsState(
    val userId: String? = null,
    val features: Map<String, ResolvedFeature> = emptyMap(),
) {
    fun isEnabled(key: String): Boolean = features[key]?.enabled == true

    fun config(key: String): JsonObject = features[key]?.config ?: JsonObject(emptyMap())

    val sharedDrops: SharedDropsConfig
        get() =
            config(FeatureKeys.SHARED_DROPS).let { c ->
                SharedDropsConfig(
                    developHours = c.configInt("develop_hours", 1, 0, 168),
                    dailyCap = c.configInt("daily_cap", 3, 1, 50),
                    stripWindowHours = c.configInt("strip_window_hours", 24, 1, 168),
                    stripMin = c.configInt("strip_min", 25, 1, 100),
                    stripMax = c.configInt("strip_max", 150, 25, 200),
                )
            }

    val eventDrops: EventDropsConfig
        get() =
            config(FeatureKeys.EVENT_DROPS).let { c ->
                EventDropsConfig(
                    perUserCap = c.configInt("per_user_cap", 10, 1, 100),
                    revealHourLocal = c.configInt("reveal_hour_local", 10, 0, 23),
                    absenteeLimit = c.configInt("absentee_limit", 6, 0, 100),
                )
            }

    val eventHistory: EventHistoryConfig
        get() = EventHistoryConfig(config(FeatureKeys.EVENT_HISTORY).configInt("recap_card_hours", 48, 1, 720))

    val soundtrackPresence: SoundtrackPresenceConfig
        get() = SoundtrackPresenceConfig(config(FeatureKeys.SOUNDTRACK_PRESENCE).configInt("heartbeat_ttl_minutes", 12, 1, 120))

    val alertConfirmations: AlertConfirmationsConfig
        get() =
            config(FeatureKeys.ALERT_CONFIRMATIONS).let { c ->
                AlertConfirmationsConfig(
                    ttlMinutes = c.configInt("ttl_minutes", 120, 1, 1_440),
                    radiusMeters = c.configInt("radius_meters", 300, 1, 50_000),
                )
            }

    val clickPlaces: ClickPlacesConfig
        get() =
            config(FeatureKeys.CLICK_PLACES).let { c ->
                ClickPlacesConfig(
                    checkinTtlMinutes = c.configInt("checkin_ttl_minutes", 180, 1, 1_440),
                    gpsMaxAccuracyMeters = c.configInt("gps_max_accuracy_meters", 100, 1, 5_000),
                    pulseCooldownMinutes = c.configInt("pulse_cooldown_minutes", 45, 0, 1_440),
                    nearbyDefaultRadiusMeters = c.configInt("nearby_default_radius_meters", 5_000, 1, 500_000),
                    nearbyMaxRadiusMeters = c.configInt("nearby_max_radius_meters", 50_000, 1, 500_000),
                    nearbyMaxLimit = c.configInt("nearby_max_limit", 200, 1, 1_000),
                )
            }

    companion object {
        val Off = FeatureFlagsState()
    }
}

/** Per-user flag storage (Android: EncryptedSharedPreferences). */
interface FeatureFlagsPersistence {
    fun read(userId: String): String?

    fun write(
        userId: String,
        json: String,
    )

    fun delete(userId: String)

    fun lastUserId(): String?

    fun setLastUserId(userId: String?)
}

class InMemoryFeatureFlagsPersistence : FeatureFlagsPersistence {
    private val byUser = mutableMapOf<String, String>()
    private var last: String? = null

    override fun read(userId: String): String? = byUser[userId]

    override fun write(
        userId: String,
        json: String,
    ) {
        byUser[userId] = json
    }

    override fun delete(userId: String) {
        byUser.remove(userId)
    }

    override fun lastUserId(): String? = last

    override fun setLastUserId(userId: String?) {
        last = userId
    }
}

private val flagsJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

/** `{features:{k:{enabled,config}}}` → flags, or null when the body is malformed (caller keeps all off). */
internal fun parseFeatureFlags(raw: String?): Map<String, ResolvedFeature>? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val root = flagsJson.parseToJsonElement(raw).jsonObject
        flagsJson.decodeFromJsonElement(FeaturesResponse.serializer(), root).features
    }.getOrNull()
}

private fun encodeFeatureFlags(features: Map<String, ResolvedFeature>): String =
    flagsJson.encodeToString(FeaturesResponse.serializer(), FeaturesResponse(features))

/**
 * The flag store. [fetch] returns the raw `/api/me/features` body (null on any failure); a failed
 * or malformed refresh keeps the last known flags.
 */
class FeatureFlagsStore(
    private val persistence: () -> FeatureFlagsPersistence,
    private val fetch: suspend () -> String?,
) {
    private val _state =
        kotlinx.coroutines.flow.MutableStateFlow(FeatureFlagsState.Off)
    val state: kotlinx.coroutines.flow.StateFlow<FeatureFlagsState> = _state

    private val refreshMutex = Mutex()

    fun isEnabled(key: String): Boolean = _state.value.isEnabled(key)

    /** Launch: start from the last signed-in user's flags so flagged screens paint on frame one. */
    fun restoreAtLaunch() {
        restore(runCatching { persistence().lastUserId() }.getOrNull())
    }

    /** Flags are per user: switch to this user's last known flags (none when signed out). */
    fun restore(userId: String?) {
        val uid = userId?.trim()?.takeIf { it.isNotEmpty() }
        if (uid != null && uid == _state.value.userId) return
        val stored = uid?.let { runCatching { persistence().read(it) }.getOrNull() }
        _state.value = FeatureFlagsState(uid, parseFeatureFlags(stored).orEmpty())
        runCatching { persistence().setLastUserId(uid) }
    }

    /** Sign-in and foreground. Returns true when the server answered with a valid body. */
    suspend fun refresh(): Boolean =
        refreshMutex.withLock {
            val uid = _state.value.userId ?: return@withLock false
            val features = parseFeatureFlags(runCatching { fetch() }.getOrNull()) ?: return@withLock false
            // A sign-out or account switch landed while the request ran: drop the stale answer.
            if (_state.value.userId != uid) return@withLock false
            _state.value = FeatureFlagsState(uid, features)
            runCatching { persistence().write(uid, encodeFeatureFlags(features)) }
            true
        }

    /** Sign-out: nothing is on, and this user's stored flags are gone. */
    fun clear() {
        val uid = _state.value.userId
        _state.value = FeatureFlagsState.Off
        runCatching {
            uid?.let { persistence().delete(it) }
            persistence().setLastUserId(null)
        }
    }
}
