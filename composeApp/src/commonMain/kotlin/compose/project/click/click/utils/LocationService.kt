package compose.project.click.click.utils

/**
 * Platform-agnostic location result.
 */
data class LocationResult(
    val latitude: Double,
    val longitude: Double
)

/**
 * Expected platform-specific location service.
 * Implementations should use CLLocationManager (iOS) or FusedLocationProvider (Android).
 */
expect class LocationService() {
    /**
     * Fetch the current device location.
     * Returns null if location permissions are denied or location is unavailable.
     */
    suspend fun getCurrentLocation(): LocationResult?

    /**
     * Check if location permissions have been granted.
     */
    fun hasLocationPermission(): Boolean

    /**
     * Request location permissions from the user.
     */
    fun requestLocationPermission()
}
