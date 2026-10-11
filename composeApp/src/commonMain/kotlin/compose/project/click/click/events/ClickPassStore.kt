package compose.project.click.click.events // pragma: allowlist secret

import compose.project.click.click.core.AppSessionHooks
import compose.project.click.click.core.UserDiskCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Click Passes kept on the device, per user ([UserDiskCache] `events.engagement.passes`), so the pass
 * opens at once and works without signal. A 403 or an RSVP cancel clears it (iOS
 * `EventEngagementRepository`). Sign-out wipes the disk copy with the rest of [UserDiskCache].
 */
object ClickPassStore {
    /** [UserDiskCache.Keys.EVENTS_ENGAGEMENT] + `.passes`. */
    internal const val CACHE_KEY = "${UserDiskCache.Keys.EVENTS_ENGAGEMENT}.passes"
    private val serializer = MapSerializer(String.serializer(), ClickPass.serializer())
    private var userId: String? = null
    private val _passes = MutableStateFlow<Map<String, ClickPass>>(emptyMap())
    val passes: StateFlow<Map<String, ClickPass>> = _passes.asStateFlow()

    init {
        AppSessionHooks.registerSessionWipe { reset() }
    }

    /** Loads the signed-in user's passes; a different user never sees another's. */
    fun bindUser(user: String?) {
        val id = user?.trim()?.takeIf { it.isNotEmpty() }
        if (id == userId) return
        userId = id
        _passes.value =
            id
                ?.let { UserDiskCache.read(it, CACHE_KEY, serializer) }
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

    /** Sign-out: forget the in-memory copy ([UserDiskCache.wipeAll] removes the disk copy). */
    fun reset() {
        userId = null
        _passes.value = emptyMap()
    }

    private fun persist() {
        val id = userId ?: return
        UserDiskCache.write(id, CACHE_KEY, serializer, _passes.value)
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
