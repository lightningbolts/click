package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.ui.utils.ConnectionMapPoint // pragma: allowlist secret
import compose.project.click.click.ui.utils.MapCluster // pragma: allowlist secret
import compose.project.click.click.ui.utils.TimeState // pragma: allowlist secret
import compose.project.click.click.ui.utils.beaconZIndex // pragma: allowlist secret

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
    /** Native marker draw order (Google Maps / MapKit). */
    val zIndex: Float = 0f,
) {
    companion object {
        /**
         * Create from ConnectionMapPoint
         */
        fun fromConnectionPoint(point: ConnectionMapPoint, imageUrl: String? = null): MapPin {
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
                zIndex = 0f,
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
                ?: beacon.metadata.description?.take(24)
                ?: when (beacon.kind) {
                    MapBeaconKind.SOUNDTRACK -> "Soundtrack"
                    MapBeaconKind.SOS -> "SOS"
                    MapBeaconKind.HAZARD -> "Alert"
                    MapBeaconKind.UTILITY -> "Utility"
                    MapBeaconKind.STUDY -> "Study"
                    MapBeaconKind.SOCIAL_VIBE -> "Social"
                    MapBeaconKind.OTHER -> "Beacon"
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
                zIndex = beaconZIndex(beacon),
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
    return MapClusterPin(
        id = id,
        latitude = centerLat,
        longitude = centerLon,
        count = count,
        hasLiveConnections = hasLive,
        zIndex = if (hazardTop) 9_000f else 0f,
    )
}
