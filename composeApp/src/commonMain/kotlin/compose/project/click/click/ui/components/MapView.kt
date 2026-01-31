package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import compose.project.click.click.ui.utils.ConnectionMapPoint
import compose.project.click.click.ui.utils.MapCluster
import compose.project.click.click.ui.utils.TimeState

/**
 * Represents a point to plot on the map
 * Enhanced with visual decay metadata
 */
data class MapPin(
    val id: String,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val isNearby: Boolean = false,
    val timeState: TimeState = TimeState.RECENT,
    val opacity: Float = 1.0f,
    val shouldPulse: Boolean = false,
    val imageUrl: String? = null  // For avatar pins
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
                imageUrl = imageUrl
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
    val hasLiveConnections: Boolean = false
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
    onZoomChanged: (Double) -> Unit = {}
)

/**
 * Create a cluster pin from a MapCluster
 */
fun MapCluster.toClusterPin(): MapClusterPin {
    val hasLive = points.any { it.timeState == TimeState.LIVE }
    return MapClusterPin(
        id = id,
        latitude = centerLat,
        longitude = centerLon,
        count = count,
        hasLiveConnections = hasLive
    )
}
