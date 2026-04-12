package compose.project.click.click.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import compose.project.click.click.data.models.deriveHeightCategory
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Clock
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.math.pow

internal class AndroidBarometricStream(
    private val sensorManager: SensorManager,
    private val pressureSensor: Sensor,
) {
    private val lock = Any()
    private var refCount = 0
    private var listener: SensorEventListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val cachedSample = AtomicReference<BarometricHeightSample?>(null)
    private val cacheEpochMs = AtomicLong(0L)

    fun retain() {
        val shouldStart = synchronized(lock) {
            refCount++
            refCount == 1
        }
        if (!shouldStart) return

        val lis = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val pressureHpa = event.values.firstOrNull()?.takeIf { it > 0f }?.toDouble() ?: return
                val elevation = 44330.0 * (1.0 - (pressureHpa / 1013.25).pow(0.1903))
                if (!elevation.isFinite() || elevation !in -500.0..12000.0) return
                val category = deriveHeightCategory(elevation) ?: return
                cachedSample.set(
                    BarometricHeightSample(
                        category = category,
                        elevationMeters = elevation,
                        pressureHpa = pressureHpa,
                    ),
                )
                cacheEpochMs.set(Clock.System.now().toEpochMilliseconds())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        synchronized(lock) {
            listener = lis
        }
        sensorManager.registerListener(lis, pressureSensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun release() {
        val lis = synchronized(lock) {
            if (refCount > 0) refCount--
            if (refCount == 0) {
                val l = listener
                listener = null
                l
            } else {
                null
            }
        }
        lis?.let { sensorManager.unregisterListener(it) }
        mainHandler.removeCallbacksAndMessages(null)
    }

    fun cached(maxAgeMs: Long): BarometricHeightSample? {
        val sample = cachedSample.get() ?: return null
        val age = Clock.System.now().toEpochMilliseconds() - cacheEpochMs.get()
        return sample.takeIf { age in 0..maxAgeMs }
    }
}

internal class AndroidBarometricHeightMonitor(
    context: Context,
    private val stream: AndroidBarometricStream?,
) : BarometricHeightMonitor {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

    override val isAvailable: Boolean
        get() = sensorManager != null && pressureSensor != null

    override fun ensureBackgroundCaching() {
        stream?.retain()
    }

    override fun releaseBackgroundCaching() {
        stream?.release()
    }

    override suspend fun sampleHeightReading(durationMs: Int): BarometricHeightSample? {
        val manager = sensorManager ?: return null
        val sensor = pressureSensor ?: return null

        stream?.cached(12_000L)?.let { return it }

        return suspendCancellableCoroutine { continuation ->
            val mainHandler = Handler(Looper.getMainLooper())
            val pressureReadings = mutableListOf<Float>()

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val pressureHpa = event.values.firstOrNull()?.takeIf { it > 0f } ?: return
                    pressureReadings += pressureHpa
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
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
                        ?.takeIf { it.isFinite() && it in -500.0..12000.0 }
                        ?.let { elevation ->
                            deriveHeightCategory(elevation)?.let { category ->
                                BarometricHeightSample(
                                    category = category,
                                    elevationMeters = elevation,
                                    pressureHpa = averagePressure?.toDouble(),
                                )
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
    val monitor = remember(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val ps = sm?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        val stream = if (sm != null && ps != null) {
            AndroidBarometricStream(sm, ps)
        } else {
            null
        }
        AndroidBarometricHeightMonitor(context, stream)
    }
    DisposableEffect(monitor) {
        monitor.ensureBackgroundCaching()
        onDispose {
            monitor.releaseBackgroundCaching()
        }
    }
    return monitor
}
