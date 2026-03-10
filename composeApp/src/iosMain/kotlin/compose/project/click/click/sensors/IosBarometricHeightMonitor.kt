package compose.project.click.click.sensors

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import compose.project.click.click.data.models.HeightCategory
import compose.project.click.click.data.models.deriveHeightCategory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreMotion.CMAltimeter
import platform.Foundation.NSOperationQueue
import kotlin.coroutines.resume
import kotlin.math.pow

class IosBarometricHeightMonitor : BarometricHeightMonitor {
    override val isAvailable: Boolean
        get() = CMAltimeter.isRelativeAltitudeAvailable()

    override suspend fun sampleHeightCategory(durationMs: Int): HeightCategory? {
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
                        continuation.resume(deriveHeightCategory(altitudeMeters))
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