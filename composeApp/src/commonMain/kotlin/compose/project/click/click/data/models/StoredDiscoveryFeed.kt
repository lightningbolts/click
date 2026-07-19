package compose.project.click.click.data.models

import compose.project.click.click.events.eventSchedule
import compose.project.click.click.ui.utils.CommunityHubPin
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
        title = metadata.title,
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
    )
}

fun StoredMapBeacon.toMapBeacon(): MapBeacon {
    val scheduleRaw = storedEventScheduleRaw(
        title = title,
        description = description,
        eventStartAtEpochMs = eventStartAtEpochMs,
        eventEndAtEpochMs = eventEndAtEpochMs,
    )
    return MapBeacon(
        id = id,
        kind = MapBeaconKind.fromRaw(kind),
        latitude = latitude,
        longitude = longitude,
        metadata = MapBeaconMetadata(
            title = title,
            description = description,
            raw = scheduleRaw,
        ),
        createdByUserId = createdByUserId,
        createdAtEpochMs = createdAtEpochMs,
        expiresAtEpochMs = expiresAtEpochMs,
        sourceBeaconType = sourceBeaconType,
        showCreatorName = showCreatorName,
        creatorDisplayName = creatorDisplayName,
        visibilityAudience = BeaconVisibilityAudience.fromRaw(visibilityAudience),
    )
}

private fun storedEventScheduleRaw(
    title: String?,
    description: String?,
    eventStartAtEpochMs: Long?,
    eventEndAtEpochMs: Long?,
): JsonObject? {
    if (title == null && description == null &&
        (eventStartAtEpochMs == null || eventEndAtEpochMs == null)
    ) {
        return null
    }
    return buildJsonObject {
        title?.takeIf { it.isNotBlank() }?.let { put("title", JsonPrimitive(it)) }
        description?.takeIf { it.isNotBlank() }?.let { put("description", JsonPrimitive(it)) }
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
    val mergedRaw = if (schedule != null && eventSchedule() == null) {
        buildJsonObject {
            metadata.raw?.forEach { (k, v) -> put(k, v) }
            put("event_start_at", JsonPrimitive(Instant.fromEpochMilliseconds(schedule.startEpochMs).toString()))
            put("event_end_at", JsonPrimitive(Instant.fromEpochMilliseconds(schedule.endEpochMs).toString()))
        }
    } else {
        metadata.raw
    }

    return copy(
        latitude = rescuedLat,
        longitude = rescuedLng,
        metadata = metadata.copy(
            title = metadata.title ?: existing.metadata.title,
            description = metadata.description ?: existing.metadata.description,
            eventCategories = metadata.eventCategories.ifEmpty { existing.metadata.eventCategories },
            raw = mergedRaw ?: existing.metadata.raw,
        ),
        createdByUserId = createdByUserId ?: existing.createdByUserId,
        createdAtEpochMs = createdAtEpochMs ?: existing.createdAtEpochMs,
        expiresAtEpochMs = expiresAtEpochMs ?: existing.expiresAtEpochMs,
        sourceBeaconType = sourceBeaconType ?: existing.sourceBeaconType,
        showCreatorName = showCreatorName || existing.showCreatorName,
        creatorDisplayName = creatorDisplayName ?: existing.creatorDisplayName,
        visibilityAudience = if (
            visibilityAudience == BeaconVisibilityAudience.EVERYONE &&
            existing.visibilityAudience != BeaconVisibilityAudience.EVERYONE
        ) {
            existing.visibilityAudience
        } else {
            visibilityAudience
        },
    )
}

fun CommunityHubPin.toStoredCommunityHubPin(): StoredCommunityHubPin = StoredCommunityHubPin(
    hubId = hubId,
    name = name,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    activeUserCount = activeUserCount,
    reportedDistanceMeters = reportedDistanceMeters,
)

fun StoredCommunityHubPin.toCommunityHubPin(): CommunityHubPin = CommunityHubPin(
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
    val latitude: Double? = null,
    val longitude: Double? = null,
    val expiresAt: String? = null,
)
