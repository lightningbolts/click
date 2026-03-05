package compose.project.click.click.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import compose.project.click.click.ui.utils.TimeState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKCircle
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKMarkerAnnotationView
import platform.MapKit.MKPointAnnotation
import platform.MapKit.MKUserLocation
import platform.MapKit.MKUserTrackingModeNone
import platform.MapKit.MKStandardMapConfiguration
import platform.MapKit.MKMapElevationStyleFlat
import platform.UIKit.UIColor
import platform.UIKit.UIUserInterfaceStyle
import kotlin.collections.filterIsInstance
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

@OptIn(ExperimentalForeignApi::class)
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
    var lastPinsHash by remember { mutableStateOf(pins.hashCode() + clusters.hashCode()) }
    var hasCentered by remember { mutableStateOf(false) }
    var mapRef by remember { mutableStateOf<MKMapView?>(null) }
    var lastAppliedTargetLat by remember { mutableStateOf<Double?>(null) }
    var lastAppliedTargetLon by remember { mutableStateOf<Double?>(null) }
    var lastAppliedTargetZoom by remember { mutableStateOf<Double?>(null) }

    UIKitView(
        modifier = modifier,
        factory = {
            MKMapView().apply {
                mapRef = this
                showsCompass = true
                showsScale = false
                zoomEnabled = true
                scrollEnabled = true
                showsUserLocation = !ghostMode
                userTrackingMode = MKUserTrackingModeNone
                
                // Enable dark mode for the map
                overrideUserInterfaceStyle = UIUserInterfaceStyle.UIUserInterfaceStyleDark
                
                // Use flat elevation for cleaner dark appearance
                preferredConfiguration = MKStandardMapConfiguration().apply {
                    elevationStyle = MKMapElevationStyleFlat
                }
            }
        },
        update = { map ->
            mapRef = map
            // Update user location visibility based on ghost mode
            map.showsUserLocation = !ghostMode
            
            val currentHash = pins.hashCode() + clusters.hashCode()
            
            // Handle Pins and Clusters
            if (currentHash != lastPinsHash || !hasCentered) {
                // Remove existing annotations (except user location)
                val existingAnnotations = map.annotations.filterIsInstance<MKPointAnnotation>()
                if (existingAnnotations.isNotEmpty()) {
                    map.removeAnnotations(existingAnnotations)
                }

                // Add individual pins with color based on time state
                pins.forEach { pin ->
                    val ann = MKPointAnnotation()
                    val displayTitle = when (pin.timeState) {
                        TimeState.LIVE -> "🔵 ${pin.title}"
                        TimeState.RECENT -> "💠 ${pin.title}"
                        TimeState.ARCHIVE -> "⚪ ${pin.title}"
                    }
                    ann.setTitle(displayTitle)
                    ann.setCoordinate(CLLocationCoordinate2DMake(pin.latitude, pin.longitude))
                    map.addAnnotation(ann)
                }

                // Add cluster pins
                clusters.forEach { cluster ->
                    val ann = MKPointAnnotation()
                    val icon = if (cluster.hasLiveConnections) "🔵" else "⭕"
                    ann.setTitle("$icon ${cluster.count} memories")
                    ann.setCoordinate(CLLocationCoordinate2DMake(cluster.latitude, cluster.longitude))
                    map.addAnnotation(ann)
                }

                // Initial centering
                if (!hasCentered) {
                    val initialTarget = when {
                        centerLat != null && centerLon != null -> Pair(centerLat, centerLon)
                        else -> computeDataCenter(pins, clusters)
                    }

                    if (initialTarget != null) {
                        val (targetLat, targetLon) = initialTarget
                        val meters = metersForZoom(zoom)
                        val center = CLLocationCoordinate2DMake(targetLat, targetLon)
                        val region = MKCoordinateRegionMakeWithDistance(center, meters, meters)
                        map.setRegion(map.regionThatFits(region), false)
                        lastAppliedTargetLat = targetLat
                        lastAppliedTargetLon = targetLon
                        lastAppliedTargetZoom = zoom
                        hasCentered = true
                    }
                }
                lastPinsHash = currentHash
            }

            // Handle camera target animation
            if (centerLat != null && centerLon != null) {
                val centerChanged =
                    !approximatelyEqual(lastAppliedTargetLat, centerLat, epsilon = 0.000001) ||
                    !approximatelyEqual(lastAppliedTargetLon, centerLon, epsilon = 0.000001) ||
                    !approximatelyEqual(lastAppliedTargetZoom, zoom, epsilon = 0.01)

                if (centerChanged) {
                    val meters = metersForZoom(zoom)
                    val center = CLLocationCoordinate2DMake(centerLat, centerLon)
                    val region = MKCoordinateRegionMakeWithDistance(center, meters, meters)
                    map.setRegion(map.regionThatFits(region), true)
                    lastAppliedTargetLat = centerLat
                    lastAppliedTargetLon = centerLon
                    lastAppliedTargetZoom = zoom
                    onCameraAnimationComplete()
                }
            } else {
                // Apply external zoom changes only when meaningfully different from the map's current zoom.
                // This avoids fighting user double-tap/pinch gestures and prevents accidental zoom-to-world resets.
                val currentMapZoom = map.region.useContents {
                    approximateZoomLevel(
                        latitudeDelta = span.latitudeDelta,
                        longitudeDelta = span.longitudeDelta
                    )
                }
                if (abs(zoom - currentMapZoom) > 0.35) {
                    val meters = metersForZoom(zoom)
                    val center = map.centerCoordinate
                    val region = MKCoordinateRegionMakeWithDistance(center, meters, meters)
                    map.setRegion(map.regionThatFits(region), true)
                }
            }
        }
    )

    // Poll the map's current region so viewport-dependent UI always reflects the true iOS map window.
    LaunchedEffect(mapRef) {
        val map = mapRef ?: return@LaunchedEffect
        var lastMinLat: Double? = null
        var lastMaxLat: Double? = null
        var lastMinLon: Double? = null
        var lastMaxLon: Double? = null
        var lastReportedZoom: Double? = null

        while (isActive) {
            val (minLat, maxLat, minLon, maxLon, zoomLevel) = map.region.useContents {
                val minLatValue = center.latitude - (span.latitudeDelta / 2.0)
                val maxLatValue = center.latitude + (span.latitudeDelta / 2.0)
                val minLonValue = center.longitude - (span.longitudeDelta / 2.0)
                val maxLonValue = center.longitude + (span.longitudeDelta / 2.0)
                val zoomValue = approximateZoomLevel(
                    latitudeDelta = span.latitudeDelta,
                    longitudeDelta = span.longitudeDelta
                )
                listOf(minLatValue, maxLatValue, minLonValue, maxLonValue, zoomValue)
            }

            val boundsChanged =
                !approximatelyEqual(lastMinLat, minLat) ||
                !approximatelyEqual(lastMaxLat, maxLat) ||
                !approximatelyEqual(lastMinLon, minLon) ||
                !approximatelyEqual(lastMaxLon, maxLon)
            if (boundsChanged) {
                onVisibleBoundsChanged(minLat, maxLat, minLon, maxLon)
                lastMinLat = minLat
                lastMaxLat = maxLat
                lastMinLon = minLon
                lastMaxLon = maxLon
            }

            if (!approximatelyEqual(lastReportedZoom, zoomLevel, epsilon = 0.05)) {
                onZoomChanged(zoomLevel)
                lastReportedZoom = zoomLevel
            }

            delay(250)
        }
    }
}

private fun computeDataCenter(pins: List<MapPin>, clusters: List<MapClusterPin>): Pair<Double, Double>? {
    if (pins.isNotEmpty()) {
        val minLat = pins.minOf { it.latitude }
        val maxLat = pins.maxOf { it.latitude }
        val minLon = pins.minOf { it.longitude }
        val maxLon = pins.maxOf { it.longitude }
        return Pair((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0)
    }
    if (clusters.isNotEmpty()) {
        val minLat = clusters.minOf { it.latitude }
        val maxLat = clusters.maxOf { it.latitude }
        val minLon = clusters.minOf { it.longitude }
        val maxLon = clusters.maxOf { it.longitude }
        return Pair((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0)
    }
    return null
}

private fun approximateZoomLevel(latitudeDelta: Double, longitudeDelta: Double): Double {
    val zoomCandidates = mutableListOf<Double>()

    val safeLon = longitudeDelta.takeIf { it.isFinite() && it > 0.0 && it <= 180.0 }
    val safeLat = latitudeDelta.takeIf { it.isFinite() && it > 0.0 && it <= 180.0 }

    if (safeLon != null) {
        zoomCandidates += ln(360.0 / safeLon) / ln(2.0)
    }
    if (safeLat != null) {
        zoomCandidates += ln(180.0 / safeLat) / ln(2.0)
    }

    val raw = when {
        zoomCandidates.isEmpty() -> 10.0
        else -> zoomCandidates.average()
    }.coerceIn(2.0, 20.0)

    return ((raw * 100.0).toInt()) / 100.0
}

private fun approximatelyEqual(previous: Double?, current: Double, epsilon: Double = 0.00001): Boolean {
    return previous != null && kotlin.math.abs(previous - current) <= epsilon
}

private fun metersForZoom(zoomLevel: Double): Double {
    val maxZoom = 20.0
    val minMeters = 120.0
    val maxMeters = 4_000_000.0
    val normalized = (maxZoom - zoomLevel).coerceIn(0.0, maxZoom)
    val meters = minMeters * 2.0.pow(normalized)
    return meters.coerceIn(minMeters, maxMeters)
}
