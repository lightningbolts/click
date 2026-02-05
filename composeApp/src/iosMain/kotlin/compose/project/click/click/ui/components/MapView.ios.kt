package compose.project.click.click.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import compose.project.click.click.ui.utils.TimeState
import kotlinx.cinterop.ExperimentalForeignApi
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
    onZoomChanged: (Double) -> Unit
) {
    var lastZoom by remember { mutableStateOf(zoom) }
    var lastPinsHash by remember { mutableStateOf(pins.hashCode() + clusters.hashCode()) }
    var hasCentered by remember { mutableStateOf(false) }

    UIKitView(
        modifier = modifier,
        factory = {
            MKMapView().apply {
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
                    val target = pins.firstOrNull() ?: clusters.firstOrNull()?.let { 
                        MapPin(it.id, "", it.latitude, it.longitude)
                    }
                    if (target != null) {
                        val meters = metersForZoom(zoom)
                        val center = CLLocationCoordinate2DMake(target.latitude, target.longitude)
                        val region = MKCoordinateRegionMakeWithDistance(center, meters, meters)
                        map.setRegion(map.regionThatFits(region), false)
                        hasCentered = true
                    }
                }
                lastPinsHash = currentHash
            }

            // Handle camera target animation
            if (centerLat != null && centerLon != null) {
                val meters = metersForZoom(zoom)
                val center = CLLocationCoordinate2DMake(centerLat, centerLon)
                val region = MKCoordinateRegionMakeWithDistance(center, meters, meters)
                map.setRegion(map.regionThatFits(region), true)
            } else if (zoom != lastZoom) {
                // Handle Zoom only (no position change)
                val meters = metersForZoom(zoom)
                val center = map.centerCoordinate
                val region = MKCoordinateRegionMakeWithDistance(center, meters, meters)
                map.setRegion(map.regionThatFits(region), true)
                lastZoom = zoom
            }
        }
    )
}

private fun metersForZoom(zoomLevel: Double): Double {
    val maxZoom = 20.0
    val minMeters = 120.0
    val maxMeters = 4_000_000.0
    val normalized = (maxZoom - zoomLevel).coerceIn(0.0, maxZoom)
    val meters = minMeters * 2.0.pow(normalized)
    return meters.coerceIn(minMeters, maxMeters)
}
