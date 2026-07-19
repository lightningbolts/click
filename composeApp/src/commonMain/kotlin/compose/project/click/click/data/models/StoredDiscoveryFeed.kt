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
 * When [incoming] lost schedule fields (e.g. disk projection), keep [existing]'s event window.
 */
internal fun MapBeacon.withPreservedEventScheduleFrom(existing: MapBeacon?): MapBeacon {
    if (existing == null || id != existing.id) return this
    if (eventSchedule() != null) return this
    val donor = existing.eventSchedule() ?: return this
    val baseRaw = metadata.raw
    val mergedRaw = buildJsonObject {
        baseRaw?.forEach { (k, v) -> put(k, v) }
        put("event_start_at", JsonPrimitive(Instant.fromEpochMilliseconds(donor.startEpochMs).toString()))
        put("event_end_at", JsonPrimitive(Instant.fromEpochMilliseconds(donor.endEpochMs).toString()))
    }
    return copy(
        metadata = metadata.copy(
            title = metadata.title ?: existing.metadata.title,
            description = metadata.description ?: existing.metadata.description,
            raw = mergedRaw,
        ),
        createdAtEpochMs = createdAtEpochMs ?: existing.createdAtEpochMs,
        expiresAtEpochMs = expiresAtEpochMs ?: existing.expiresAtEpochMs,
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
