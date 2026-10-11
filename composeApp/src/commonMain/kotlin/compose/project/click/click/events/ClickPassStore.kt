package compose.project.click.click.events // pragma: allowlist secret

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** A string key-value store; [EventsDiskStore.backing] is swapped for a map in unit tests. */
interface EventsKeyValue {
    fun get(key: String): String?

    fun put(
        key: String,
        value: String?,
    )
}

/** Small per-user disk caches for the events feature (G1-5 keys `events.*`). */
object EventsDiskStore {
    var backing: EventsKeyValue =
        object : EventsKeyValue {
            private val settings: Settings? by lazy { runCatching { Settings() }.getOrNull() }

            override fun get(key: String): String? = runCatching { settings?.getStringOrNull(key) }.getOrNull()

            override fun put(
                key: String,
                value: String?,
            ) {
                runCatching {
                    if (value == null) settings?.remove(key) else settings?.putString(key, value)
                }
            }
        }
}

/**
 * Click Passes kept on the device, per user (`events.engagement.passes`), so the pass opens at once
 * and works without signal. A 403 or an RSVP cancel clears it (iOS `EventEngagementRepository`).
 */
object ClickPassStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), ClickPass.serializer())
    private var userId: String? = null
    private val _passes = MutableStateFlow<Map<String, ClickPass>>(emptyMap())
    val passes: StateFlow<Map<String, ClickPass>> = _passes.asStateFlow()

    private fun key(user: String) = "events.engagement.passes.$user"

    /** Loads the signed-in user's passes; a different user never sees another's. */
    fun bindUser(user: String?) {
        val id = user?.trim()?.takeIf { it.isNotEmpty() }
        if (id == userId) return
        userId = id
        _passes.value =
            id
                ?.let { EventsDiskStore.backing.get(key(it)) }
                ?.let { raw -> runCatching { json.decodeFromString(serializer, raw) }.getOrNull() }
                .orEmpty()
    }

    fun cached(beaconId: String): ClickPass? = _passes.value[beaconId]

    fun put(
        beaconId: String,
        pass: ClickPass,
    ) {
        if (_passes.value[beaconId] == pass) return
        _passes.value = _passes.value + (beaconId to pass)
        persist()
    }

    fun clear(beaconId: String) {
        if (beaconId !in _passes.value) return
        _passes.value = _passes.value - beaconId
        persist()
    }

    /** Sign-out: forget the in-memory copy (the disk copy stays keyed to its user). */
    fun reset() {
        userId = null
        _passes.value = emptyMap()
    }

    private fun persist() {
        val id = userId ?: return
        EventsDiskStore.backing.put(key(id), json.encodeToString(serializer, _passes.value))
    }

    /** Applies a fetch to the cache: a pass is stored, a 403 voids it, a failure keeps it. */
    fun apply(
        beaconId: String,
        fetch: ClickPassFetch,
    ) {
        when (fetch) {
            is ClickPassFetch.Loaded -> put(beaconId, fetch.pass)
            ClickPassFetch.NotGoing -> clear(beaconId)
            is ClickPassFetch.Failed -> Unit
        }
    }
}
