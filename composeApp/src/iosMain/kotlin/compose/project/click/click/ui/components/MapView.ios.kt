package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKMarkerAnnotationView
import platform.MapKit.MKPointAnnotation
import platform.MapKit.MKUserLocation
import platform.MapKit.MKUserTrackingModeNone
import kotlin.collections.filterIsInstance
import kotlin.math.pow

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformMap(
    modifier: Modifier,
    pins: List<MapPin>,
    zoom: Double,
    onPinTapped: (MapPin) -> Unit
) {
    UIKitView(
        modifier = modifier,
        factory = {
            MKMapView().apply {
                showsCompass = true
                showsScale = false
                zoomEnabled = true
                scrollEnabled = true
                showsUserLocation = true
                userTrackingMode = MKUserTrackingModeNone
            }
        },
        update = { map ->
            // Remove old annotations
            val poiAnnotations = map.annotations.filterIsInstance<MKPointAnnotation>()
            if (poiAnnotations.isNotEmpty()) {
                map.removeAnnotations(poiAnnotations)
            }

            // Add new annotations
            pins.forEach { pin ->
                val ann = MKPointAnnotation()
                ann.setTitle(pin.title)
                ann.setCoordinate(CLLocationCoordinate2DMake(pin.latitude, pin.longitude))
                map.addAnnotation(ann)
            }

            // Update map region based on zoom
            val target = pins.firstOrNull()
            if (target != null) {
                val clampedZoom = zoom.coerceIn(2.0, 20.0)
                val meters = metersForZoom(clampedZoom)
                val center = CLLocationCoordinate2DMake(target.latitude, target.longitude)
                val region = MKCoordinateRegionMakeWithDistance(center, meters, meters)
                map.setRegion(map.regionThatFits(region), true)
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
