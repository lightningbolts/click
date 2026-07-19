package compose.project.click.click.data.storage

import compose.project.click.click.viewmodel.BeaconEngagementCacheEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Disk-backed bookmark + check-in flags keyed by user id. */
object BeaconEngagementPersistence {
    private val json = Json {
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
        @SerialName("local_early_check_in") val localEarlyCheckIn: Boolean = false,
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
                entry.beaconId to BeaconEngagementCacheEntry(
                    bookmarked = entry.bookmarked,
                    checkedIn = entry.checkedIn || entry.localEarlyCheckIn,
                    checkedInAt = entry.checkedInAt,
                    checkInCount = entry.checkInCount,
                    localEarlyCheckIn = entry.localEarlyCheckIn,
                )
            }
        }.getOrDefault(emptyMap())
    }

    suspend fun save(
        tokenStorage: TokenStorage,
        userId: String,
        cache: Map<String, BeaconEngagementCacheEntry>,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val snapshot = PersistedSnapshot(
            userId = userId,
            entries = cache.map { (beaconId, entry) ->
                PersistedEntry(
                    beaconId = beaconId,
                    bookmarked = entry.bookmarked,
                    checkedIn = entry.checkedIn,
                    checkedInAt = entry.checkedInAt,
                    checkInCount = entry.checkInCount,
                    localEarlyCheckIn = entry.localEarlyCheckIn,
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
