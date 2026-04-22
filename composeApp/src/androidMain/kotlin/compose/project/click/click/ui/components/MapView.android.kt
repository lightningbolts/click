package compose.project.click.click.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.ui.utils.TimeState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

@Composable
actual fun PlatformMap(
    modifier: Modifier,
    pins: List<MapPin>,
    clusters: List<MapClusterPin>,
    zoom: Double,
    centerLat: Double?,
    centerLon: Double?,
    ghostMode: Boolean,
    onPinTapped: (MapPin) -> Unit,
    onClusterTapped: (MapClusterPin) -> Unit,
    onZoomChanged: (Double) -> Unit,
    onVisibleBoundsChanged: (minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) -> Unit,
    onCameraAnimationComplete: () -> Unit
) {
    // Determine center position
    val initialCenter = when {
        centerLat != null && centerLon != null -> LatLng(centerLat, centerLon)
        pins.isNotEmpty() -> LatLng(pins.first().latitude, pins.first().longitude)
        clusters.isNotEmpty() -> LatLng(clusters.first().latitude, clusters.first().longitude)
        else -> LatLng(40.7580, -73.9855) // Default to NYC
    }
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialCenter, zoom.toFloat())
    }

    // One effect: lat/lon + zoom use newLatLngZoom together. A separate zoomTo effect kept the
    // old viewport center and broke cluster zoom.
    LaunchedEffect(centerLat, centerLon, zoom) {
        if (centerLat != null && centerLon != null) {
            val target = LatLng(centerLat, centerLon)
            val z = zoom.toFloat()
            val pos = cameraPositionState.position
            val moved = abs(pos.target.latitude - centerLat) > 1e-5 ||
                abs(pos.target.longitude - centerLon) > 1e-5
            val zoomChanged = abs(pos.zoom - z) > 0.05f
            if (moved || zoomChanged) {
                cameraPositionState.animate(
                    update = com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(target, z),
                )
                onCameraAnimationComplete()
            }
        }
        // Do not call zoomTo from ViewModel zoom when center is null — the GoogleMap owns
        // pinch/double-tap zoom; mirroring state here fought gestures and caused random zoom jumps.
    }

    // Report zoom changes back to the ViewModel.
    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.position.zoom.toDouble() }
            .distinctUntilChanged()
            .collectLatest { onZoomChanged(it) }
    }

    // Report true visible map bounds so "memories in view" uses the real viewport.
    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.projection?.visibleRegion?.latLngBounds }
            .filterNotNull()
            .map { bounds ->
                listOf(
                    bounds.southwest.latitude,
                    bounds.northeast.latitude,
                    bounds.southwest.longitude,
                    bounds.northeast.longitude
                )
            }
            .distinctUntilChanged()
            .collectLatest { (minLat, maxLat, minLon, maxLon) ->
                onVisibleBoundsChanged(minLat, maxLat, minLon, maxLon)
            }
    }

    // Map properties - grayscale when ghost mode
    val mapProperties = remember(ghostMode) {
        MapProperties(
            isMyLocationEnabled = !ghostMode,
            mapStyleOptions = if (ghostMode) {
                // Grayscale style for ghost mode
                MapStyleOptions(GRAYSCALE_MAP_STYLE)
            } else {
                // Dark mode style for normal mode
                MapStyleOptions(DARK_MAP_STYLE)
            }
        )
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = !ghostMode
        )
    ) {
        // Render individual pins
        pins.forEach { pin ->
            val markerColor = when (pin.timeState) {
                TimeState.LIVE -> BitmapDescriptorFactory.HUE_AZURE
                TimeState.RECENT -> BitmapDescriptorFactory.HUE_CYAN
                TimeState.ARCHIVE -> BitmapDescriptorFactory.HUE_VIOLET
            }
            
            Marker(
                state = MarkerState(position = LatLng(pin.latitude, pin.longitude)),
                title = pin.title,
                alpha = pin.opacity,
                icon = BitmapDescriptorFactory.defaultMarker(markerColor),
                onClick = {
                    onPinTapped(pin)
                    true // Consume the click
                }
            )
        }

        // Render cluster pins
        clusters.forEach { cluster ->
            val markerColor = if (cluster.hasLiveConnections) {
                BitmapDescriptorFactory.HUE_AZURE
            } else {
                BitmapDescriptorFactory.HUE_ORANGE
            }
            
            Marker(
                state = MarkerState(position = LatLng(cluster.latitude, cluster.longitude)),
                title = "${cluster.count} memories",
                icon = BitmapDescriptorFactory.defaultMarker(markerColor),
                onClick = {
                    onClusterTapped(cluster)
                    true
                }
            )
            
            // Add a circle around clusters for visual effect
            Circle(
                center = LatLng(cluster.latitude, cluster.longitude),
                radius = 100.0 * cluster.count, // Radius based on count
                fillColor = Color(0x2200A3FF), // Semi-transparent blue
                strokeColor = Color(0x6600A3FF),
                strokeWidth = 2f
            )
        }
    }
}

// Dark map style JSON matching the Glass & Neon aesthetic (Zinc-950 base)
private const val DARK_MAP_STYLE = """
[
  {
    "elementType": "geometry",
    "stylers": [{"color": "#09090b"}]
  },
  {
    "elementType": "labels.icon",
    "stylers": [{"visibility": "off"}]
  },
  {
    "elementType": "labels.text.fill",
    "stylers": [{"color": "#71717a"}]
  },
  {
    "elementType": "labels.text.stroke",
    "stylers": [{"color": "#09090b"}]
  },
  {
    "featureType": "road",
    "elementType": "geometry",
    "stylers": [{"color": "#18181b"}]
  },
  {
    "featureType": "road.highway",
    "elementType": "geometry",
    "stylers": [{"color": "#27272a"}]
  },
  {
    "featureType": "water",
    "elementType": "geometry",
    "stylers": [{"color": "#0c0c0e"}]
  },
  {
    "featureType": "poi",
    "elementType": "geometry",
    "stylers": [{"color": "#18181b"}]
  },
  {
    "featureType": "transit",
    "elementType": "geometry",
    "stylers": [{"color": "#18181b"}]
  }
]
"""

// Grayscale map style for ghost mode
private const val GRAYSCALE_MAP_STYLE = """
[
  {
    "stylers": [
      {"saturation": -100},
      {"lightness": -30}
    ]
  },
  {
    "elementType": "labels",
    "stylers": [{"visibility": "simplified"}]
  }
]
"""
