package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.*

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformMap(
    modifier: Modifier,
    pins: List<MapPin>,
    zoom: Double,
    onPinTapped: (MapPin) -> Unit
) {
    UIKitView(
        factory = {
            val map = MKMapView()
            map.showsCompass = true
            map.showsScale = false
            map.zoomEnabled = true
            map.scrollEnabled = true
            map.showsUserLocation = true
            map.userTrackingMode = MKUserTrackingModeFollow
            map
        },
        modifier = modifier,
        update = { map ->
            // Clear and add annotations
            map.removeAnnotations(map.annotations)
            pins.forEach { pin ->
                val ann = MKPointAnnotation()
                ann.setTitle(pin.title)
                ann.setCoordinate(CLLocationCoordinate2DMake(pin.latitude, pin.longitude))
                map.addAnnotation(ann)
            }

            val first = pins.firstOrNull() ?: return@UIKitView
            val clamped = zoom.coerceIn(2.0, 20.0)

            // Calculate span delta using exponential function for smoother zoom
            // Formula: span = base^(maxZoom - currentZoom) * scale
            val spanDelta = kotlin.math.pow(1.5, (20.0 - clamped)) * 0.0001

            val span: CValue<MKCoordinateSpan> = MKCoordinateSpanMake(spanDelta, spanDelta)
            val region = MKCoordinateRegionMake(
                CLLocationCoordinate2DMake(first.latitude, first.longitude),
                span
            )

            // Use animated=false to ensure immediate update
            map.setRegion(region, false)
        }
    )
}
