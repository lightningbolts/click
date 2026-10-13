package compose.project.click.click.data.storage

import compose.project.click.click.core.UserDiskCache
import compose.project.click.click.data.api.BeaconAttendeeDto
import compose.project.click.click.viewmodel.BeaconRsvpCacheEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Disk-backed RSVP cache keyed by Supabase user id so cold starts show the user's RSVP
 * before click-web auth finishes restoring, and so a transient GET failure does not wipe UI state.
 *
 * Lives in the per-user [UserDiskCache] (`events.rsvp_saved.rsvp`, sheet 07 G1-5), so it can be read
 * synchronously for the first frame and is wiped at sign-out. Older installs kept one snapshot in
 * token storage; [load] moves it over once.
 */
object BeaconRsvpPersistence {
    /** [UserDiskCache.Keys.RSVP_SAVED] + `.rsvp`. */
    internal const val CACHE_KEY = "${UserDiskCache.Keys.RSVP_SAVED}.rsvp"

    private val legacyJson =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Serializable
    internal data class PersistedEntry(
        @SerialName("beacon_id") val beaconId: String,
        val attendees: List<BeaconAttendeeDto> = emptyList(),
        @SerialName("current_user_signed_up") val currentUserSignedUp: Boolean = false,
        @SerialName("updated_at_ms") val updatedAtEpochMs: Long = 0L,
    )

    @Serializable
    internal data class PersistedSnapshot(
        @SerialName("user_id") val userId: String,
        val entries: List<PersistedEntry> = emptyList(),
    )

    private fun PersistedSnapshot.toCache(): Map<String, BeaconRsvpCacheEntry> =
        entries.associate { entry ->
            entry.beaconId to
                BeaconRsvpCacheEntry(
                    attendees = entry.attendees,
                    currentUserSignedUp = entry.currentUserSignedUp,
                )
        }

    /** The user's cached RSVPs, read synchronously (first-frame paint). */
    fun cached(userId: String): Map<String, BeaconRsvpCacheEntry> =
        UserDiskCache
            .read(userId, CACHE_KEY, PersistedSnapshot.serializer())
            ?.takeIf { it.userId == userId }
            ?.toCache()
            .orEmpty()

    suspend fun load(
        tokenStorage: TokenStorage,
        userId: String,
    ): Map<String, BeaconRsvpCacheEntry> {
        cached(userId).takeIf { it.isNotEmpty() }?.let { return it }
        val raw = runCatching { tokenStorage.getBeaconRsvpSnapshot() }.getOrNull() ?: return emptyMap()
        val legacy = runCatching { legacyJson.decodeFromString<PersistedSnapshot>(raw) }.getOrNull()
        runCatching { tokenStorage.saveBeaconRsvpSnapshot(null) }
        if (legacy == null || legacy.userId != userId) return emptyMap()
        UserDiskCache.write(userId, CACHE_KEY, PersistedSnapshot.serializer(), legacy)
        return legacy.toCache()
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun save(
        tokenStorage: TokenStorage,
        userId: String,
        cache: Map<String, BeaconRsvpCacheEntry>,
    ) {
        val now =
            kotlinx.datetime.Clock.System
                .now()
                .toEpochMilliseconds()
        val snapshot =
            PersistedSnapshot(
                userId = userId,
                entries =
                    cache.map { (beaconId, entry) ->
                        PersistedEntry(
                            beaconId = beaconId,
                            attendees = entry.attendees,
                            currentUserSignedUp = entry.currentUserSignedUp,
                            updatedAtEpochMs = now,
                        )
                    },
            )
        UserDiskCache.write(userId, CACHE_KEY, PersistedSnapshot.serializer(), snapshot)
    }

    suspend fun clear(tokenStorage: TokenStorage) {
        tokenStorage.saveBeaconRsvpSnapshot(null)
    }
}
