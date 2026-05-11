package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.ui.utils.CommunityHubPin // pragma: allowlist secret
import compose.project.click.click.ui.utils.ConnectionMapPoint // pragma: allowlist secret
import compose.project.click.click.ui.utils.MapCluster // pragma: allowlist secret
import compose.project.click.click.ui.utils.TimeState // pragma: allowlist secret
import compose.project.click.click.ui.utils.beaconZIndex // pragma: allowlist secret

/** Short on-map caption (beacon context or truncated connection name). */
internal fun truncateMapPinCaption(text: String, maxChars: Int): String {
    val t = text.trim()
    if (t.isEmpty()) return ""
    if (t.length <= maxChars) return t
    return t.take(maxChars - 1) + "…"
}

/**
 * Represents a point to plot on the map
 * Enhanced with visual decay metadata
 */
enum class MapPinKind {
    CONNECTION,
    BEACON_SOUNDTRACK,
    BEACON_ALERT,
    BEACON_SOCIAL,
    BEACON_OTHER,
    COMMUNITY_HUB,
}

data class MapPin(
    val id: String,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val isNearby: Boolean = false,
    val timeState: TimeState = TimeState.RECENT,
    val opacity: Float = 1.0f,
    val shouldPulse: Boolean = false,
    val imageUrl: String? = null,
    val kind: MapPinKind = MapPinKind.CONNECTION,
    /** Set for beacon pins — drives per-type marker color when [beaconTypeKey] is null. */
    val beaconKind: MapBeaconKind? = null,
    /** Raw API `beacon_type` string for palette + labels (preferred when set). */
    val beaconTypeKey: String? = null,
    /** Native marker draw order (Google Maps / MapKit). */
    val zIndex: Float = 0f,
    /**
     * Optional caption drawn below the marker (truncated soundtrack / description / peer name).
     */
    val caption: String? = null,
) {
    companion object {
        /**
         * Create from ConnectionMapPoint
         */
        fun fromConnectionPoint(point: ConnectionMapPoint, imageUrl: String? = null): MapPin {
            val cap = truncateMapPinCaption(point.displayName, 12).takeIf { it.isNotEmpty() }
            return MapPin(
                id = point.connection.id,
                title = point.displayName,
                latitude = point.latitude,
                longitude = point.longitude,
                isNearby = point.timeState == TimeState.LIVE,
                timeState = point.timeState,
                opacity = point.opacity,
                shouldPulse = point.shouldPulse,
                imageUrl = imageUrl,
                kind = MapPinKind.CONNECTION,
                beaconKind = null,
                beaconTypeKey = null,
                zIndex = 0f,
                caption = cap,
            )
        }

        fun fromBeacon(beacon: MapBeacon): MapPin {
            val kind = when (beacon.kind) {
                MapBeaconKind.SOUNDTRACK -> MapPinKind.BEACON_SOUNDTRACK
                MapBeaconKind.HAZARD, MapBeaconKind.SOS, MapBeaconKind.UTILITY, MapBeaconKind.STUDY ->
                    MapPinKind.BEACON_ALERT
                MapBeaconKind.SOCIAL_VIBE -> MapPinKind.BEACON_SOCIAL
                MapBeaconKind.OTHER -> MapPinKind.BEACON_OTHER
            }
            val label = beacon.metadata.title
                ?: beacon.metadata.trackName
                ?: beacon.metadata.description?.take(24)
                ?: when (beacon.kind) {
                    MapBeaconKind.SOUNDTRACK -> "Soundtrack"
                    MapBeaconKind.SOS -> "SOS"
                    MapBeaconKind.HAZARD -> "Hazard"
                    MapBeaconKind.UTILITY -> "Utility"
                    MapBeaconKind.STUDY -> "Study"
                    MapBeaconKind.SOCIAL_VIBE -> "Social"
                    MapBeaconKind.OTHER -> "Beacon"
                }
            val caption = when (beacon.kind) {
                MapBeaconKind.SOUNDTRACK -> {
                    val raw = beacon.metadata.trackName
                        ?: beacon.metadata.title
                        ?: beacon.metadata.musicUrl
                        ?: label
                    truncateMapPinCaption(raw, 12).takeIf { it.isNotEmpty() }
                }
                MapBeaconKind.HAZARD, MapBeaconKind.UTILITY, MapBeaconKind.SOS, MapBeaconKind.STUDY -> {
                    beacon.metadata.description?.let { truncateMapPinCaption(it, 12) }
                        ?.takeIf { it.isNotEmpty() }
                }
                MapBeaconKind.SOCIAL_VIBE, MapBeaconKind.OTHER -> {
                    beacon.metadata.description?.let { truncateMapPinCaption(it, 12) }
                        ?.takeIf { it.isNotEmpty() }
                }
            }
            return MapPin(
                id = "beacon:${beacon.id}",
                title = label,
                latitude = beacon.latitude,
                longitude = beacon.longitude,
                isNearby = false,
                timeState = TimeState.RECENT,
                opacity = 1f,
                shouldPulse = beacon.kind == MapBeaconKind.SOS || beacon.kind == MapBeaconKind.HAZARD,
                imageUrl = null,
                kind = kind,
                beaconKind = beacon.kind,
                beaconTypeKey = beacon.sourceBeaconType,
                zIndex = beaconZIndex(beacon),
                caption = caption,
            )
        }

        fun fromCommunityHub(hub: CommunityHubPin): MapPin {
            val cap = truncateMapPinCaption(hub.name, 14).takeIf { it.isNotEmpty() }
            return MapPin(
                id = "hub:${hub.hubId}",
                title = hub.name,
                latitude = hub.latitude,
                longitude = hub.longitude,
                isNearby = false,
                timeState = TimeState.LIVE,
                opacity = 1f,
                shouldPulse = true,
                imageUrl = null,
                kind = MapPinKind.COMMUNITY_HUB,
                beaconKind = null,
                beaconTypeKey = null,
                zIndex = 11_600f,
                caption = cap ?: "${hub.activeUserCount} here",
            )
        }
    }
}

/**
 * Represents a cluster hub to plot on the map
 */
data class MapClusterPin(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val count: Int,
    val hasLiveConnections: Boolean = false,
    /**
     * True when this cluster contains only [ConnectionMapPoint]s (no beacons). These hubs use the
     * same accent as individual connection markers (magenta) instead of the generic "no live" orange.
     */
    val isConnectionOnly: Boolean = false,
    val zIndex: Float = 0f,
)

/**
 * Sealed class for map markers (either individual pins or clusters)
 */
sealed class MapMarker {
    data class Pin(val pin: MapPin) : MapMarker()
    data class Cluster(val cluster: MapClusterPin) : MapMarker()
}

/**
 * Platform-specific map implementation
 * 
 * Supports two rendering modes:
 * - Individual pins (when zoomed in)
 * - Cluster hubs (when zoomed out)
 */
@Composable
expect fun PlatformMap(
    modifier: Modifier,
    pins: List<MapPin>,
    clusters: List<MapClusterPin> = emptyList(),
    zoom: Double,
    centerLat: Double? = null,
    centerLon: Double? = null,
    ghostMode: Boolean = false,
    onPinTapped: (MapPin) -> Unit = {},
    onClusterTapped: (MapClusterPin) -> Unit = {},
    onZoomChanged: (Double) -> Unit = {},
    onVisibleBoundsChanged: (minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) -> Unit = { _, _, _, _ -> },
    onCameraAnimationComplete: () -> Unit = {}
)

/**
 * Create a cluster pin from a MapCluster
 */
fun MapCluster.toClusterPin(): MapClusterPin {
    val hasLive = points.any { it.timeState == TimeState.LIVE }
    val hazardTop = beaconPoints.any { it.kind == MapBeaconKind.HAZARD || it.kind == MapBeaconKind.SOS }
    val connectionOnly = beaconPoints.isEmpty() && points.isNotEmpty()
    return MapClusterPin(
        id = id,
        latitude = centerLat,
        longitude = centerLon,
        count = count,
        hasLiveConnections = hasLive,
        isConnectionOnly = connectionOnly,
        zIndex = if (hazardTop) 80f else 20f,
    )
}

/**
 * Google Maps default-marker hue (0–360°) — keep in sync with iOS tint in MapView.ios.kt.
 */
fun MapPin.markerHueDegrees(): Float = when {
    beaconTypeKey != null -> hueForRawBeaconType(beaconTypeKey!!)
    // Unified connection accent (magenta) — matches cluster hubs for connection-only groups.
    kind == MapPinKind.CONNECTION -> 300f
    beaconKind != null -> hueForMapBeaconKind(beaconKind!!)
    else -> when (kind) {
        MapPinKind.BEACON_SOUNDTRACK -> 275f
        MapPinKind.BEACON_ALERT -> 0f
        MapPinKind.BEACON_SOCIAL -> 310f
        MapPinKind.BEACON_OTHER -> 55f
        MapPinKind.COMMUNITY_HUB -> 195f
        MapPinKind.CONNECTION -> 300f
    }
}

private fun hueForRawBeaconType(raw: String): Float =
    when (raw.lowercase()) {
        "soundtrack" -> 275f
        "sos" -> 0f
        "study" -> 240f
        "hazard" -> 35f
        "utility" -> 210f
        "hazard_utility" -> 28f
        "transit" -> 195f
        "recreation" -> 118f
        "hobby" -> 145f
        "swag" -> 300f
        "capacity" -> 328f
        "scavenger" -> 62f
        else -> 55f
    }

private fun hueForMapBeaconKind(kind: MapBeaconKind): Float =
    when (kind) {
        MapBeaconKind.SOUNDTRACK -> 275f
        MapBeaconKind.SOS -> 0f
        MapBeaconKind.HAZARD -> 35f
        MapBeaconKind.UTILITY -> 210f
        MapBeaconKind.STUDY -> 240f
        MapBeaconKind.SOCIAL_VIBE -> 310f
        MapBeaconKind.OTHER -> 55f
    }
