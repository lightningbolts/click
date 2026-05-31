package compose.project.click.click.data.storage

import compose.project.click.click.data.api.BeaconAttendeeDto
import compose.project.click.click.viewmodel.BeaconRsvpCacheEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Disk-backed RSVP cache keyed by Supabase user id so cold starts show the user's RSVP
 * before click-web auth finishes restoring, and so a transient GET failure does not wipe UI state.
 */
object BeaconRsvpPersistence {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class PersistedEntry(
        @SerialName("beacon_id") val beaconId: String,
        val attendees: List<BeaconAttendeeDto> = emptyList(),
        @SerialName("current_user_signed_up") val currentUserSignedUp: Boolean = false,
        @SerialName("updated_at_ms") val updatedAtEpochMs: Long = 0L,
    )

    @Serializable
    private data class PersistedSnapshot(
        @SerialName("user_id") val userId: String,
        val entries: List<PersistedEntry> = emptyList(),
    )

    suspend fun load(tokenStorage: TokenStorage, userId: String): Map<String, BeaconRsvpCacheEntry> {
        val raw = tokenStorage.getBeaconRsvpSnapshot() ?: return emptyMap()
        return runCatching {
            val snapshot = json.decodeFromString<PersistedSnapshot>(raw)
            if (snapshot.userId != userId) return emptyMap()
            snapshot.entries.associate { entry ->
                entry.beaconId to BeaconRsvpCacheEntry(
                    attendees = entry.attendees,
                    currentUserSignedUp = entry.currentUserSignedUp,
                )
            }
        }.getOrDefault(emptyMap())
    }

    suspend fun save(
        tokenStorage: TokenStorage,
        userId: String,
        cache: Map<String, BeaconRsvpCacheEntry>,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val snapshot = PersistedSnapshot(
            userId = userId,
            entries = cache.map { (beaconId, entry) ->
                PersistedEntry(
                    beaconId = beaconId,
                    attendees = entry.attendees,
                    currentUserSignedUp = entry.currentUserSignedUp,
                    updatedAtEpochMs = now,
                )
            },
        )
        tokenStorage.saveBeaconRsvpSnapshot(json.encodeToString(PersistedSnapshot.serializer(), snapshot))
    }

    suspend fun clear(tokenStorage: TokenStorage) {
        tokenStorage.saveBeaconRsvpSnapshot(null)
    }
}
