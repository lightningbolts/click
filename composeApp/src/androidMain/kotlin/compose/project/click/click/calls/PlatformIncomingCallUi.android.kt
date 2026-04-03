package compose.project.click.click.calls

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import compose.project.click.click.MainActivity

private const val TAG = "PlatformIncomingCallUi"
private const val CLICK_CALLS_CHANNEL_ID = "click_calls_v2"
private const val CLICK_CALLS_CHANNEL_NAME = "Incoming calls"

actual object PlatformIncomingCallUi {
    actual fun showIncomingCall(invite: CallInvite) {
        val context = AndroidCallRuntime.appContext() ?: run {
            Log.e(TAG, "showIncomingCall: no app context available")
            return
        }
        ensureIncomingCallChannel(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            invite.callId.hashCode(),
            MainActivity.createIncomingCallIntent(context, MainActivity.ACTION_VIEW_CALL, invite),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val answerIntent = PendingIntent.getActivity(
            context,
            invite.callId.hashCode() + 1,
            MainActivity.createIncomingCallIntent(context, MainActivity.ACTION_ACCEPT_CALL, invite),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val declineIntent = PendingIntent.getActivity(
            context,
            invite.callId.hashCode() + 2,
            MainActivity.createIncomingCallIntent(context, MainActivity.ACTION_DECLINE_CALL, invite),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val person = Person.Builder().setName(invite.callerName).build()

        val notification = NotificationCompat.Builder(context, CLICK_CALLS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(invite.callerName)
            .setContentText(if (invite.videoEnabled) "Incoming video call" else "Incoming voice call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(contentIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, declineIntent, answerIntent))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId(invite.callId), notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing POST_NOTIFICATIONS permission", e)
        }

        triggerVibration(context)
    }

    actual fun dismissIncomingCall(callId: String, reason: String?) {
        val context = AndroidCallRuntime.appContext() ?: return
        NotificationManagerCompat.from(context).cancel(notificationId(callId))
    }

    private fun notificationId(callId: String): Int = callId.hashCode()

    private fun triggerVibration(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 1000, 500, 1000, 500, 1000), 0)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createWaveform(pattern, 0)
                    )
                } else {
                    vibrator?.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to start vibration: ${e.message}")
        }
    }

    private fun ensureIncomingCallChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return

        val existingChannel = notificationManager.getNotificationChannel(CLICK_CALLS_CHANNEL_ID)
        if (existingChannel != null) return

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .build()

        notificationManager.createNotificationChannel(
            NotificationChannel(
                CLICK_CALLS_CHANNEL_ID,
                CLICK_CALLS_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Incoming call alerts"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(ringtoneUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                setBypassDnd(true)
            }
        )
    }
}