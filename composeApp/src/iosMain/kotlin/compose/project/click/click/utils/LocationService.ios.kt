package compose.project.click.click.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/**
 * iOS [LocationService]: permission state comes from [IosLocationAuthorizationTracker] (delegate +
 * background bootstrap), not synchronous [CLLocationManager.authorizationStatus] on the main thread.
 * [getCurrentLocation] uses a single global mutex so concurrent callers cannot clobber the shared [CLLocationManager] delegate.
 * Completion always runs on the main queue for thread safety with Compose.
 */
actual class LocationService {

    private companion object {
        val fetchMutex = Mutex()

        /** One-shot request; align with product expectation (~3–5s). */
        const val REQUEST_TIMEOUT_MS = 5_000L

        private val ACCURACY_THRESHOLDS_METERS = doubleArrayOf(100.0, 300.0, 1_000.0, 5_000.0, Double.MAX_VALUE)
    }

    private val locationManager = CLLocationManager()
    private var activeDelegate: NSObject? = null

    actual suspend fun getHighAccuracyLocation(timeoutMs: Long): LocationResult? {
        if (timeoutMs <= 0L) return null
        return fetchMutex.withLock {
            coroutineScope {
                suspendCancellableCoroutine { continuation ->
                    var finished = false
                    var timeoutJob: Job? = null
                    val session = ProgressiveLocationSession.start()

                    fun cleanup() {
                        locationManager.stopUpdatingLocation()
                        locationManager.delegate = null
                        activeDelegate = null
                    }

                    fun finishOnMain(result: LocationResult?) {
                        dispatch_async(dispatch_get_main_queue()) {
                            if (finished) return@dispatch_async
                            finished = true
                            timeoutJob?.cancel()
                            cleanup()
                            if (continuation.isActive) {
                                continuation.resume(result)
                            }
                        }
                    }

                    if (!CLLocationManager.locationServicesEnabled()) {
                        finishOnMain(null)
                        return@suspendCancellableCoroutine
                    }

                    if (!hasLocationPermission()) {
                        finishOnMain(null)
                        return@suspendCancellableCoroutine
                    }

                    val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                            val candidates = didUpdateLocations.filterIsInstance<CLLocation>()
                            if (candidates.isEmpty()) return
                            for (loc in candidates) {
                                val acc = loc.horizontalAccuracy
                                if (acc <= 0.0 || !acc.isFinite()) continue
                                val (lat, lon) = loc.latLonOrNull() ?: continue
                                val alt = loc.altitude.takeIf { loc.verticalAccuracy >= 0.0 }
                                val accepted = session.onReading(lat, lon, acc, alt)
                                if (accepted != null) {
                                    finishOnMain(accepted)
                                    return
                                }
                            }
                        }

                        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                            finishOnMain(session.bestAtTimeout())
                        }
                    }

                    activeDelegate = delegate
                    locationManager.delegate = delegate
                    locationManager.desiredAccuracy = kCLLocationAccuracyBest

                    continuation.invokeOnCancellation {
                        dispatch_async(dispatch_get_main_queue()) {
                            if (!finished) {
                                finished = true
                                timeoutJob?.cancel()
                                cleanup()
                            }
                        }
                    }

                    timeoutJob = launch {
                        delay(timeoutMs)
                        finishOnMain(session.bestAtTimeout())
                    }

                    locationManager.startUpdatingLocation()
                }
            }
        }
    }

    actual suspend fun getCurrentLocation(): LocationResult? {
        return fetchMutex.withLock {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    var finished = false

                    fun cleanup() {
                        locationManager.stopUpdatingLocation()
                        locationManager.delegate = null
                        activeDelegate = null
                    }

                    /**
                     * Core Location may invoke the delegate off the main thread; resume only on the main queue.
                     * [didUpdateLocations] can fire more than once — complete at most once.
                     */
                    fun finishOnMain(result: LocationResult?) {
                        dispatch_async(dispatch_get_main_queue()) {
                            if (finished) return@dispatch_async
                            finished = true
                            cleanup()
                            if (continuation.isActive) {
                                continuation.resume(result)
                            }
                        }
                    }

                    if (!CLLocationManager.locationServicesEnabled()) {
                        finishOnMain(null)
                        return@suspendCancellableCoroutine
                    }

                    if (!hasLocationPermission()) {
                        finishOnMain(null)
                        return@suspendCancellableCoroutine
                    }

                    val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                            val candidates = didUpdateLocations.filterIsInstance<CLLocation>()
                            if (candidates.isEmpty()) {
                                return
                            }
                            val picked = pickBestLocation(candidates)
                                ?: cachedLocationResult(maxAccuracyMeters = 5_000.0)
                            finishOnMain(picked ?: cachedLocationResult(maxAccuracyMeters = 5_000.0))
                        }

                        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                            val fallback = cachedLocationResult(maxAccuracyMeters = 5_000.0)
                            finishOnMain(fallback)
                        }
                    }

                    activeDelegate = delegate
                    locationManager.delegate = delegate
                    locationManager.desiredAccuracy = kCLLocationAccuracyBest

                    continuation.invokeOnCancellation {
                        dispatch_async(dispatch_get_main_queue()) {
                            if (!finished) {
                                finished = true
                                cleanup()
                            }
                        }
                    }

                    locationManager.requestLocation()
                }
            } ?: run {
                locationManager.stopUpdatingLocation()
                locationManager.delegate = null
                activeDelegate = null
                cachedLocationResult(maxAccuracyMeters = 5_000.0)
            }
        }
    }

    /**
     * Always query the system — never a stale client-side cache or UserDefaults mirror.
     */
    actual fun hasLocationPermission(): Boolean {
        if (!CLLocationManager.locationServicesEnabled()) {
            return false
        }
        IosLocationAuthorizationTracker.ensureStarted()
        return IosLocationAuthorizationTracker.hasWhenInUseOrAlways()
    }

    actual fun requestLocationPermission() {
        IosLocationAuthorizationTracker.ensureStarted()
        dispatch_async(dispatch_get_main_queue()) {
            locationManager.requestWhenInUseAuthorization()
        }
    }

    private fun pickBestLocation(locations: List<CLLocation>): LocationResult? {
        val valid = locations.filter { it.horizontalAccuracy > 0.0 && it.horizontalAccuracy.isFinite() }
        if (valid.isEmpty()) return null
        for (i in 0 until ACCURACY_THRESHOLDS_METERS.size) {
            val maxAcc = ACCURACY_THRESHOLDS_METERS[i]
            val inBand = valid.filter { it.horizontalAccuracy <= maxAcc }
            val best = inBand.minByOrNull { it.horizontalAccuracy } ?: continue
            return best.toLocationResult()
        }
        return null
    }

    private fun cachedLocationResult(maxAccuracyMeters: Double): LocationResult? {
        val cached = locationManager.location ?: return null
        if (cached.horizontalAccuracy <= 0.0 || cached.horizontalAccuracy > maxAccuracyMeters) {
            return null
        }
        return cached.toLocationResult()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun CLLocation.latLonOrNull(): Pair<Double, Double>? = coordinate.useContents {
        if (!latitude.isFinite() || !longitude.isFinite()) return@useContents null
        if (latitude == 0.0 && longitude == 0.0) return@useContents null
        latitude to longitude
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun CLLocation.toLocationResult(): LocationResult {
        val acc = horizontalAccuracy.takeIf { it > 0.0 && it.isFinite() }
        return coordinate.useContents {
            LocationResult(
                latitude = latitude,
                longitude = longitude,
                altitudeMeters = this@toLocationResult.altitude.takeIf { this@toLocationResult.verticalAccuracy >= 0.0 },
                accuracyMeters = acc,
            )
        }
    }
}
