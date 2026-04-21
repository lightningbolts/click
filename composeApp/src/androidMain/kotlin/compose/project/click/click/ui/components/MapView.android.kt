package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
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

    val density = LocalDensity.current
    val clusterIconCache = remember { mutableMapOf<Int, BitmapDescriptor>() }

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
            val markerHue = pinHue(pin)
            Marker(
                state = MarkerState(position = LatLng(pin.latitude, pin.longitude)),
                title = pin.title,
                alpha = pin.opacity,
                zIndex = pin.zIndex,
                icon = BitmapDescriptorFactory.defaultMarker(markerHue),
                onClick = {
                    onPinTapped(pin)
                    true // Consume the click
                }
            )
        }

        // Render cluster pins
        clusters.forEach { cluster ->
            val bmp = clusterIconCache.getOrPut(cluster.count) {
                val px = with(density) { 52.dp.roundToPx() }
                bitmapDescriptorFromClusterCount(cluster.count, px)
            }
            Marker(
                state = MarkerState(position = LatLng(cluster.latitude, cluster.longitude)),
                title = "${cluster.count}",
                zIndex = cluster.zIndex,
                icon = bmp,
                onClick = {
                    onClusterTapped(cluster)
                    true
                }
            )
        }
    }
}

private fun pinHue(pin: MapPin): Float =
    when (pin.kind) {
        MapPinKind.CONNECTION -> when (pin.timeState) {
            TimeState.LIVE -> BitmapDescriptorFactory.HUE_AZURE
            TimeState.RECENT -> BitmapDescriptorFactory.HUE_CYAN
            TimeState.ARCHIVE -> BitmapDescriptorFactory.HUE_VIOLET
        }
        MapPinKind.BEACON_SOUNDTRACK -> BitmapDescriptorFactory.HUE_GREEN
        MapPinKind.BEACON_ALERT -> BitmapDescriptorFactory.HUE_RED
        MapPinKind.BEACON_SOCIAL -> BitmapDescriptorFactory.HUE_MAGENTA
        MapPinKind.BEACON_OTHER -> BitmapDescriptorFactory.HUE_YELLOW
    }

private fun bitmapDescriptorFromClusterCount(count: Int, sizePx: Int): BitmapDescriptor {
    val d = sizePx.coerceIn(48, 128)
    val bmp = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(230, 0, 163, 255)
        style = android.graphics.Paint.Style.FILL
    }
    val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(255, 255, 255, 255)
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = d * 0.06f
    }
    val r = RectF(0f, 0f, d.toFloat(), d.toFloat())
    val pad = d * 0.06f
    r.inset(pad, pad)
    canvas.drawOval(r, fill)
    canvas.drawOval(r, stroke)
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = d * 0.36f
        isFakeBoldText = true
    }
    val label = if (count > 99) "99+" else count.toString()
    val cx = d / 2f
    val cy = d / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(label, cx, cy, textPaint)
    return BitmapDescriptorFactory.fromBitmap(bmp)
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
