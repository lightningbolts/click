package compose.project.click.click.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
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
            // Try to get the last known location first (instant, no battery cost)
            val lastLocation = getLastKnownLocation()
            if (lastLocation != null) {
                println("LocationService.android: Got last known location: ${lastLocation.latitude}, ${lastLocation.longitude}")
                return lastLocation
            }

            // Fall back to requesting a fresh location
            println("LocationService.android: Requesting fresh location...")
            getFreshLocation()
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
                    if (location != null && location.latitude != 0.0 && location.longitude != 0.0) {
                        continuation.resume(LocationResult(location.latitude, location.longitude))
                    } else {
                        continuation.resume(null)
                    }
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
                    if (location != null) {
                        println("LocationService.android: Fresh location: ${location.latitude}, ${location.longitude}")
                        continuation.resume(LocationResult(location.latitude, location.longitude))
                    } else {
                        println("LocationService.android: Fresh location returned null")
                        continuation.resume(null)
                    }
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
