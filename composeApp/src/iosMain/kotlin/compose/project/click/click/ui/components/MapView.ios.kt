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
    onPinTapped: (MapPin) -> Unit
) {
    UIKitView(
        factory = {
            val map = MKMapView()
            map.showsCompass = true
            map.showsScale = false
            map
        },
        modifier = modifier,
        update = { map ->
            // Remove existing annotations
            map.removeAnnotations(map.annotations)

            // Add new annotations
            pins.forEach { pin ->
                val ann = MKPointAnnotation()
                ann.setTitle(pin.title)
                ann.setCoordinate(CLLocationCoordinate2DMake(pin.latitude, pin.longitude))
                map.addAnnotation(ann)
            }

            // Center on first
            pins.firstOrNull()?.let { first ->
                val span: CValue<MKCoordinateSpan> = MKCoordinateSpanMake(0.05, 0.05)
                val region = MKCoordinateRegionMake(CLLocationCoordinate2DMake(first.latitude, first.longitude), span)
                map.setRegion(region, true)
            }
        }
    )
}
