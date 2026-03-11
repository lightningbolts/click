package compose.project.click.click.calls

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import compose.project.click.click.MainActivity

private const val CLICK_CALLS_CHANNEL_ID = "click_calls"
private const val CLICK_CALLS_CHANNEL_NAME = "Click calls"

actual object PlatformIncomingCallUi {
    actual fun showIncomingCall(invite: CallInvite) {
        val context = AndroidCallRuntime.appContext() ?: return
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
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, declineIntent, answerIntent))
            .build()

        NotificationManagerCompat.from(context).notify(notificationId(invite.callId), notification)
    }

    actual fun dismissIncomingCall(callId: String, reason: String?) {
        val context = AndroidCallRuntime.appContext() ?: return
        NotificationManagerCompat.from(context).cancel(notificationId(callId))
    }

    private fun notificationId(callId: String): Int = callId.hashCode()

    private fun ensureIncomingCallChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        if (notificationManager.getNotificationChannel(CLICK_CALLS_CHANNEL_ID) != null) return

        notificationManager.createNotificationChannel(
            NotificationChannel(
                CLICK_CALLS_CHANNEL_ID,
                CLICK_CALLS_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Incoming call alerts"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
    }
}