package compose.project.click.click.data.models // pragma: allowlist secret

import compose.project.click.click.events.eventSchedule // pragma: allowlist secret
import compose.project.click.click.ui.utils.CommunityHubPin // pragma: allowlist secret
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Disk-safe projection of [MapBeacon] for [CachedAppSnapshot]. */
@Serializable
data class StoredMapBeacon(
    val id: String,
    val kind: String,
    val latitude: Double,
    val longitude: Double,
    val title: String? = null,
    val description: String? = null,
    val createdByUserId: String? = null,
    val createdAtEpochMs: Long? = null,
    val expiresAtEpochMs: Long? = null,
    val sourceBeaconType: String? = null,
    val showCreatorName: Boolean = false,
    val creatorDisplayName: String? = null,
    val visibilityAudience: String? = null,
    /** Explicit event window — must survive cache round-trip (not derived from created_at). */
    val eventStartAtEpochMs: Long? = null,
    val eventEndAtEpochMs: Long? = null,
    /** Soundtrack fields — previously dropped on disk, which hid preview playback after cold start. */
    val trackName: String? = null,
    val artistName: String? = null,
    val previewUrl: String? = null,
    val albumArtUrl: String? = null,
    val musicUrl: String? = null,
    val originalUrl: String? = null,
    /** Event venue labels — previously dropped on disk, delaying address text after cold start. */
    val locationName: String? = null,
    val formattedAddress: String? = null,
    val hubId: String? = null,
)

@Serializable
data class StoredCommunityHubPin(
    val hubId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val activeUserCount: Int,
    val reportedDistanceMeters: Double? = null,
)

fun MapBeacon.toStoredMapBeacon(): StoredMapBeacon {
    val schedule = eventSchedule()
    return StoredMapBeacon(
        id = id,
        kind = kind.apiValue,
        latitude = latitude,
        longitude = longitude,
        title = metadata.title ?: metadata.trackName,
        description = metadata.description,
        createdByUserId = createdByUserId,
        createdAtEpochMs = createdAtEpochMs,
        expiresAtEpochMs = expiresAtEpochMs,
        sourceBeaconType = sourceBeaconType,
        showCreatorName = showCreatorName,
        creatorDisplayName = creatorDisplayName,
        visibilityAudience = visibilityAudience.apiValue,
        eventStartAtEpochMs = schedule?.startEpochMs,
        eventEndAtEpochMs = schedule?.endEpochMs,
        trackName = metadata.trackName,
        artistName = metadata.artistName ?: metadata.artist,
        previewUrl = metadata.previewUrl,
        albumArtUrl = metadata.albumArtUrl,
        musicUrl = metadata.musicUrl,
        originalUrl = metadata.originalUrl,
        locationName = metadata.locationName,
        formattedAddress = metadata.formattedAddress,
        hubId = hubId,
    )
}

fun StoredMapBeacon.toMapBeacon(): MapBeacon {
    val scheduleRaw =
        storedEventScheduleRaw(
            title = title,
            description = description,
            eventStartAtEpochMs = eventStartAtEpochMs,
            eventEndAtEpochMs = eventEndAtEpochMs,
            trackName = trackName,
            artistName = artistName,
            previewUrl = previewUrl,
            albumArtUrl = albumArtUrl,
            musicUrl = musicUrl,
            originalUrl = originalUrl,
            locationName = locationName,
            formattedAddress = formattedAddress,
        )
    return MapBeacon(
        id = id,
        kind = MapBeaconKind.fromRaw(kind),
        latitude = latitude,
        longitude = longitude,
        metadata =
            MapBeaconMetadata(
                title = title,
                description = description,
                trackName = trackName,
                artistName = artistName,
                artist = artistName,
                previewUrl = previewUrl,
                albumArtUrl = albumArtUrl,
                musicUrl = musicUrl,
                originalUrl = originalUrl,
                locationName = locationName,
                formattedAddress = formattedAddress,
                raw = scheduleRaw,
            ),
        createdByUserId = createdByUserId,
        createdAtEpochMs = createdAtEpochMs,
        expiresAtEpochMs = expiresAtEpochMs,
        sourceBeaconType = sourceBeaconType,
        showCreatorName = showCreatorName,
        creatorDisplayName = creatorDisplayName,
        visibilityAudience = BeaconVisibilityAudience.fromRaw(visibilityAudience),
        hubId = hubId,
    )
}

private fun storedEventScheduleRaw(
    title: String?,
    description: String?,
    eventStartAtEpochMs: Long?,
    eventEndAtEpochMs: Long?,
    trackName: String? = null,
    artistName: String? = null,
    previewUrl: String? = null,
    albumArtUrl: String? = null,
    musicUrl: String? = null,
    originalUrl: String? = null,
    locationName: String? = null,
    formattedAddress: String? = null,
): JsonObject? {
    val hasSoundtrack =
        listOf(trackName, artistName, previewUrl, albumArtUrl, musicUrl, originalUrl)
            .any { !it.isNullOrBlank() }
    val hasVenue = !locationName.isNullOrBlank() || !formattedAddress.isNullOrBlank()
    if (title == null && description == null && !hasSoundtrack && !hasVenue &&
        (eventStartAtEpochMs == null || eventEndAtEpochMs == null)
    ) {
        return null
    }
    return buildJsonObject {
        title?.takeIf { it.isNotBlank() }?.let { put("title", JsonPrimitive(it)) }
        description?.takeIf { it.isNotBlank() }?.let { put("description", JsonPrimitive(it)) }
        trackName?.takeIf { it.isNotBlank() }?.let { put("track_name", JsonPrimitive(it)) }
        artistName?.takeIf { it.isNotBlank() }?.let {
            put("artist_name", JsonPrimitive(it))
            put("artist", JsonPrimitive(it))
        }
        previewUrl?.takeIf { it.isNotBlank() }?.let { put("preview_url", JsonPrimitive(it)) }
        albumArtUrl?.takeIf { it.isNotBlank() }?.let { put("album_art_url", JsonPrimitive(it)) }
        musicUrl?.takeIf { it.isNotBlank() }?.let { put("music_url", JsonPrimitive(it)) }
        originalUrl?.takeIf { it.isNotBlank() }?.let { put("original_url", JsonPrimitive(it)) }
        locationName?.takeIf { it.isNotBlank() }?.let { put("location_name", JsonPrimitive(it)) }
        formattedAddress?.takeIf { it.isNotBlank() }?.let { put("formatted_address", JsonPrimitive(it)) }
        if (eventStartAtEpochMs != null && eventEndAtEpochMs != null &&
            eventEndAtEpochMs > eventStartAtEpochMs
        ) {
            put(
                "event_start_at",
                JsonPrimitive(Instant.fromEpochMilliseconds(eventStartAtEpochMs).toString()),
            )
            put(
                "event_end_at",
                JsonPrimitive(Instant.fromEpochMilliseconds(eventEndAtEpochMs).toString()),
            )
        }
    }
}

/**
 * When merging beacon rows, keep schedule / coords / host / posted metadata that [incoming]
 * dropped (bookmark seeds, disk projections, partial GET patches).
 */
internal fun MapBeacon.withPreservedEventScheduleFrom(existing: MapBeacon?): MapBeacon {
    if (existing == null || id != existing.id) return this
    val usableExistingCoords =
        existing.latitude.isFinite() &&
            existing.longitude.isFinite() &&
            !(existing.latitude == 0.0 && existing.longitude == 0.0)
    val needsCoordRescue =
        usableExistingCoords &&
            (
                !latitude.isFinite() ||
                    !longitude.isFinite() ||
                    (latitude == 0.0 && longitude == 0.0)
            )
    val rescuedLat = if (needsCoordRescue) existing.latitude else latitude
    val rescuedLng = if (needsCoordRescue) existing.longitude else longitude

    val schedule = eventSchedule() ?: existing.eventSchedule()
    val mergedRaw =
        if (schedule != null && eventSchedule() == null) {
            buildJsonObject {
                metadata.raw?.forEach { (k, v) -> put(k, v) }
                put("event_start_at", JsonPrimitive(Instant.fromEpochMilliseconds(schedule.startEpochMs).toString()))
                put("event_end_at", JsonPrimitive(Instant.fromEpochMilliseconds(schedule.endEpochMs).toString()))
            }
        } else {
            // Prefer richer soundtrack metadata when either side has preview/art/track fields.
            mergeSoundtrackRaw(metadata.raw, existing.metadata.raw)
        }

    return copy(
        latitude = rescuedLat,
        longitude = rescuedLng,
        metadata =
            metadata.copy(
                title = metadata.title ?: existing.metadata.title,
                description = metadata.description ?: existing.metadata.description,
                trackName = metadata.trackName ?: existing.metadata.trackName,
                artistName = metadata.artistName ?: existing.metadata.artistName,
                artist = metadata.artist ?: existing.metadata.artist,
                previewUrl = metadata.previewUrl ?: existing.metadata.previewUrl,
                albumArtUrl = metadata.albumArtUrl ?: existing.metadata.albumArtUrl,
                musicUrl = metadata.musicUrl ?: existing.metadata.musicUrl,
                originalUrl = metadata.originalUrl ?: existing.metadata.originalUrl,
                locationName = metadata.locationName ?: existing.metadata.locationName,
                formattedAddress = metadata.formattedAddress ?: existing.metadata.formattedAddress,
                eventCategories = metadata.eventCategories.ifEmpty { existing.metadata.eventCategories },
                raw = mergedRaw ?: existing.metadata.raw,
            ),
        createdByUserId = createdByUserId ?: existing.createdByUserId,
        createdAtEpochMs = createdAtEpochMs ?: existing.createdAtEpochMs,
        expiresAtEpochMs = expiresAtEpochMs ?: existing.expiresAtEpochMs,
        sourceBeaconType = sourceBeaconType ?: existing.sourceBeaconType,
        showCreatorName = showCreatorName || existing.showCreatorName,
        creatorDisplayName = creatorDisplayName ?: existing.creatorDisplayName,
        visibilityAudience =
            if (
                visibilityAudience == BeaconVisibilityAudience.EVERYONE &&
                existing.visibilityAudience != BeaconVisibilityAudience.EVERYONE
            ) {
                existing.visibilityAudience
            } else {
                visibilityAudience
            },
        hubId = hubId ?: existing.hubId,
    )
}

private fun mergeSoundtrackRaw(
    primary: JsonObject?,
    fallback: JsonObject?,
): JsonObject? {
    if (primary == null) return fallback
    if (fallback == null) return primary
    val keys =
        listOf(
            "preview_url",
            "album_art_url",
            "track_name",
            "artist_name",
            "artist",
            "music_url",
            "original_url",
            "title",
            "location_name",
            "formatted_address",
        )
    return buildJsonObject {
        primary.forEach { (k, v) -> put(k, v) }
        for (k in keys) {
            val have = primary[k]
            val donor = fallback[k]
            if ((have == null || (have is JsonPrimitive && have.content.isBlank())) && donor != null) {
                put(k, donor)
            }
        }
    }
}

fun CommunityHubPin.toStoredCommunityHubPin(): StoredCommunityHubPin =
    StoredCommunityHubPin(
        hubId = hubId,
        name = name,
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        activeUserCount = activeUserCount,
        reportedDistanceMeters = reportedDistanceMeters,
    )

fun StoredCommunityHubPin.toCommunityHubPin(): CommunityHubPin =
    CommunityHubPin(
        hubId = hubId,
        name = name,
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        activeUserCount = activeUserCount,
        reportedDistanceMeters = reportedDistanceMeters,
    )

/** Disk-safe Home saved-event row (mirrors [compose.project.click.click.data.api.EventBookmarkItemDto]). */
@Serializable
data class StoredEventBookmark(
    val beaconId: String,
    val bookmarkedAt: String? = null,
    val title: String? = null,
    val eventStartAt: String? = null,
    val eventEndAt: String? = null,
    val locationName: String? = null,
    val formattedAddress: String? = null,
    val eventCategories: List<String> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val expiresAt: String? = null,
    val creatorId: String? = null,
    val creatorName: String? = null,
    val createdAt: String? = null,
    val showCreatorName: Boolean = false,
)
