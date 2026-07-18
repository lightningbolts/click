package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.ui.theme.LocalIsDarkMode
import compose.project.click.click.ui.utils.BeaconPinMetrics
import compose.project.click.click.utils.LocationService
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
    mapGesturesEnabled: Boolean,
    showCompass: Boolean,
    onPinTapped: (MapPin) -> Unit,
    onClusterTapped: (MapClusterPin) -> Unit,
    onZoomChanged: (Double) -> Unit,
    onVisibleBoundsChanged: (minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) -> Unit,
    onCameraAnimationComplete: () -> Unit,
    onMapGesture: () -> Unit,
) {
    val locationService = remember { LocationService() }
    val hasLocationPermission = locationService.hasLocationPermission()
    val canShowMyLocation = !ghostMode && hasLocationPermission
    val deviceLocation by AppDataManager.lastKnownDeviceLocation.collectAsState()
    val isDarkMode = LocalIsDarkMode.current

    // Initial frame only — device GPS / first pin. Programmatic moves use explicit centerLat/Lon.
    val deviceLoc = deviceLocation
    val initialCenter = when {
        centerLat != null && centerLon != null -> LatLng(centerLat, centerLon)
        deviceLoc != null -> LatLng(deviceLoc.first, deviceLoc.second)
        pins.isNotEmpty() -> LatLng(pins.first().latitude, pins.first().longitude)
        clusters.isNotEmpty() -> LatLng(clusters.first().latitude, clusters.first().longitude)
        else -> LatLng(40.7580, -73.9855) // Default to NYC
    }
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialCenter, zoom.toFloat())
    }

    // One effect: lat/lon + zoom use newLatLngZoom together. A separate zoomTo effect kept the
    // old viewport center and broke cluster zoom.
    // Only animate when the ViewModel supplies an explicit center (CameraTarget). When centers
    // clear after onCameraAnimationComplete, leave the GoogleMap where it settled — do not
    // fall back to deviceLocation (that snapped Featured Event focus back to the user).
    LaunchedEffect(centerLat, centerLon, zoom, mapGesturesEnabled) {
        if (centerLat != null && centerLon != null) {
            val target = LatLng(centerLat, centerLon)
            val z = zoom.toFloat()
            val pos = cameraPositionState.position
            val moved = abs(pos.target.latitude - centerLat) > 1e-5 ||
                abs(pos.target.longitude - centerLon) > 1e-5
            val zoomChanged = abs(pos.zoom - z) > 0.05f
            if (moved || zoomChanged) {
                val update = com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(target, z)
                if (!mapGesturesEnabled) {
                    cameraPositionState.move(update)
                } else {
                    cameraPositionState.animate(update = update)
                    onCameraAnimationComplete()
                }
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

    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.isMoving }
            .distinctUntilChanged()
            .collectLatest { moving ->
                if (moving) onMapGesture()
            }
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

    // Map basemap: ghost → grayscale; dark app → zinc dark style; light app → default color tiles
    // (PR #44 map_color_android + Track A dark/light policy).
    val mapProperties = remember(ghostMode, canShowMyLocation, isDarkMode) {
        MapProperties(
            // Enabling my-location without runtime permission crashes with SecurityException.
            isMyLocationEnabled = canShowMyLocation,
            mapStyleOptions = when {
                ghostMode -> MapStyleOptions(GRAYSCALE_MAP_STYLE)
                isDarkMode -> MapStyleOptions(DARK_MAP_STYLE)
                else -> null
            }
        )
    }

    val density = LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val clusterIconCache = remember { mutableMapOf<String, BitmapDescriptor>() }
    val labeledPinCache = remember { mutableMapOf<String, BitmapDescriptor>() }
    val avatarPinCache = remember { mutableMapOf<String, BitmapDescriptor>() }
    var loadedAvatarBitmaps by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    val imageLoader = remember(context) { coil3.ImageLoader(context) }

    LaunchedEffect(pins) {
        val urls = pins.mapNotNull { it.imageUrl?.trim()?.takeIf { u -> u.isNotEmpty() } }.distinct()
        urls.forEach { url ->
            if (loadedAvatarBitmaps.containsKey(url)) return@forEach
            val request = coil3.request.ImageRequest.Builder(context)
                .data(url)
                .size(128)
                .build()
            val result = runCatching { imageLoader.execute(request) }.getOrNull()
            if (result is coil3.request.SuccessResult) {
                val src = when (val img = result.image) {
                    is coil3.BitmapImage -> img.bitmap
                    else -> null
                } ?: return@forEach
                // Software copy — Google Maps markers reject hardware bitmaps.
                val bmp = if (src.config == Bitmap.Config.HARDWARE) {
                    src.copy(Bitmap.Config.ARGB_8888, false) ?: return@forEach
                } else {
                    src.copy(Bitmap.Config.ARGB_8888, false) ?: src
                }
                loadedAvatarBitmaps = loadedAvatarBitmaps + (url to bmp)
            }
        }
    }

    // GoogleMap is a SurfaceView-backed AndroidView: it must receive explicit max constraints.
    // Parent scale animations (e.g. AnimatedVisibility scaleIn) also break SurfaceView compositing.
    Box(modifier = modifier.fillMaxSize()) {
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = showCompass,
            myLocationButtonEnabled = canShowMyLocation,
            scrollGesturesEnabled = mapGesturesEnabled,
            zoomGesturesEnabled = mapGesturesEnabled,
            rotationGesturesEnabled = mapGesturesEnabled,
            tiltGesturesEnabled = mapGesturesEnabled,
        )
    ) {
        pins.forEach { pin ->
            key(pin.id, pin.imageUrl, loadedAvatarBitmaps[pin.imageUrl?.trim().orEmpty()]?.generationId) {
                val markerHue = pin.markerHueDegrees()
                val squadScale = pin.squadMultiplier.coerceAtLeast(1f)
                val position = remember(pin.id, pin.latitude, pin.longitude) {
                    LatLng(pin.latitude, pin.longitude)
                }
                val markerState = remember(pin.id) { MarkerState(position = position) }
                LaunchedEffect(pin.latitude, pin.longitude) {
                    markerState.position = LatLng(pin.latitude, pin.longitude)
                }
                val photo = pin.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let { loadedAvatarBitmaps[it] }
                // Fixed 44dp diameter for all avatar pins (squad only bumps z-index / pulse).
                val avatarCacheKey =
                    "${pin.id}|${pin.avatarInitials}|$markerHue|${pin.avatarFillArgb}|${photo?.generationId ?: 0}"
                val icon = avatarPinCache.getOrPut(avatarCacheKey) {
                    bitmapDescriptorForCircularAvatarPin(
                        density = density,
                        hueDegrees = markerHue,
                        initials = pin.avatarInitials,
                        photo = photo,
                        scale = 1f,
                        fillArgb = pin.avatarFillArgb,
                    )
                }
                Marker(
                    state = markerState,
                    title = pin.title,
                    alpha = pin.opacity,
                    zIndex = pin.zIndex + if (squadScale > 1f) 500f else 0f,
                    icon = icon,
                    anchor = Offset(0.5f, 0.5f),
                    onClick = {
                        onPinTapped(pin)
                        true
                    },
                )
            }
        }

        // Render cluster pins
        clusters.forEach { cluster ->
            key("cluster-${cluster.id}") {
                val clusterPosition = remember(cluster.id, cluster.latitude, cluster.longitude) {
                    LatLng(cluster.latitude, cluster.longitude)
                }
                val clusterState = remember(cluster.id) { MarkerState(position = clusterPosition) }
                LaunchedEffect(cluster.latitude, cluster.longitude) {
                    clusterState.position = LatLng(cluster.latitude, cluster.longitude)
                }
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
                val px = with(density) { 44.dp.roundToPx() }.coerceAtLeast(36)
                val fill = when {
                    cluster.isConnectionOnly -> android.graphics.Color.argb(230, 220, 0, 200)
                    cluster.hasLiveConnections -> android.graphics.Color.argb(230, 0, 163, 255)
                    else -> android.graphics.Color.argb(230, 255, 150, 50)
                }
                bitmapDescriptorFromClusterCount(cluster.count, px, fill)
            }
            Marker(
                state = clusterState,
                title = "${cluster.count}",
                zIndex = cluster.zIndex,
                icon = bmp,
                anchor = Offset(0.5f, 0.5f),
                onClick = {
                    onClusterTapped(cluster)
                    true
                }
            )
            }
        }
    }
    }
}

private fun bitmapDescriptorForCircularAvatarPin(
    density: androidx.compose.ui.unit.Density,
    hueDegrees: Float,
    initials: String,
    photo: Bitmap?,
    scale: Float,
    fillArgb: Int? = null,
): BitmapDescriptor {
    val sizePx = with(density) { (44.dp * scale).roundToPx() }.coerceAtLeast(36)
    val borderPx = with(density) { 2.dp.toPx() }.coerceAtLeast(2f)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val radius = sizePx / 2f - borderPx / 2f

    val fillColor = fillArgb ?: run {
        val hsv = floatArrayOf(hueDegrees, 0.72f, 0.92f)
        android.graphics.Color.HSVToColor(hsv)
    }

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, radius, fillPaint)

    if (photo != null && !photo.isRecycled) {
        val shaderBmp = Bitmap.createScaledBitmap(photo, sizePx, sizePx, true)
        val shader = android.graphics.BitmapShader(
            shaderBmp,
            android.graphics.Shader.TileMode.CLAMP,
            android.graphics.Shader.TileMode.CLAMP,
        )
        val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
        }
        canvas.drawCircle(cx, cy, radius - borderPx * 0.25f, photoPaint)
    } else {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.34f
            isFakeBoldText = true
        }
        val glyph = initials.take(2).ifEmpty { "?" }
        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(glyph, cx, textY, textPaint)
    }

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = borderPx
    }
    canvas.drawCircle(cx, cy, radius, borderPaint)

    return BitmapDescriptorFactory.fromBitmap(bmp)
}

private fun bitmapDescriptorForSquadPin(
    density: androidx.compose.ui.unit.Density,
    hueDegrees: Float,
    scale: Float,
): BitmapDescriptor {
    val basePx = with(density) { (28.dp * scale).roundToPx() }
    val auraPx = with(density) { (44.dp * scale).roundToPx() }
    val bmp = Bitmap.createBitmap(auraPx, auraPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = auraPx / 2f
    val cy = auraPx / 2f
    val aura = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(90, 0, 163, 255)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, auraPx / 2f, aura)
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.HSVToColor(floatArrayOf(hueDegrees, 0.92f, 0.95f))
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, basePx / 2f, dot)
    return BitmapDescriptorFactory.fromBitmap(bmp)
}

private fun bitmapDescriptorFromClusterCount(count: Int, sizePx: Int, fillArgb: Int): BitmapDescriptor {
    val d = sizePx.coerceIn(36, 128)
    val bmp = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = fillArgb
        style = android.graphics.Paint.Style.FILL
    }
    val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = (d * 0.06f).coerceAtLeast(2f)
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
    val pinRadius = with(density) { BeaconPinMetrics.CircleRadiusDp.dp.roundToPx() }
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

// Dark map style for app dark mode (Zinc-950 base)
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
