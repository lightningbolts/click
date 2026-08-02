package compose.project.click.click.data.repository // pragma: allowlist secret

import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.BeaconAttendeeDto
import compose.project.click.click.data.api.BeaconAttendeeDirectoryResponseDto
import compose.project.click.click.data.api.BeaconRsvpGetResponseDto
import compose.project.click.click.data.api.ConnectionEventRecommendationResponseDto
import compose.project.click.click.data.api.MapBeaconPatchBody
import compose.project.click.click.data.api.CommunityHubNearbyDto // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconInsert // pragma: allowlist secret
import compose.project.click.click.data.models.parseMapBeaconRows // pragma: allowlist secret
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Proximity map beacons via click-web `/api/beacons` (JWT); no direct `map_beacons` access.
 */
class MapBeaconRepository(
    private val apiClient: ApiClient = ApiClient(),
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fetchLocalBeacons(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        beaconTypeFilters: String? = null,
    ): Result<List<MapBeacon>> {
        val (lat, lon, radius) = bboxToCenterRadiusMeters(minLat, maxLat, minLon, maxLon)
        return apiClient.getMapBeacons(
            lat = lat,
            lon = lon,
            radiusMeters = radius,
            filters = beaconTypeFilters,
        ).fold(
            onSuccess = { text -> Result.success(parseBeaconResponsePayload(text)) },
            onFailure = { Result.failure(it) },
        )
    }

    /** Full beacon by id — used to hydrate event schedule when proximity list omitted it. */
    suspend fun fetchBeacon(beaconId: String): Result<MapBeacon> =
        apiClient.getMapBeacon(beaconId)

    suspend fun insertBeacon(insert: MapBeaconInsert): Result<MapBeacon> =
        apiClient.postMapBeacon(insert)

    suspend fun updateBeacon(beaconId: String, patch: MapBeaconPatchBody): Result<MapBeacon> =
        apiClient.patchMapBeacon(beaconId, patch)

    suspend fun deleteBeacon(beaconId: String): Result<Unit> =
        apiClient.deleteMapBeacon(beaconId)

    suspend fun fetchBeaconRsvp(beaconId: String): Result<BeaconRsvpGetResponseDto> =
        apiClient.getBeaconRsvp(beaconId)

    suspend fun fetchBeaconAttendeeDirectory(beaconId: String): Result<BeaconAttendeeDirectoryResponseDto> =
        apiClient.getBeaconAttendeeDirectory(beaconId)

    suspend fun fetchConnectionEventRecommendation(
        connectionId: String,
        latitude: Double? = null,
        longitude: Double? = null,
    ): Result<ConnectionEventRecommendationResponseDto> =
        apiClient.getConnectionEventRecommendation(connectionId, latitude, longitude)

    suspend fun rsvpBeacon(
        beaconId: String,
        latitude: Double? = null,
        longitude: Double? = null,
        accuracyMeters: Double? = null,
        platform: String? = null,
    ): Result<BeaconAttendeeDto> =
        apiClient.postBeaconRsvp(beaconId, latitude, longitude, accuracyMeters, platform)

    suspend fun cancelRsvp(beaconId: String): Result<Unit> =
        apiClient.deleteBeaconRsvp(beaconId)

    suspend fun fetchBeaconEngagement(beaconId: String) =
        apiClient.getBeaconEngagement(beaconId)

    suspend fun setBeaconBookmark(
        beaconId: String,
        bookmarked: Boolean,
        telemetry: compose.project.click.click.data.api.EngagementTelemetryBody,
    ) = apiClient.putBeaconBookmark(beaconId, bookmarked, telemetry)

    suspend fun checkInBeacon(
        beaconId: String,
        telemetry: compose.project.click.click.data.api.EngagementTelemetryBody,
    ) = apiClient.postBeaconCheckIn(beaconId, telemetry)

    suspend fun checkOutBeacon(beaconId: String) = apiClient.deleteBeaconCheckIn(beaconId)

    suspend fun recordBeaconImpression(
        beaconId: String,
        telemetry: compose.project.click.click.data.api.EngagementTelemetryBody,
    ) = apiClient.postBeaconImpression(beaconId, telemetry)

    suspend fun recordBeaconShare(
        beaconId: String,
        telemetry: compose.project.click.click.data.api.EngagementTelemetryBody,
        shareUrl: String? = null,
    ) = apiClient.postBeaconShare(beaconId, telemetry, shareUrl)

    suspend fun fetchMyEventBookmarks(limit: Int = 50, cursor: String? = null) =
        apiClient.getMyEventBookmarks(limit = limit, cursor = cursor)

    suspend fun fetchNearbyCommunityHubs(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): Result<List<CommunityHubNearbyDto>> {
        val (lat, lon, _) = bboxToCenterRadiusMeters(minLat, maxLat, minLon, maxLon)
        val diag = haversineMeters(minLat, minLon, maxLat, maxLon)
        val radius = (diag / 2.0 * 1.35).coerceIn(500.0, 50_000.0)
        return apiClient.getNearbyCommunityHubs(lat, lon, radius)
    }

    private fun parseBeaconResponsePayload(text: String): List<MapBeacon> {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed == "null") return emptyList()
        return runCatching {
            val root = json.parseToJsonElement(trimmed)
            when (root) {
                is JsonObject -> {
                    val rows = root["beacons"] ?: root["data"] ?: root["rows"] ?: root["items"]
                    when (rows) {
                        null -> {
                            if (root.keys.any { it.equals("id", true) || it == "kind" || it == "beacon_type" }) {
                                parseMapBeaconRows(root)
                            } else {
                                emptyList()
                            }
                        }
                        else -> parseMapBeaconRows(rows)
                    }
                }
                else -> parseMapBeaconRows(root)
            }
        }.getOrElse {
            runCatching { parseMapBeaconRows(json.parseToJsonElement(trimmed)) }.getOrDefault(emptyList())
        }
    }

    private fun bboxToCenterRadiusMeters(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): Triple<Double, Double, Double> {
        val cLat = (minLat + maxLat) / 2.0
        val cLon = (minLon + maxLon) / 2.0
        val diag = haversineMeters(minLat, minLon, maxLat, maxLon)
        val radius = (diag / 2.0 * 1.2).coerceIn(250.0, 50_000.0)
        return Triple(cLat, cLon, radius)
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val lat1Rad = lat1 * PI / 180.0
        val lat2Rad = lat2 * PI / 180.0
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1Rad) * cos(lat2Rad) * (sin(dLon / 2) * sin(dLon / 2))
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    fun close() {
        apiClient.close()
    }
}
