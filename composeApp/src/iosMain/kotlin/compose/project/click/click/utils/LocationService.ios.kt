package compose.project.click.click.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.CLLocation
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * iOS implementation of LocationService using CLLocationManager.
 */
actual class LocationService {
    private val locationManager = CLLocationManager()

    actual suspend fun getCurrentLocation(): LocationResult? {
        return suspendCancellableCoroutine { continuation ->
            val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                    val candidates = didUpdateLocations.filterIsInstance<CLLocation>()
                    val location = candidates.firstOrNull { loc ->
                        loc.horizontalAccuracy > 0.0 && loc.horizontalAccuracy <= 100.0
                    } ?: candidates.firstOrNull { loc ->
                        loc.horizontalAccuracy > 0.0 && loc.horizontalAccuracy <= 300.0
                    }

                    manager.stopUpdatingLocation()
                    manager.delegate = null
                    if (location != null) {
                        @OptIn(ExperimentalForeignApi::class)
                        val result = location.coordinate.useContents {
                            LocationResult(latitude = this.latitude, longitude = this.longitude)
                        }
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    } else {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                }

                override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                    manager.stopUpdatingLocation()
                    manager.delegate = null
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }

            locationManager.delegate = delegate
            locationManager.desiredAccuracy = kCLLocationAccuracyBest

            if (hasLocationPermission()) {
                // Single-shot request avoids long-running updates and reduces stale values.
                locationManager.requestLocation()
            } else {
                // If no permission, return null — caller should request first
                continuation.resume(null)
            }

            continuation.invokeOnCancellation {
                locationManager.stopUpdatingLocation()
                locationManager.delegate = null
            }
        }
    }

    actual fun hasLocationPermission(): Boolean {
        val status = CLLocationManager.authorizationStatus()
        return status == kCLAuthorizationStatusAuthorizedWhenInUse ||
               status == kCLAuthorizationStatusAuthorizedAlways
    }

    actual fun requestLocationPermission() {
        locationManager.requestWhenInUseAuthorization()
    }
}
