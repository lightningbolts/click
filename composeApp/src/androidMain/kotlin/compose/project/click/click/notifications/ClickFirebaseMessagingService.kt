package compose.project.click.click.notifications // pragma: allowlist secret

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
import compose.project.click.click.MainActivity // pragma: allowlist secret
import compose.project.click.click.crypto.MessageCrypto // pragma: allowlist secret

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
        val prefs = NotificationRuntimeState.getNotificationPreferences()
        if (type == "incoming_call") {
            return
        }
        val allowed =
            when (type) {
                "event_reminder" -> prefs.eventReminderNotificationsEnabled
                "event_teaser" -> prefs.eventTeaserNotificationsEnabled
                "reconnect_nudge", "shared_upcoming_event" -> prefs.reconnectNudgeNotificationsEnabled
                "availability_match" -> prefs.availabilityMatchNotificationsEnabled
                "hub_message" -> prefs.hubMessageNotificationsEnabled
                else -> prefs.messageNotificationsEnabled
            }
        if (!allowed) {
            return
        }
        if (type == "disposable_reveal") {
            val connectionId = message.data["connection_id"] ?: ""
            val chatId = message.data["chat_id"] ?: ""
            val deepLinkId = connectionId.ifBlank { chatId }
            val title = message.notification?.title ?: "Click Drops"
            val body =
                message.notification?.body
                    ?: "📸 Your Click Drop has been revealed!"
            val launchIntent =
                if (deepLinkId.isNotBlank()) {
                    MainActivity.createChatDeepLinkIntent(
                        context = this,
                        chatId = chatId,
                        connectionId = connectionId,
                    )
                } else {
                    packageManager.getLaunchIntentForPackage(packageName)?.apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    } ?: return
                }
            val pendingIntent =
                PendingIntent.getActivity(
                    this,
                    deepLinkId.hashCode(),
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val notification =
                NotificationCompat
                    .Builder(this, CLICK_MESSAGES_CHANNEL_ID)
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
            NotificationManagerCompat.from(this).notify(
                chatNotificationTag(deepLinkId) ?: "disposable_reveal",
                0,
                notification,
            )
            return
        }

        if (type == "event_teaser" || type == "event_reminder") {
            val beaconId = message.data["beacon_id"].orEmpty()
            val title = message.data["title"] ?: message.notification?.title ?: "Click event"
            val body = message.data["body"] ?: message.notification?.body ?: "Open Click to view this event"
            val launchIntent =
                if (beaconId.isNotBlank()) {
                    MainActivity.createEventDeepLinkIntent(this, beaconId)
                } else {
                    packageManager.getLaunchIntentForPackage(packageName)?.apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    } ?: return
                }
            showSimplePush(
                tag = beaconId.ifBlank { type ?: "event" },
                title = title,
                body = body,
                launchIntent = launchIntent,
            )
            return
        }

        if (type == "reconnect_nudge" || type == "shared_upcoming_event") {
            val connectionId = message.data["connection_id"].orEmpty()
            val beaconId = message.data["beacon_id"].orEmpty()
            val title = message.data["title"] ?: message.notification?.title ?: "Click"
            val body = message.data["body"] ?: message.notification?.body ?: "Open Click"
            val launchIntent =
                if (type == "shared_upcoming_event" && beaconId.isNotBlank()) {
                    MainActivity.createEventDeepLinkIntent(this, beaconId)
                } else if (connectionId.isNotBlank()) {
                    MainActivity.createChatDeepLinkIntent(
                        context = this,
                        chatId = "",
                        connectionId = connectionId,
                    )
                } else {
                    packageManager.getLaunchIntentForPackage(packageName)?.apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    } ?: return
                }
            showSimplePush(
                tag = connectionId.ifBlank { beaconId.ifBlank { type ?: "nudge" } },
                title = title,
                body = body,
                launchIntent = launchIntent,
            )
            return
        }

        val activeChatId = NotificationRuntimeState.getActiveChatId()
        if (!activeChatId.isNullOrBlank() && activeChatId == message.data["chat_id"]) {
            return
        }

        val chatId = message.data["chat_id"] ?: ""
        val senderName = message.data["sender_name"] ?: "Someone"
        val connectionId = message.data["connection_id"] ?: ""
        val previewFromServer = message.data["preview_text"]?.trim()?.takeIf { it.isNotEmpty() }
        val decrypted =
            decryptMessagePreview(
                encryptedContent = message.data["encrypted_content"] ?: "",
                connectionId = connectionId,
                senderUserId = message.data["sender_user_id"] ?: "",
                recipientUserId = message.data["recipient_user_id"] ?: "",
                fallback = "Open Click to view it",
            )
        val fallbackPreview = "Open Click to view it"
        val body = if (decrypted != fallbackPreview) decrypted else previewFromServer ?: decrypted

        if (connectionId.isNotBlank() || chatId.isNotBlank()) {
            ChatPushInboxBridge.applyChatMessagePush(
                chatId = chatId,
                connectionId = connectionId,
                senderUserId = message.data["sender_user_id"] ?: "",
                previewText = body,
                messageId = message.data["message_id"],
            )
        }

        val deepLinkId = chatId.ifBlank { connectionId }
        val launchIntent =
            if (deepLinkId.isNotBlank()) {
                MainActivity.createChatDeepLinkIntent(
                    context = this,
                    chatId = chatId,
                    connectionId = connectionId,
                )
            } else {
                packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                } ?: return
            }

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                deepLinkId.hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat
                .Builder(this, CLICK_MESSAGES_CHANNEL_ID)
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

        val notifyTag =
            if (deepLinkId.isNotBlank()) {
                chatNotificationTag(deepLinkId)
            } else {
                message.messageId?.let { chatNotificationTag(it) }
            }
        if (notifyTag != null) {
            NotificationManagerCompat.from(this).notify(notifyTag, 0, notification)
        } else {
            NotificationManagerCompat.from(this).notify(body.hashCode(), notification)
        }
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

    private fun showSimplePush(
        tag: String,
        title: String,
        body: String,
        launchIntent: Intent,
    ) {
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                tag.hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(this, CLICK_MESSAGES_CHANNEL_ID)
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
        NotificationManagerCompat.from(this).notify(tag, 0, notification)
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
            NotificationManager.IMPORTANCE_HIGH,
        ),
    )
}
