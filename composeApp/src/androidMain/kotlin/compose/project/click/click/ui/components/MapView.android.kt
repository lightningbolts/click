package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import compose.project.click.click.ui.components.markerHueDegrees
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.max

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
    val clusterIconCache = remember { mutableMapOf<String, BitmapDescriptor>() }
    val labeledPinCache = remember { mutableMapOf<String, BitmapDescriptor>() }

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
            key(pin.id) {
                val markerHue = pin.markerHueDegrees()
                val caption = pin.caption?.trim().orEmpty()
                if (caption.isEmpty()) {
                    Marker(
                        state = MarkerState(position = LatLng(pin.latitude, pin.longitude)),
                        title = pin.title,
                        alpha = pin.opacity,
                        zIndex = pin.zIndex,
                        icon = BitmapDescriptorFactory.defaultMarker(markerHue),
                        onClick = {
                            onPinTapped(pin)
                            true
                        },
                    )
                } else {
                    val cacheKey = "${pin.id}|$caption|$markerHue|${pin.opacity}"
                    val icon = labeledPinCache.getOrPut(cacheKey) {
                        bitmapDescriptorForLabeledPin(
                            density = density,
                            hueDegrees = markerHue,
                            caption = caption,
                            pinOpacity = pin.opacity,
                        )
                    }
                    Marker(
                        state = MarkerState(position = LatLng(pin.latitude, pin.longitude)),
                        title = pin.title,
                        alpha = pin.opacity,
                        zIndex = pin.zIndex,
                        icon = icon,
                        anchor = Offset(0.5f, 1f),
                        onClick = {
                            onPinTapped(pin)
                            true
                        },
                    )
                }
            }
        }

        // Render cluster pins
        clusters.forEach { cluster ->
            val cacheKey = buildString {
                append(cluster.count)
                append('|')
                when {
                    cluster.isConnectionOnly -> append("conn")
                    cluster.hasLiveConnections -> append("live")
                    else -> append("mix")
                }
            }
            val bmp = clusterIconCache.getOrPut(cacheKey) {
                val px = with(density) { 52.dp.roundToPx() }
                val fill = when {
                    cluster.isConnectionOnly -> android.graphics.Color.argb(230, 220, 0, 200)
                    cluster.hasLiveConnections -> android.graphics.Color.argb(230, 0, 163, 255)
                    else -> android.graphics.Color.argb(230, 255, 150, 50)
                }
                bitmapDescriptorFromClusterCount(cluster.count, px, fill)
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

private fun bitmapDescriptorFromClusterCount(count: Int, sizePx: Int, fillArgb: Int): BitmapDescriptor {
    val d = sizePx.coerceIn(48, 128)
    val bmp = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = fillArgb
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

/**
 * Marker + caption bitmap: label pill above a tinted dot; anchor (0.5, 1) = pin at lat/lon.
 */
private fun bitmapDescriptorForLabeledPin(
    density: androidx.compose.ui.unit.Density,
    hueDegrees: Float,
    caption: String,
    pinOpacity: Float,
): BitmapDescriptor {
    val padH = with(density) { 8.dp.roundToPx() }
    val padV = with(density) { 4.dp.roundToPx() }
    val pinRadius = with(density) { 10.dp.roundToPx() }
    val gap = with(density) { 4.dp.roundToPx() }
    val corner = with(density) { 6.dp.toPx() }
    val maxLabelPx = with(density) { 132.dp.roundToPx() }

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(255, 250, 250, 250)
        textSize = with(density) { 11.sp.toPx() }
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val fm = Paint.FontMetrics()
    textPaint.getFontMetrics(fm)
    val textH = (fm.descent - fm.ascent).toInt().coerceAtLeast(1)
    val textW = kotlin.math.min(textPaint.measureText(caption), maxLabelPx.toFloat())
    val labelW = max((textW + padH * 2f).toInt(), pinRadius * 2 + padH * 2)
    val labelH = textH + padV * 2
    val totalW = max(labelW, pinRadius * 2 + padH * 2 + 4)
    val totalH = labelH + gap + pinRadius * 2 + padV

    val bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = totalW / 2f

    val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(228, 24, 24, 27)
        style = Paint.Style.FILL
    }
    val labelStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(200, 82, 82, 91)
        style = Paint.Style.STROKE
        strokeWidth = with(density) { 1.dp.toPx() }.coerceAtLeast(1f)
    }
    val labelRect = RectF(
        cx - labelW / 2f,
        0f,
        cx + labelW / 2f,
        labelH.toFloat(),
    )
    canvas.drawRoundRect(labelRect, corner, corner, labelBg)
    canvas.drawRoundRect(labelRect, corner, corner, labelStroke)

    textPaint.setShadowLayer(with(density) { 2.5.dp.toPx() }, 0f, 1f, android.graphics.Color.BLACK)
    val textY = padV - fm.ascent
    canvas.drawText(caption, cx, textY, textPaint)
    textPaint.clearShadowLayer()

    val hsv = floatArrayOf(hueDegrees.coerceIn(0f, 360f), 0.88f, 0.94f)
    val pinArgb = android.graphics.Color.HSVToColor(hsv)
    val a = (255f * pinOpacity.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
    val fillColor = pinArgb and 0x00FFFFFF or (a shl 24)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        style = Paint.Style.FILL
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb((180f * pinOpacity).toInt().coerceIn(0, 255), 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = with(density) { 2.dp.toPx() }.coerceAtLeast(2f)
    }
    val cy = labelH + gap + pinRadius
    canvas.drawCircle(cx, cy.toFloat(), pinRadius.toFloat(), fill)
    canvas.drawCircle(cx, cy.toFloat(), pinRadius.toFloat(), stroke)

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
