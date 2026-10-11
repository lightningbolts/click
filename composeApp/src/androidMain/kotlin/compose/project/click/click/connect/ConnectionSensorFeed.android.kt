package compose.project.click.click.connect

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import compose.project.click.click.utils.locationAppContextOrNull

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Platform values of one [Location], validated in common code. */
internal fun Location.toLocationFix(
    fullAccuracy: Boolean,
    nowMs: Long = System.currentTimeMillis(),
    maximumAgeMs: Long = ConnectionLocationQuality.MAXIMUM_SEED_AGE_MS,
): LocationFix? {
    val vertical =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasVerticalAccuracy()) {
            verticalAccuracyMeters.toDouble()
        } else {
            null
        }
    val msl =
        if (Build.VERSION.SDK_INT >= 34 && hasMslAltitude()) mslAltitudeMeters else null
    val mock =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isMock
        } else {
            @Suppress("DEPRECATION")
            isFromMockProvider
        }
    val speedAcc =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasSpeedAccuracy()) {
            speedAccuracyMetersPerSecond.toDouble()
        } else {
            null
        }
    val bearingAcc =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasBearingAccuracy()) {
            bearingAccuracyDegrees.toDouble()
        } else {
            null
        }
    // Location.time is the wall-clock fix time; elapsedRealtime corrects a skewed fix clock.
    val observedAt =
        nowMs - (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos).coerceAtLeast(0L) / 1_000_000L
    return LocationFix.validated(
        latitude = latitude,
        longitude = longitude,
        horizontalAccuracyMeters = if (hasAccuracy()) accuracy.toDouble() else null,
        observedAtMs = observedAt,
        nowMs = nowMs,
        maximumAgeMs = maximumAgeMs,
        verticalAccuracyMeters = vertical,
        altitudeMslMeters = msl,
        ellipsoidalAltitudeMeters = if (hasAltitude()) altitude else null,
        isFullAccuracy = fullAccuracy,
        isSimulated = mock,
        speedMetersPerSecond = if (hasSpeed()) speed.toDouble() else null,
        speedAccuracyMetersPerSecond = speedAcc,
        courseDegrees = if (hasBearing()) bearing.toDouble() else null,
        courseAccuracyDegrees = bearingAcc,
    )
}

/**
 * Fused high-accuracy location (1 s) plus `TYPE_PRESSURE`. Never prompts: location starts only
 * with permission already granted and a provider on; nothing outlives [stopLocation]/[stopPressure].
 */
internal class AndroidConnectionSensorFeed(
    private val context: Context?,
) : ConnectionSensorFeed {
    private var locationCallback: LocationCallback? = null
    private var pressureListener: SensorEventListener? = null

    private val sensorManager: SensorManager? get() = context?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    override val hasPressureSensor: Boolean get() = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null

    @SuppressLint("MissingPermission")
    override fun startLocation(onFix: (LocationFix) -> Unit): Boolean {
        val ctx = context ?: return false
        val fine = ctx.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ctx.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!fine && !coarse) return false
        val manager = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val enabled =
            manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                manager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        if (!enabled) return false
        if (locationCallback != null) return true
        val client = LocationServices.getFusedLocationProviderClient(ctx)
        // A fresh cached fix may seed the buffer, tagged with its real time.
        runCatching {
            client.lastLocation.addOnSuccessListener { last -> last?.toLocationFix(fine)?.let(onFix) }
        }
        val callback =
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    for (loc in result.locations) loc.toLocationFix(fine)?.let(onFix)
                }
            }
        val request =
            LocationRequest
                .Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
                .setMinUpdateIntervalMillis(500L)
                .setWaitForAccurateLocation(false)
                .build()
        return runCatching {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            locationCallback = callback
            true
        }.getOrDefault(false)
    }

    override fun startPressure(onSample: (PressureSample) -> Unit): Boolean {
        val sm = sensorManager ?: return false
        val sensor = sm.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return false
        if (pressureListener != null) return true
        val listener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val hpa =
                        event.values
                            .firstOrNull()
                            ?.takeIf { it > 0f }
                            ?.toDouble() ?: return
                    val now = System.currentTimeMillis()
                    val observedAt = now - (SystemClock.elapsedRealtimeNanos() - event.timestamp).coerceAtLeast(0L) / 1_000_000L
                    onSample(PressureSample(pressureHpa = hpa, observedAtMs = observedAt))
                }

                override fun onAccuracyChanged(
                    sensor: Sensor?,
                    accuracy: Int,
                ) = Unit
            }
        val ok = sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        if (ok) pressureListener = listener
        return ok
    }

    override fun stopLocation() {
        val callback = locationCallback ?: return
        locationCallback = null
        val ctx = context ?: return
        runCatching { LocationServices.getFusedLocationProviderClient(ctx).removeLocationUpdates(callback) }
    }

    override fun stopPressure() {
        val listener = pressureListener ?: return
        pressureListener = null
        runCatching { sensorManager?.unregisterListener(listener) }
    }
}

actual fun createConnectionSensorFeed(): ConnectionSensorFeed = AndroidConnectionSensorFeed(locationAppContextOrNull())

actual fun currentConnectionDeviceContext(preciseLocationAuthorized: Boolean): ConnectionDeviceContext? {
    val ctx = locationAppContextOrNull() ?: return null
    return runCatching {
        val battery = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val power = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val thermal =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && power != null) {
                when (power.currentThermalStatus) {
                    PowerManager.THERMAL_STATUS_NONE, PowerManager.THERMAL_STATUS_LIGHT -> "nominal"
                    PowerManager.THERMAL_STATUS_MODERATE -> "fair"
                    PowerManager.THERMAL_STATUS_SEVERE -> "serious"
                    PowerManager.THERMAL_STATUS_CRITICAL,
                    PowerManager.THERMAL_STATUS_EMERGENCY,
                    PowerManager.THERMAL_STATUS_SHUTDOWN,
                    -> "critical"
                    else -> "unknown"
                }
            } else {
                "unknown"
            }
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        ConnectionDeviceContext(
            batteryPercent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null,
            lowPowerMode = power?.isPowerSaveMode == true,
            thermalState = thermal,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            osVersion = "Android ${Build.VERSION.RELEASE}",
            charging =
                if (status < 0) {
                    null
                } else {
                    status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                },
            hasBarometer = sm?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null,
            hasDeviceMotion = sm?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null,
            hasMagnetometer = sm?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null,
            preciseLocationAuthorized = preciseLocationAuthorized,
        )
    }.getOrNull()
}

/** True when fine (precise) location was granted. */
actual fun hasPreciseLocationPermission(): Boolean =
    locationAppContextOrNull()?.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) == true
