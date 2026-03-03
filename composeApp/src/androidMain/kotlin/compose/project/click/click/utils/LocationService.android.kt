package compose.project.click.click.utils

/**
 * Android stub implementation of LocationService.
 * TODO: Implement with FusedLocationProviderClient for real location support.
 */
actual class LocationService {

    actual suspend fun getCurrentLocation(): LocationResult? {
        // Stub: return null until FusedLocationProvider is integrated
        return null
    }

    actual fun hasLocationPermission(): Boolean {
        // Stub: return false until runtime permission check is implemented
        return false
    }

    actual fun requestLocationPermission() {
        // Stub: no-op until Activity-based permission request is wired
    }
}
