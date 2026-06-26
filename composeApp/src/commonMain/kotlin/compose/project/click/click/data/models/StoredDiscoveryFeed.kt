package compose.project.click.click.data.models

import compose.project.click.click.ui.utils.CommunityHubPin
import kotlinx.serialization.Serializable

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

fun MapBeacon.toStoredMapBeacon(): StoredMapBeacon = StoredMapBeacon(
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
)

fun StoredMapBeacon.toMapBeacon(): MapBeacon = MapBeacon(
    id = id,
    kind = MapBeaconKind.fromRaw(kind),
    latitude = latitude,
    longitude = longitude,
    metadata = MapBeaconMetadata(
        title = title,
        description = description,
    ),
    createdByUserId = createdByUserId,
    createdAtEpochMs = createdAtEpochMs,
    expiresAtEpochMs = expiresAtEpochMs,
    sourceBeaconType = sourceBeaconType,
    showCreatorName = showCreatorName,
    creatorDisplayName = creatorDisplayName,
    visibilityAudience = BeaconVisibilityAudience.fromRaw(visibilityAudience),
)

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
