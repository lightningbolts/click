package compose.project.click.click.calls

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import compose.project.click.click.MainActivity

/**
 * Keeps microphone/camera capture legal while a LiveKit call is up (Android 14+ FGS types).
 */
class CallForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val videoEnabled = intent?.getBooleanExtra(EXTRA_VIDEO_ENABLED, false) == true
        ensureChannel()
        val notification = buildNotification()
        val types = foregroundServiceTypes(videoEnabled)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            types,
        )
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun foregroundServiceTypes(videoEnabled: Boolean): Int {
        var types = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            val cameraGranted =
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
            if (videoEnabled && cameraGranted) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
        }
        return types
    }

    private fun buildNotification(): Notification {
        val launchIntent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        val contentIntent =
            PendingIntent.getActivity(
                this,
                NOTIFICATION_ID,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Click call")
            .setContentText("Call in progress")
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "In-call",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Ongoing Click voice and video calls"
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    companion object {
        const val EXTRA_VIDEO_ENABLED = "extra_video_enabled"
        const val NOTIFICATION_ID = 41001
        const val CHANNEL_ID = "click_in_call"

        fun start(context: Context, videoEnabled: Boolean) {
            val intent =
                Intent(context, CallForegroundService::class.java).apply {
                    putExtra(EXTRA_VIDEO_ENABLED, videoEnabled)
                }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (error: Exception) {
                android.util.Log.e(TAG, "Unable to start call foreground service", error)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, CallForegroundService::class.java))
            } catch (error: Exception) {
                android.util.Log.e(TAG, "Unable to stop call foreground service", error)
            }
        }
    }
}

private const val TAG = "CallForegroundService"
