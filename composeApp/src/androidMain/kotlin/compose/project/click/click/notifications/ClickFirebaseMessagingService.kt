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
import compose.project.click.click.MainActivity
import compose.project.click.click.calls.CallInvite
import compose.project.click.click.calls.PlatformIncomingCallUi
import compose.project.click.click.crypto.MessageCrypto

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

        val type = message.data["type"]
        if (type == "incoming_call") {
            if (!NotificationRuntimeState.getNotificationPreferences().callNotificationsEnabled) {
                return
            }

            if (AndroidPushNotificationRuntime.isAppInForeground()) {
                return
            }

            message.toIncomingCallInvite()?.let { invite ->
                PlatformIncomingCallUi.showIncomingCall(invite)
            }
            return
        }

        if (!NotificationRuntimeState.getNotificationPreferences().messageNotificationsEnabled) {
            return
        }

        val activeChatId = NotificationRuntimeState.getActiveChatId()
        if (!activeChatId.isNullOrBlank() && activeChatId == message.data["chat_id"]) {
            return
        }

        val senderName = message.data["sender_name"] ?: "Someone"
        val connectionId = message.data["connection_id"] ?: ""
        val body = decryptMessagePreview(
            encryptedContent = message.data["encrypted_content"] ?: "",
            connectionId = connectionId,
            senderUserId = message.data["sender_user_id"] ?: "",
            recipientUserId = message.data["recipient_user_id"] ?: "",
            fallback = message.data["body"] ?: "Open Click to view it"
        )

        val launchIntent = if (connectionId.isNotBlank()) {
            MainActivity.createChatDeepLinkIntent(this, connectionId)
        } else {
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            } ?: return
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            connectionId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CLICK_MESSAGES_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(senderName)
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

    private fun decryptMessagePreview(
        encryptedContent: String,
        connectionId: String,
        senderUserId: String,
        recipientUserId: String,
        fallback: String,
    ): String {
        if (encryptedContent.isBlank()) return fallback

        if (!MessageCrypto.isEncrypted(encryptedContent)) {
            return encryptedContent.take(120)
        }

        if (connectionId.isBlank() || senderUserId.isBlank() || recipientUserId.isBlank()) {
            return fallback
        }

        return try {
            val keys = MessageCrypto.deriveKeysForConnection(connectionId, listOf(senderUserId, recipientUserId))
            val decrypted = MessageCrypto.decryptContent(encryptedContent, keys)
            if (MessageCrypto.isEncrypted(decrypted)) fallback else decrypted.take(120)
        } catch (_: Exception) {
            fallback
        }
    }
}

private fun RemoteMessage.toIncomingCallInvite(): CallInvite? {
    val callId = data["call_id"] ?: return null
    val connectionId = data["connection_id"] ?: return null
    val roomName = data["room_name"] ?: return null
    val callerId = data["caller_id"] ?: return null
    val callerName = data["caller_name"] ?: return null
    val calleeId = data["callee_id"] ?: return null
    val calleeName = data["callee_name"] ?: return null

    return CallInvite(
        callId = callId,
        connectionId = connectionId,
        roomName = roomName,
        callerId = callerId,
        callerName = callerName,
        calleeId = calleeId,
        calleeName = calleeName,
        videoEnabled = data["video_enabled"]?.toBooleanStrictOrNull() ?: false,
        createdAt = data["created_at"]?.toLongOrNull() ?: 0L,
    )
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