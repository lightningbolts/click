package compose.project.click.click.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Raw file access for [UserDiskCache]. Android: `noBackupFilesDir/<uid>/cache/<key>.json`
 * (never in cloud backups), written atomically.
 */
interface DiskCacheBackend {
    fun read(
        userId: String,
        key: String,
    ): String?

    fun write(
        userId: String,
        key: String,
        text: String,
    )

    fun delete(
        userId: String,
        key: String,
    )

    /** Every user's cache directory (sign-out and a rejected session). */
    fun wipeAll()
}

class InMemoryDiskCacheBackend : DiskCacheBackend {
    val files = mutableMapOf<Pair<String, String>, String>()

    override fun read(
        userId: String,
        key: String,
    ): String? = files[userId to key]

    override fun write(
        userId: String,
        key: String,
        text: String,
    ) {
        files[userId to key] = text
    }

    override fun delete(
        userId: String,
        key: String,
    ) {
        files.remove(userId to key)
    }

    override fun wipeAll() = files.clear()
}

expect fun platformDiskCacheBackend(): DiskCacheBackend

/**
 * Per-user disk-first JSON cache (iOS `CacheStore`): ViewModels seed their `StateFlow` from it
 * synchronously, then revalidate from the network. Calls are blocking file IO, so call from IO
 * (or accept a tiny read on first frame, which is the point). Wiped at sign-out.
 */
object UserDiskCache {
    /** Cache keys. One file each; a key holds one JSON document. */
    object Keys {
        const val HOME_FEED = "home.feed"

        /** The shared drops strip (≤240 items, sheet 07 G1-5). */
        const val SHARED_DROPS_STRIP = "drops.shared.strip"
        const val BEACON_EXTRAS = "beacons.extras"
        const val RSVP_SAVED = "events.rsvp_saved"

        /** Passes, RSVPs and saved events (G3-15 reads passes offline). */
        const val EVENTS_ENGAGEMENT = "events.engagement"
        const val ACTIVITY = "activity"

        /** The last map region (G3-20). */
        const val MAP_REGION = "map.last_region"

        /** The weather cache (G3-14). */
        const val WEATHER = "weather"
        // Flags persist in encrypted prefs ([FeatureFlags]), not here.
    }

    private val safeName = Regex("^[A-Za-z0-9._-]{1,128}$")

    internal var backendFactory: () -> DiskCacheBackend = ::platformDiskCacheBackend
    private val backend: DiskCacheBackend? get() = runCatching { backendFactory() }.getOrNull()

    internal val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

    private fun valid(
        userId: String?,
        key: String,
    ): Boolean = userId != null && safeName.matches(userId) && safeName.matches(key) && userId != "." && userId != ".."

    fun readRaw(
        userId: String?,
        key: String,
    ): String? {
        if (!valid(userId, key)) return null
        return runCatching { backend?.read(userId!!, key) }.getOrNull()
    }

    fun writeRaw(
        userId: String?,
        key: String,
        text: String,
    ) {
        if (!valid(userId, key)) return
        runCatching { backend?.write(userId!!, key, text) }
    }

    /** A cached value, or null when absent. A file that no longer decodes is dropped. */
    fun <T> read(
        userId: String?,
        key: String,
        serializer: KSerializer<T>,
    ): T? {
        val raw = readRaw(userId, key) ?: return null
        return runCatching { json.decodeFromString(serializer, raw) }.getOrElse {
            remove(userId, key)
            null
        }
    }

    fun <T> write(
        userId: String?,
        key: String,
        serializer: KSerializer<T>,
        value: T,
    ) {
        val text = runCatching { json.encodeToString(serializer, value) }.getOrNull() ?: return
        writeRaw(userId, key, text)
    }

    fun remove(
        userId: String?,
        key: String,
    ) {
        if (!valid(userId, key)) return
        runCatching { backend?.delete(userId!!, key) }
    }

    /** Sign-out wipe: every user's cache. */
    fun wipeAll() {
        runCatching { backend?.wipeAll() }
    }
}
