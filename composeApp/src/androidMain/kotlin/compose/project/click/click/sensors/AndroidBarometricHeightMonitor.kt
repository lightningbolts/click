package compose.project.click.click.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import compose.project.click.click.data.models.HeightCategory
import compose.project.click.click.data.models.deriveHeightCategory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidBarometricHeightMonitor(
    context: Context
) : BarometricHeightMonitor {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

    override val isAvailable: Boolean
        get() = sensorManager != null && pressureSensor != null

    override suspend fun sampleHeightReading(durationMs: Int): BarometricHeightSample? {
        val manager = sensorManager ?: return null
        val sensor = pressureSensor ?: return null

        return suspendCancellableCoroutine { continuation ->
            val mainHandler = Handler(Looper.getMainLooper())
            val pressureReadings = mutableListOf<Float>()

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val pressureHpa = event.values.firstOrNull()?.takeIf { it > 0f } ?: return
                    pressureReadings += pressureHpa
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                }
            }

            fun finish() {
                manager.unregisterListener(listener)
                mainHandler.removeCallbacksAndMessages(null)
                val averagePressure = pressureReadings.average().takeIf { !it.isNaN() }?.toFloat()
                val altitudeMeters = averagePressure?.let {
                    SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, it)
                }?.toDouble()
                if (continuation.isActive) {
                    val sample = altitudeMeters
                        ?.let { elevation ->
                            deriveHeightCategory(elevation)?.let { category ->
                                BarometricHeightSample(category = category, elevationMeters = elevation)
                            }
                        }
                    continuation.resume(sample)
                }
            }

            if (!manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            mainHandler.postDelayed({ finish() }, durationMs.toLong())

            continuation.invokeOnCancellation {
                manager.unregisterListener(listener)
                mainHandler.removeCallbacksAndMessages(null)
            }
        }
    }
}

@Composable
actual fun rememberBarometricHeightMonitor(): BarometricHeightMonitor {
    val context = LocalContext.current
    return remember(context) { AndroidBarometricHeightMonitor(context) }
}