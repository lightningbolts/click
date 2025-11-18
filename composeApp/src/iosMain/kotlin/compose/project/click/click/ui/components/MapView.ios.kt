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
            val clamped = zoom.coerceIn(0.0, 22.0)
            val spanDelta = when {
                clamped >= 20 -> 0.002
                clamped >= 18 -> 0.005
                clamped >= 16 -> 0.01
                clamped >= 14 -> 0.02
                clamped >= 12 -> 0.04
                clamped >= 10 -> 0.08
                clamped >= 8 -> 0.16
                clamped >= 6 -> 0.32
                clamped >= 4 -> 0.64
                clamped >= 2 -> 1.28
                else -> 2.56
            }
            val span: CValue<MKCoordinateSpan> = MKCoordinateSpanMake(spanDelta, spanDelta)
            val region = MKCoordinateRegionMake(CLLocationCoordinate2DMake(first.latitude, first.longitude), span)
            map.setRegion(region, true)
        }
    )
}
