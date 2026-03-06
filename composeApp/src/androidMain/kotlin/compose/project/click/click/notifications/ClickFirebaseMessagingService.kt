package compose.project.click.click.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

private const val CLICK_MESSAGES_CHANNEL_ID = "click_messages"
private const val CLICK_MESSAGES_CHANNEL_NAME = "Click messages"

class ClickFirebaseMessagingService : FirebaseMessagingService() {
    override fun onCreate() {
        super.onCreate()
        initPushNotificationService(applicationContext)
        ensureNotificationChannel(applicationContext)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        initPushNotificationService(applicationContext)
        uploadAndroidPushToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        ensureNotificationChannel(applicationContext)

        val title = message.notification?.title ?: message.data["title"] ?: "New message"
        val body = message.notification?.body ?: message.data["body"] ?: "Open Click to view it"
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        } ?: return

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CLICK_MESSAGES_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        NotificationManagerCompat.from(this).notify(message.messageId?.hashCode() ?: body.hashCode(), notification)
    }
}

private fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
    if (notificationManager.getNotificationChannel(CLICK_MESSAGES_CHANNEL_ID) != null) return

    notificationManager.createNotificationChannel(
        NotificationChannel(
            CLICK_MESSAGES_CHANNEL_ID,
            CLICK_MESSAGES_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )
    )
}