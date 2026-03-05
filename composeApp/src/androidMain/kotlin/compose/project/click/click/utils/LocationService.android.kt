package compose.project.click.click.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Android implementation of LocationService using FusedLocationProviderClient.
 *
 * Requires:
 *  - android.permission.ACCESS_FINE_LOCATION (already in manifest)
 *  - com.google.android.gms:play-services-location (added to build.gradle.kts)
 *
 * Context is initialized from MainActivity via [initLocationService].
 */
actual class LocationService {

    private companion object {
        private const val FRESH_MAX_AGE_MS = 30_000L
        private const val LAST_KNOWN_MAX_AGE_MS = 10 * 60_000L
        private const val FRESH_MAX_ACCURACY_METERS = 80f
        private const val LAST_KNOWN_MAX_ACCURACY_METERS = 150f
    }

    private val context: Context
        get() = locationContext
            ?: throw IllegalStateException("LocationService not initialized. Call initLocationService() from MainActivity first.")

    private val fusedClient: FusedLocationProviderClient
        get() = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    actual suspend fun getCurrentLocation(): LocationResult? {
        if (!hasLocationPermission()) {
            println("LocationService.android: No location permission")
            return null
        }

        if (!isLocationEnabled()) {
            println("LocationService.android: Location services disabled")
            return null
        }

        return try {
            // Prefer a fresh high-accuracy fix first.
            val freshLocation = getFreshLocation()
            if (freshLocation != null) {
                println("LocationService.android: Got fresh location: ${freshLocation.latitude}, ${freshLocation.longitude}")
                return freshLocation
            }

            // Fall back to a recent last-known fix if a fresh fix is unavailable.
            val lastLocation = getLastKnownLocation()
            if (lastLocation != null) {
                println("LocationService.android: Falling back to last known location: ${lastLocation.latitude}, ${lastLocation.longitude}")
            }
            lastLocation
        } catch (e: Exception) {
            println("LocationService.android: Error getting location: ${e.message}")
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocation(): LocationResult? {
        return suspendCancellableCoroutine { continuation ->
            fusedClient.lastLocation
                .addOnSuccessListener { location ->
                    continuation.resume(location?.toValidatedLocationResult(
                        maxAgeMs = LAST_KNOWN_MAX_AGE_MS,
                        maxAccuracyMeters = LAST_KNOWN_MAX_ACCURACY_METERS
                    ))
                }
                .addOnFailureListener {
                    println("LocationService.android: lastLocation failed: ${it.message}")
                    continuation.resume(null)
                }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getFreshLocation(): LocationResult? {
        return suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()

            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }

            fusedClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            )
                .addOnSuccessListener { location ->
                    val validated = location?.toValidatedLocationResult(
                        maxAgeMs = FRESH_MAX_AGE_MS,
                        maxAccuracyMeters = FRESH_MAX_ACCURACY_METERS
                    )
                    if (validated != null) {
                        println("LocationService.android: Fresh location accepted")
                    } else {
                        println("LocationService.android: Fresh location missing or not accurate enough")
                    }
                    continuation.resume(validated)
                }
                .addOnFailureListener { e ->
                    println("LocationService.android: Fresh location failed: ${e.message}")
                    continuation.resume(null)
                }
        }
    }

    actual fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    actual fun requestLocationPermission() {
        // Permissions are requested via Accompanist or ActivityResultContracts
        // at the composable level. This is a no-op since we can't launch
        // permission requests without an Activity reference.
        println("LocationService.android: requestLocationPermission() called — use Accompanist at UI level")
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
               locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    private fun Location.toValidatedLocationResult(maxAgeMs: Long, maxAccuracyMeters: Float): LocationResult? {
        if (latitude == 0.0 && longitude == 0.0) return null

        val ageMs = (System.currentTimeMillis() - time).coerceAtLeast(0L)
        if (ageMs > maxAgeMs) return null

        if (hasAccuracy() && accuracy > maxAccuracyMeters) return null

        return LocationResult(latitude = latitude, longitude = longitude)
    }
}

// ── Context initialization (same pattern as TokenStorage) ──

private var locationContext: Context? = null

/**
 * Initialize LocationService with application context.
 * Must be called from MainActivity.onCreate() before any LocationService usage.
 */
fun initLocationService(context: Context) {
    locationContext = context.applicationContext
}
