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
import androidx.core.app.Person
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import compose.project.click.click.MainActivity // pragma: allowlist secret
import compose.project.click.click.crypto.MessageCrypto // pragma: allowlist secret
import compose.project.click.click.crypto.PushPreviewKeyStore
import compose.project.click.click.data.ChatMuteStore
import compose.project.click.click.data.storage.createTokenStorage

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
        // Calls and Seed-a-Room teasers (spec §56.2.2) are retired; an older server may still send them.
        if (type == "incoming_call" || type == "event_teaser") {
            return
        }
        val momentType = type?.takeIf(RelationshipMomentPush::isMoment)
        val allowed =
            when {
                momentType == null -> allowedForLegacyType(type, prefs)
                RelationshipMomentPush.usesRelationshipMomentsPreference(momentType) -> prefs.reconnectNudgeNotificationsEnabled
                else -> prefs.messageNotificationsEnabled
            }
        if (!allowed) {
            return
        }
        if (momentType != null) {
            showRelationshipMomentPush(momentType, message)
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
            val launchIntent = launchIntentFor(PushRoutes.route(message.data)) ?: return
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

        // Event reminders, nudges, archive warnings and availability matches carry server text.
        if (type != null && type in PushRoutes.serverTextTypes) {
            val title = message.data["title"] ?: message.notification?.title ?: "Click"
            val body = message.data["body"] ?: message.notification?.body ?: "Open Click"
            val launchIntent = launchIntentFor(PushRoutes.route(message.data)) ?: return
            val tag =
                listOf("beacon_id", "connection_id", "peer_user_id")
                    .firstNotNullOfOrNull { key -> message.data[key]?.takeIf { it.isNotBlank() } }
                    ?: type
            showSimplePush(tag = "$type:$tag", title = title, body = body, launchIntent = launchIntent)
            return
        }

        // Defense in depth: the push function enforces mutes, but drop a muted chat here too.
        val pushChatId = message.data["chat_id"] ?: message.data["hub_id"]
        if (pushChatId != null && isChatMutedLocally(pushChatId)) {
            return
        }

        val activeChatId = NotificationRuntimeState.getActiveChatId()
        if (!activeChatId.isNullOrBlank() && activeChatId == message.data["chat_id"]) {
            return
        }

        val chatId = message.data["chat_id"] ?: ""
        val senderName = message.data["sender_name"] ?: "Someone"
        val connectionId = message.data["connection_id"] ?: ""
        // Never fall back to server `preview_text`: see chatPushBody.
        val body =
            chatPushBody(
                decryptedPreview =
                    decryptMessagePreview(
                        encryptedContent = message.data["encrypted_content"] ?: "",
                        connectionId = connectionId,
                        senderUserId = message.data["sender_user_id"] ?: "",
                        recipientUserId = message.data["recipient_user_id"] ?: "",
                    ),
                messageType = message.data["message_type"],
            )

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
        val launchIntent = launchIntentFor(PushRoutes.route(message.data)) ?: return

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                deepLinkId.hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notifyTag =
            if (deepLinkId.isNotBlank()) {
                chatNotificationTag(deepLinkId)
            } else {
                message.messageId?.let { chatNotificationTag(it) }
            }

        // One conversation-style notification per chat: new messages append (iOS threads by
        // chat_id); group pushes are titled with the group name.
        val groupName = message.data["group_name"]?.trim()?.takeIf { it.isNotEmpty() }
        val style =
            (notifyTag?.let { existingMessagingStyle(it) } ?: NotificationCompat.MessagingStyle(Person.Builder().setName("You").build()))
                .setConversationTitle(groupName)
                .setGroupConversation(groupName != null)
                .addMessage(body, System.currentTimeMillis(), Person.Builder().setName(senderName).build())

        val notification =
            NotificationCompat
                .Builder(this, CLICK_MESSAGES_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(groupName ?: senderName)
                .setContentText(body)
                .setStyle(style)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
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
        if (notifyTag != null) {
            NotificationManagerCompat.from(this).notify(notifyTag, 0, notification)
        } else {
            NotificationManagerCompat.from(this).notify(body.hashCode(), notification)
        }
    }

    /** The messaging style of this chat's notification that is still showing, so we can append. */
    private fun existingMessagingStyle(tag: String): NotificationCompat.MessagingStyle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val manager = getSystemService(NotificationManager::class.java) ?: return null
        val active = runCatching { manager.activeNotifications }.getOrNull() ?: return null
        val existing = active.firstOrNull { it.tag == tag && it.id == 0 }?.notification ?: return null
        return NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(existing)
    }

    private fun isChatMutedLocally(chatId: String): Boolean =
        ChatMuteStore.isMuted(chatId) ||
            runCatching {
                kotlinx.coroutines.runBlocking {
                    val persisted =
                        ChatMuteStore.loadPersisted(
                            compose.project.click.click.data.storage
                                .createTokenStorage(),
                        )
                    ChatMuteStore.isMuted(persisted, chatId, System.currentTimeMillis())
                }
            }.getOrDefault(false)

    private fun allowedForLegacyType(
        type: String?,
        prefs: LocalNotificationPreferences,
    ): Boolean =
        when (type) {
            "event_reminder" -> prefs.eventReminderNotificationsEnabled
            "reconnect_nudge", "shared_upcoming_event" -> prefs.reconnectNudgeNotificationsEnabled
            "availability_match" -> prefs.availabilityMatchNotificationsEnabled
            "hub_message" -> prefs.hubMessageNotificationsEnabled
            else -> prefs.messageNotificationsEnabled
        }

    /** Server-written title/body; tap opens the peer profile, the chat, or the group chat. */
    private fun showRelationshipMomentPush(
        type: String,
        message: RemoteMessage,
    ) {
        val title = message.data["title"] ?: message.notification?.title ?: "Click"
        val body = message.data["body"] ?: message.notification?.body ?: "Open Click"
        val launchIntent = launchIntentFor(PushRoutes.route(message.data + ("type" to type))) ?: return
        val tag = message.data["nudge_id"]?.takeIf { it.isNotBlank() } ?: "$type:${message.data["connection_id"].orEmpty()}"
        showSimplePush(tag = tag, title = title, body = body, launchIntent = launchIntent)
    }

    /** The activity intent for a [PushRoute]; null only when the app can't be launched at all. */
    private fun launchIntentFor(route: PushRoute): Intent? =
        when (route) {
            is PushRoute.DirectChat ->
                MainActivity.createChatDeepLinkIntent(this, chatId = route.chatId, connectionId = route.connectionId)
            is PushRoute.GroupChat -> MainActivity.createChatDeepLinkIntent(this, chatId = route.chatId)
            is PushRoute.Event -> MainActivity.createEventDeepLinkIntent(this, route.beaconId)
            is PushRoute.Hub -> MainActivity.createHubDeepLinkIntent(this, route.hubId)
            is PushRoute.Profile -> MainActivity.createProfileDeepLinkIntent(this, route.userId)
            PushRoute.Connections -> MainActivity.createConnectionsIntent(this)
            PushRoute.OpenApp ->
                packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
        }

    /** Local v1 decrypt only; returns null when the preview can't be produced on-device. */
    private fun decryptMessagePreview(
        encryptedContent: String,
        connectionId: String,
        senderUserId: String,
        recipientUserId: String,
    ): String? {
        if (encryptedContent.isBlank()) return null
        if (!MessageCrypto.isAnyE2eeWireContent(encryptedContent)) return encryptedContent
        if (MessageCrypto.isV2Encrypted(encryptedContent)) {
            return runCatching {
                kotlinx.coroutines.runBlocking {
                    PushPreviewKeyStore.decryptPreview(createTokenStorage(), encryptedContent)
                }
            }.getOrNull()
        }
        if (!MessageCrypto.isEncrypted(encryptedContent)) return null
        if (connectionId.isBlank() || senderUserId.isBlank() || recipientUserId.isBlank()) return null

        return try {
            val keys = MessageCrypto.deriveKeysForConnection(connectionId, listOf(senderUserId, recipientUserId))
            MessageCrypto.decryptContent(encryptedContent, keys)
        } catch (_: Exception) {
            null
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
