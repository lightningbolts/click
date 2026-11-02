package compose.project.click.click.connect

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import compose.project.click.click.data.api.ApiClient
import compose.project.click.click.utils.locationAppContextOrNull
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent

/** Fused `PRIORITY_HIGH_ACCURACY` at 1 s plus `TYPE_LINEAR_ACCELERATION` at ~10 Hz. */
internal class AndroidStillLocationFeed(
    private val context: Context?,
) : StillLocationFeed {
    private var callback: LocationCallback? = null
    private var accel: SensorEventListener? = null

    @SuppressLint("MissingPermission")
    override fun start(
        onFix: (LocationFix) -> Unit,
        onLinearAcceleration: (x: Double, y: Double, z: Double) -> Unit,
    ): Boolean {
        val ctx = context ?: return false
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return false
        val cb =
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    for (loc in result.locations) loc.toLocationFix(fine)?.let(onFix)
                }
            }
        val request =
            LocationRequest
                .Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
                .setMinUpdateIntervalMillis(1_000L)
                .build()
        val ok =
            runCatching {
                LocationServices.getFusedLocationProviderClient(ctx).requestLocationUpdates(request, cb, Looper.getMainLooper())
                true
            }.getOrDefault(false)
        if (!ok) return false
        callback = cb
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sm?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let { sensor ->
            val listener =
                object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val v = event.values
                        if (v.size >= 3) onLinearAcceleration(v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
                    }

                    override fun onAccuracyChanged(
                        sensor: Sensor?,
                        accuracy: Int,
                    ) = Unit
                }
            // 100 ms ≈ 10 Hz, matching the detector's samples per second.
            if (sm.registerListener(listener, sensor, 100_000)) accel = listener
        }
        return true
    }

    override fun stop() {
        val ctx = context
        callback?.let { cb ->
            callback = null
            if (ctx != null) runCatching { LocationServices.getFusedLocationProviderClient(ctx).removeLocationUpdates(cb) }
        }
        accel?.let { listener ->
            accel = null
            runCatching { (ctx?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.unregisterListener(listener) }
        }
    }
}

actual fun createStillLocationFeed(): StillLocationFeed = AndroidStillLocationFeed(locationAppContextOrNull())

/** Sends one encounter follow-up body; failures are silent (the connection is unaffected). */
class EncounterFollowUpWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val path = inputData.getString(KEY_PATH)?.takeIf { it.startsWith("/api/connections/encounter-") } ?: return Result.success()
        val body = inputData.getString(KEY_BODY) ?: return Result.success()
        return runCatching {
            val response =
                ApiClient().clickWebClient.post("${ApiClient.clickWebAuthOrigin}$path") {
                    setBody(TextContent(body, ContentType.Application.Json))
                }
            // 5xx and network errors retry within WorkManager's backoff; 4xx (window passed) never do.
            if (response.status.value >= 500) Result.retry() else Result.success()
        }.getOrElse { if (runAttemptCount < 3) Result.retry() else Result.success() }
    }

    companion object {
        const val KEY_PATH = "path"
        const val KEY_BODY = "body"
    }
}

actual fun enqueueEncounterFollowUpPost(
    path: String,
    body: String,
) {
    val ctx = locationAppContextOrNull() ?: return
    runCatching {
        val builder =
            OneTimeWorkRequestBuilder<EncounterFollowUpWorker>()
                .setInputData(workDataOf(EncounterFollowUpWorker.KEY_PATH to path, EncounterFollowUpWorker.KEY_BODY to body))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        // Below API 31 expedited work runs as a foreground service (needs a notification); a
        // plain request is fine there since the server window is 15 minutes.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        WorkManager.getInstance(ctx).enqueue(builder.build())
    }
}
