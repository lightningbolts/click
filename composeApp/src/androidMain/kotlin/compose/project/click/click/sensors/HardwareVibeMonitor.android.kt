package compose.project.click.click.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import compose.project.click.click.data.storage.androidStorageContextOrThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

actual class HardwareVibeMonitor actual constructor() {

    actual suspend fun takeSnapshot(): HardwareVibeSnapshot = withContext(Dispatchers.Default) {
        val context = androidStorageContextOrThrow()
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val accelSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magSensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val lightSensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT)

        val accelSamples = mutableListOf<Triple<Float, Float, Float>>()
        var lastMag: Triple<Float, Float, Float>? = null
        var lux: Float? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        accelSamples += Triple(event.values[0], event.values[1], event.values[2])
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        lastMag = Triple(event.values[0], event.values[1], event.values[2])
                    }
                    Sensor.TYPE_LIGHT -> {
                        if (event.values.isNotEmpty()) {
                            val v = event.values[0]
                            if (v.isFinite() && v >= 0f) lux = v
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val sampling = accelSensor != null || magSensor != null || lightSensor != null
        if (sampling) {
            accelSensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
            magSensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
            lightSensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        }

        val windowMs = 500L
        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - start < windowMs) {
            delay(16L)
        }

        if (sampling) {
            sm.unregisterListener(listener)
        }

        val magnitudes = accelSamples.map { (x, y, z) ->
            sqrt(x * x + y * y + z * z)
        }
        val motionVar = if (magnitudes.size >= 3) {
            val mean = magnitudes.average().toFloat()
            magnitudes.map { (it - mean) * (it - mean) }.average().toFloat()
        } else {
            null
        }

        val azimuth = computeAzimuthDeg(accelSamples.lastOrNull(), lastMag)

        val batteryPct = readBatteryPercent(context)

        HardwareVibeSnapshot(
            luxLevel = lux,
            motionVariance = motionVar?.takeIf { it.isFinite() },
            compassAzimuth = azimuth?.takeIf { it.isFinite() },
            batteryLevel = batteryPct,
        )
    }
}

private fun computeAzimuthDeg(
    accel: Triple<Float, Float, Float>?,
    mag: Triple<Float, Float, Float>?,
): Float? {
    if (accel == null || mag == null) return null
    val g = FloatArray(3) { i ->
        when (i) {
            0 -> accel.first
            1 -> accel.second
            else -> accel.third
        }
    }
    val m = FloatArray(3) { i ->
        when (i) {
            0 -> mag.first
            1 -> mag.second
            else -> mag.third
        }
    }
    val r = FloatArray(9)
    val i = FloatArray(9)
    if (!SensorManager.getRotationMatrix(r, i, g, m)) return null
    val orient = FloatArray(3)
    SensorManager.getOrientation(r, orient)
    val radAz = orient[0]
    if (!radAz.isFinite()) return null
    val deg = Math.toDegrees(radAz.toDouble()).toFloat()
    var norm = deg % 360f
    if (norm < 0f) norm += 360f
    return norm
}

private fun readBatteryPercent(context: Context): Int? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        val cap = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (cap in 0..100) cap else null
    } else {
        null
    }
}
