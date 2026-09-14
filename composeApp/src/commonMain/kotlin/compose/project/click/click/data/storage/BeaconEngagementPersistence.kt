package compose.project.click.click.data.storage

import compose.project.click.click.viewmodel.BeaconEngagementCacheEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Disk-backed bookmark + server-confirmed check-in flags keyed by user id. */
object BeaconEngagementPersistence {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Serializable
    private data class PersistedEntry(
        @SerialName("beacon_id") val beaconId: String,
        val bookmarked: Boolean = false,
        @SerialName("checked_in") val checkedIn: Boolean = false,
        @SerialName("checked_in_at") val checkedInAt: String? = null,
        @SerialName("check_in_count") val checkInCount: Int = 0,
        // Retained only for backwards-compatible decoding of snapshots written by older builds.
        // A local early check-in is not authoritative and must never restore checked-in state.
        @SerialName("local_early_check_in") val localEarlyCheckIn: Boolean = false,
        @SerialName("hub_id") val hubId: String? = null,
        @SerialName("updated_at_ms") val updatedAtEpochMs: Long = 0L,
    )

    @Serializable
    private data class PersistedSnapshot(
        @SerialName("user_id") val userId: String,
        val entries: List<PersistedEntry> = emptyList(),
    )

    suspend fun load(
        tokenStorage: TokenStorage,
        userId: String,
    ): Map<String, BeaconEngagementCacheEntry> {
        val raw = tokenStorage.getBeaconEngagementSnapshot() ?: return emptyMap()
        return runCatching {
            val snapshot = json.decodeFromString<PersistedSnapshot>(raw)
            if (snapshot.userId != userId) return emptyMap()
            snapshot.entries.associate { entry ->
                entry.beaconId to
                    BeaconEngagementCacheEntry(
                        bookmarked = entry.bookmarked,
                        checkedIn = entry.checkedIn && !entry.localEarlyCheckIn,
                        checkedInAt = entry.checkedInAt.takeIf { entry.checkedIn && !entry.localEarlyCheckIn },
                        checkInCount = entry.checkInCount,
                        localEarlyCheckIn = false,
                        hubId = entry.hubId,
                    )
            }
        }.getOrDefault(emptyMap())
    }

    suspend fun save(
        tokenStorage: TokenStorage,
        userId: String,
        cache: Map<String, BeaconEngagementCacheEntry>,
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
                        val serverConfirmedCheckedIn = entry.checkedIn && !entry.localEarlyCheckIn
                        PersistedEntry(
                            beaconId = beaconId,
                            bookmarked = entry.bookmarked,
                            checkedIn = serverConfirmedCheckedIn,
                            checkedInAt = entry.checkedInAt.takeIf { serverConfirmedCheckedIn },
                            checkInCount = entry.checkInCount,
                            localEarlyCheckIn = false,
                            hubId = entry.hubId,
                            updatedAtEpochMs = now,
                        )
                    },
            )
        tokenStorage.saveBeaconEngagementSnapshot(
            json.encodeToString(PersistedSnapshot.serializer(), snapshot),
        )
    }

    suspend fun clear(tokenStorage: TokenStorage) {
        tokenStorage.saveBeaconEngagementSnapshot(null)
    }
}
