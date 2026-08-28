package compose.project.click.click.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
 * Retained [CLLocationManager] delegate — must stay strongly referenced while Core Location
 * is active. Anonymous objects assigned off the main thread can trigger
 * `NSInternalInconsistencyException: Delegate must respond to locationManager:didUpdateLocations:`.
 */
private class LocationFetchDelegate(
    private val onUpdate: (List<CLLocation>) -> Unit,
    private val onFail: (NSError) -> Unit,
) : NSObject(),
    CLLocationManagerDelegateProtocol {
    override fun locationManager(
        manager: CLLocationManager,
        didUpdateLocations: List<*>,
    ) {
        onUpdate(didUpdateLocations.filterIsInstance<CLLocation>())
    }

    override fun locationManager(
        manager: CLLocationManager,
        didFailWithError: NSError,
    ) {
        onFail(didFailWithError)
    }
}

/**
 * iOS [LocationService]: permission state comes from [IosLocationAuthorizationTracker] (delegate +
 * background bootstrap), not synchronous [CLLocationManager.authorizationStatus] on the main thread.
 * [getCurrentLocation] uses a single global mutex so concurrent callers cannot clobber the shared [CLLocationManager] delegate.
 * All [CLLocationManager] mutations run on the main queue.
 */
actual class LocationService {
    private companion object {
        val fetchMutex = Mutex()

        /** One-shot request; align with product expectation (~3–5s). */
        const val REQUEST_TIMEOUT_MS = 5_000L

        private val ACCURACY_THRESHOLDS_METERS = doubleArrayOf(100.0, 300.0, 1_000.0, 5_000.0, Double.MAX_VALUE)
    }

    private val locationManager = CLLocationManager()
    private var activeDelegate: LocationFetchDelegate? = null

    actual suspend fun getHighAccuracyLocation(timeoutMs: Long): LocationResult? =
        fetchProgressiveLocation(timeoutMs, telemetryTier = false)

    actual suspend fun getTelemetryLocation(timeoutMs: Long): LocationResult? = fetchProgressiveLocation(timeoutMs, telemetryTier = true)

    private suspend fun fetchProgressiveLocation(
        timeoutMs: Long,
        telemetryTier: Boolean,
    ): LocationResult? {
        if (timeoutMs <= 0L) return null
        if (!withContext(Dispatchers.Default) { CLLocationManager.locationServicesEnabled() }) {
            return null
        }
        return fetchMutex.withLock {
            coroutineScope {
                suspendCancellableCoroutine { continuation ->
                    var finished = false
                    var timeoutJob: Job? = null
                    val session = ProgressiveLocationSession.start()

                    fun cleanupOnMain() {
                        locationManager.stopUpdatingLocation()
                        locationManager.delegate = null
                        activeDelegate = null
                    }

                    fun finishOnMain(result: LocationResult?) {
                        dispatch_async(dispatch_get_main_queue()) {
                            if (finished) return@dispatch_async
                            finished = true
                            timeoutJob?.cancel()
                            cleanupOnMain()
                            if (continuation.isActive) {
                                continuation.resume(result)
                            }
                        }
                    }

                    if (!hasLocationPermission()) {
                        finishOnMain(null)
                        return@suspendCancellableCoroutine
                    }

                    val delegate =
                        LocationFetchDelegate(
                            onUpdate = { candidates ->
                                if (candidates.isEmpty()) return@LocationFetchDelegate
                                for (loc in candidates) {
                                    val acc = loc.horizontalAccuracy
                                    if (acc <= 0.0 || !acc.isFinite()) continue
                                    val (lat, lon) = loc.latLonOrNull() ?: continue
                                    val alt = loc.altitude.takeIf { loc.verticalAccuracy >= 0.0 }
                                    val accepted =
                                        if (telemetryTier) {
                                            session.onTelemetryReading(lat, lon, acc, alt)
                                        } else {
                                            session.onReading(lat, lon, acc, alt)
                                        }
                                    if (accepted != null) {
                                        finishOnMain(accepted)
                                        return@LocationFetchDelegate
                                    }
                                }
                            },
                            onFail = {
                                finishOnMain(
                                    if (telemetryTier) session.bestTelemetryAtTimeout() else session.bestAtTimeout(),
                                )
                            },
                        )

                    continuation.invokeOnCancellation {
                        dispatch_async(dispatch_get_main_queue()) {
                            if (!finished) {
                                finished = true
                                timeoutJob?.cancel()
                                cleanupOnMain()
                            }
                        }
                    }

                    timeoutJob =
                        launch {
                            delay(timeoutMs)
                            finishOnMain(
                                if (telemetryTier) session.bestTelemetryAtTimeout() else session.bestAtTimeout(),
                            )
                        }

                    launch(Dispatchers.Main.immediate) {
                        activeDelegate = delegate
                        locationManager.delegate = delegate
                        locationManager.desiredAccuracy = kCLLocationAccuracyBest
                        locationManager.startUpdatingLocation()
                    }
                }
            }
        }
    }

    actual suspend fun getCurrentLocation(): LocationResult? {
        if (!withContext(Dispatchers.Default) { CLLocationManager.locationServicesEnabled() }) {
            return null
        }
        return fetchMutex.withLock {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    var finished = false

                    fun cleanupOnMain() {
                        locationManager.stopUpdatingLocation()
                        locationManager.delegate = null
                        activeDelegate = null
                    }

                    fun finishOnMain(result: LocationResult?) {
                        dispatch_async(dispatch_get_main_queue()) {
                            if (finished) return@dispatch_async
                            finished = true
                            cleanupOnMain()
                            if (continuation.isActive) {
                                continuation.resume(result)
                            }
                        }
                    }

                    if (!hasLocationPermission()) {
                        finishOnMain(null)
                        return@suspendCancellableCoroutine
                    }

                    val delegate =
                        LocationFetchDelegate(
                            onUpdate = { candidates ->
                                if (candidates.isEmpty()) return@LocationFetchDelegate
                                val picked =
                                    pickBestLocation(candidates)
                                        ?: cachedLocationResult(maxAccuracyMeters = 5_000.0)
                                finishOnMain(picked ?: cachedLocationResult(maxAccuracyMeters = 5_000.0))
                            },
                            onFail = {
                                finishOnMain(cachedLocationResult(maxAccuracyMeters = 5_000.0))
                            },
                        )

                    continuation.invokeOnCancellation {
                        dispatch_async(dispatch_get_main_queue()) {
                            if (!finished) {
                                finished = true
                                cleanupOnMain()
                            }
                        }
                    }

                    // Prefer startUpdatingLocation — requestLocation() is stricter about
                    // main-thread delegate wiring and has crashed under concurrent map polls.
                    dispatch_async(dispatch_get_main_queue()) {
                        activeDelegate = delegate
                        locationManager.delegate = delegate
                        locationManager.desiredAccuracy = kCLLocationAccuracyBest
                        locationManager.startUpdatingLocation()
                    }
                }
            } ?: withContext(Dispatchers.Main.immediate) {
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
    private fun CLLocation.latLonOrNull(): Pair<Double, Double>? =
        coordinate.useContents {
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
