package compose.project.click.click.sensors

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import compose.project.click.click.data.models.deriveHeightCategory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import platform.CoreMotion.CMAltimeter
import platform.CoreMotion.CMAltitudeData
import platform.Foundation.NSError
import platform.Foundation.NSLock
import platform.Foundation.NSOperationQueue
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import platform.darwin.NSEC_PER_MSEC
import kotlin.coroutines.resume
import kotlin.math.pow

/**
 * Shared relative-altitude stream so [sampleHeightReading] can return a recent sample
 * instead of always cold-starting [CMAltimeter] (which often yields nothing in a 350ms window).
 */
private object IosBarometricAltimeterStream {
    private val lock = NSLock()

    private var refCount = 0
    private var altimeter: CMAltimeter? = null

    private var cachedSample: BarometricHeightSample? = null

    private var cacheEpochMs: Long = 0L

    private inline fun <T> withLock(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }

    fun retain() {
        val needsStart = withLock {
            refCount++
            refCount == 1
        }
        if (needsStart) {
            startAltimeter()
        }
    }

    fun release() {
        val cm = withLock {
            if (refCount > 0) refCount--
            if (refCount == 0) {
                val a = altimeter
                altimeter = null
                a
            } else {
                null
            }
        }
        cm?.stopRelativeAltitudeUpdates()
    }

    fun cached(maxAgeMs: Long): BarometricHeightSample? = withLock {
        val sample = cachedSample ?: return@withLock null
        val age = Clock.System.now().toEpochMilliseconds() - cacheEpochMs
        sample.takeIf { age in 0..maxAgeMs }
    }

    private fun startAltimeter() {
        if (!CMAltimeter.isRelativeAltitudeAvailable()) return
        val cm = withLock {
            if (altimeter != null) return@withLock null
            CMAltimeter().also { altimeter = it }
        } ?: return
        cm.startRelativeAltitudeUpdatesToQueue(
            NSOperationQueue.mainQueue,
            withHandler = baro@{ data: CMAltitudeData?, error: NSError? ->
                if (error != null) return@baro
                val kpa = data?.pressure?.doubleValue ?: return@baro
                if (kpa <= 0.0) return@baro
                val hpa = kpa * 10.0
                val elevation = 44330.0 * (1.0 - (hpa / 1013.25).pow(0.1903))
                if (!elevation.isFinite() || elevation !in -500.0..12000.0) return@baro
                val category = deriveHeightCategory(elevation) ?: return@baro
                withLock {
                    cachedSample = BarometricHeightSample(
                        category = category,
                        elevationMeters = elevation,
                        pressureHpa = hpa,
                    )
                    cacheEpochMs = Clock.System.now().toEpochMilliseconds()
                }
            },
        )
    }
}

class IosBarometricHeightMonitor : BarometricHeightMonitor {
    override val isAvailable: Boolean
        get() = CMAltimeter.isRelativeAltitudeAvailable()

    override fun ensureBackgroundCaching() {
        if (isAvailable) {
            IosBarometricAltimeterStream.retain()
        }
    }

    override fun releaseBackgroundCaching() {
        IosBarometricAltimeterStream.release()
    }

    override suspend fun sampleHeightReading(durationMs: Int): BarometricHeightSample? {
        if (!isAvailable) return null

        IosBarometricAltimeterStream.cached(maxAgeMs = 12_000L)?.let { return it }

        return withTimeoutOrNull(durationMs.toLong() + 750L) {
            suspendCancellableCoroutine { continuation ->
                val altimeter = CMAltimeter()
                val pressureReadings = mutableListOf<Double>()
                var finished = false

                fun finish() {
                    if (finished) return
                    finished = true
                    altimeter.stopRelativeAltitudeUpdates()
                    val averagePressureKpa = pressureReadings.average().takeIf { !it.isNaN() }
                    val pressureHpa = averagePressureKpa?.times(10.0)
                    val altitudeMeters = pressureHpa?.let { pressure ->
                        44330.0 * (1.0 - (pressure / 1013.25).pow(0.1903))
                    }
                    if (continuation.isActive) {
                        val sample = altitudeMeters
                            ?.takeIf { it.isFinite() && it in -500.0..12000.0 }
                            ?.let { elevation ->
                                deriveHeightCategory(elevation)?.let { category ->
                                    BarometricHeightSample(
                                        category = category,
                                        elevationMeters = elevation,
                                        pressureHpa = pressureHpa,
                                    )
                                }
                            }
                        continuation.resume(sample)
                    }
                }

                altimeter.startRelativeAltitudeUpdatesToQueue(
                    NSOperationQueue.mainQueue,
                    withHandler = { data, error ->
                        if (error != null) {
                            finish()
                        } else {
                            val pressure = data?.pressure?.doubleValue
                            if (pressure != null && pressure > 0.0) {
                                pressureReadings += pressure
                            }
                        }
                    },
                )

                dispatch_after(
                    dispatch_time(DISPATCH_TIME_NOW, durationMs.toLong() * NSEC_PER_MSEC.toLong()),
                    dispatch_get_main_queue(),
                ) {
                    finish()
                }

                continuation.invokeOnCancellation {
                    altimeter.stopRelativeAltitudeUpdates()
                }
            }
        }
    }
}

@Composable
actual fun rememberBarometricHeightMonitor(): BarometricHeightMonitor {
    val monitor = remember { IosBarometricHeightMonitor() }
    DisposableEffect(monitor) {
        monitor.ensureBackgroundCaching()
        onDispose {
            monitor.releaseBackgroundCaching()
        }
    }
    return monitor
}
