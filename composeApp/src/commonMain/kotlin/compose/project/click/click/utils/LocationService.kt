package compose.project.click.click.utils

/**
 * Platform-agnostic location result.
 *
 * @param accuracyMeters Horizontal accuracy radius (68th percentile); null when the platform did not provide it.
 */
data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Double? = null,
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
     * Streams high-accuracy fixes for up to [timeoutMs], applying [ProgressiveLocationSession] rules.
     * Returns null if permission is denied, services are off, or no reading meets the final threshold.
     * Implementations must remove listeners before returning.
     */
    suspend fun getHighAccuracyLocation(timeoutMs: Long = 4000L): LocationResult?

    /**
     * Check if location permissions have been granted.
     */
    fun hasLocationPermission(): Boolean

    /**
     * Request location permissions from the user.
     */
    fun requestLocationPermission()
}
