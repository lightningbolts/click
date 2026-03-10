package compose.project.click.click.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
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
    private companion object {
        const val REQUEST_TIMEOUT_MS = 6_000L
        const val PREFERRED_ACCURACY_METERS = 100.0
        const val FALLBACK_ACCURACY_METERS = 300.0
        const val CACHED_ACCURACY_METERS = 150.0
    }

    private val locationManager = CLLocationManager()
    private var activeDelegate: NSObject? = null

    actual suspend fun getCurrentLocation(): LocationResult? {
        return withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                fun finish(result: LocationResult?) {
                    locationManager.stopUpdatingLocation()
                    locationManager.delegate = null
                    activeDelegate = null
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }

                val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                        val candidates = didUpdateLocations.filterIsInstance<CLLocation>()
                        val location = candidates.firstOrNull { loc ->
                            loc.horizontalAccuracy > 0.0 && loc.horizontalAccuracy <= PREFERRED_ACCURACY_METERS
                        } ?: candidates.firstOrNull { loc ->
                            loc.horizontalAccuracy > 0.0 && loc.horizontalAccuracy <= FALLBACK_ACCURACY_METERS
                        }

                        finish(location?.toLocationResult() ?: cachedLocationResult())
                    }

                    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                        finish(cachedLocationResult())
                    }
                }

                activeDelegate = delegate
                locationManager.delegate = delegate
                locationManager.desiredAccuracy = kCLLocationAccuracyBest

                if (hasLocationPermission()) {
                    // Single-shot request avoids long-running updates and reduces stale values.
                    locationManager.requestLocation()
                } else {
                    finish(null)
                }

                continuation.invokeOnCancellation {
                    locationManager.stopUpdatingLocation()
                    locationManager.delegate = null
                    activeDelegate = null
                }
            }
        } ?: cachedLocationResult()
    }

    actual fun hasLocationPermission(): Boolean {
        val status = CLLocationManager.authorizationStatus()
        return status == kCLAuthorizationStatusAuthorizedWhenInUse ||
               status == kCLAuthorizationStatusAuthorizedAlways
    }

    actual fun requestLocationPermission() {
        locationManager.requestWhenInUseAuthorization()
    }

    private fun cachedLocationResult(): LocationResult? {
        val cached = locationManager.location ?: return null
        if (cached.horizontalAccuracy <= 0.0 || cached.horizontalAccuracy > CACHED_ACCURACY_METERS) {
            return null
        }

        return cached.toLocationResult()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun CLLocation.toLocationResult(): LocationResult = coordinate.useContents {
        LocationResult(
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = this@toLocationResult.altitude.takeIf { this@toLocationResult.verticalAccuracy >= 0.0 }
        )
    }
}
