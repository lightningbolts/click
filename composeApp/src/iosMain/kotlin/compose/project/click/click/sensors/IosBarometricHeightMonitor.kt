package compose.project.click.click.sensors

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import compose.project.click.click.data.models.HeightCategory
import compose.project.click.click.data.models.deriveHeightCategory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreMotion.CMAltimeter
import platform.Foundation.NSOperationQueue
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import platform.darwin.NSEC_PER_MSEC
import kotlin.coroutines.resume
import kotlin.math.pow

class IosBarometricHeightMonitor : BarometricHeightMonitor {
    override val isAvailable: Boolean
        get() = CMAltimeter.isRelativeAltitudeAvailable()

    override suspend fun sampleHeightReading(durationMs: Int): BarometricHeightSample? {
        if (!isAvailable) return null

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
                            ?.let { elevation ->
                                deriveHeightCategory(elevation)?.let { category ->
                                    BarometricHeightSample(
                                        category = category,
                                        elevationMeters = elevation,
                                        pressureHpa = pressureHpa
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
                    }
                )

                dispatch_after(
                    dispatch_time(DISPATCH_TIME_NOW, durationMs.toLong() * NSEC_PER_MSEC.toLong()),
                    dispatch_get_main_queue()
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
    return remember { IosBarometricHeightMonitor() }
}