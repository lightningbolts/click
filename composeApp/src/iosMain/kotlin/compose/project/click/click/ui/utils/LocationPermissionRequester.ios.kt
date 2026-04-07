package compose.project.click.click.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import compose.project.click.click.utils.LocationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted

@Composable
actual fun rememberLocationPermissionRequester(): ((onComplete: () -> Unit) -> Unit) {
    val locationService = remember { LocationService() }
    return { onComplete ->
        when (CLLocationManager.authorizationStatus()) {
            kCLAuthorizationStatusDenied,
            kCLAuthorizationStatusRestricted,
            -> {
                openApplicationSystemSettings()
                onComplete()
            }
            kCLAuthorizationStatusAuthorizedWhenInUse,
            kCLAuthorizationStatusAuthorizedAlways,
            -> onComplete()
            else -> {
                locationService.requestLocationPermission()
                CoroutineScope(Dispatchers.Main).launch {
                    delay(800)
                    onComplete()
                }
            }
        }
    }
}
