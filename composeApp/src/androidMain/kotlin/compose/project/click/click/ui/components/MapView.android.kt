package compose.project.click.click.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@Composable
actual fun PlatformMap(
    modifier: Modifier,
    pins: List<MapPin>,
    zoom: Double,
    onPinTapped: (MapPin) -> Unit
) {
    val center = pins.firstOrNull()?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(40.7580, -73.9855)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, zoom.toFloat())
    }

    LaunchedEffect(zoom) {
        cameraPositionState.animate(
            update = com.google.android.gms.maps.CameraUpdateFactory.zoomTo(zoom.toFloat())
        )
    }

    LaunchedEffect(pins) {
        if (pins.isNotEmpty()) {
            val firstPin = pins.first()
            cameraPositionState.animate(
                update = com.google.android.gms.maps.CameraUpdateFactory.newLatLng(
                    LatLng(firstPin.latitude, firstPin.longitude)
                )
            )
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(zoomControlsEnabled = false)
    ) {
        pins.forEach { pin ->
            Marker(
                state = MarkerState(position = LatLng(pin.latitude, pin.longitude)),
                title = pin.title,
                onClick = {
                    onPinTapped(pin)
                    false // Return false to allow default behavior (showing info window)
                }
            )
        }
    }
}
