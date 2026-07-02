package compose.project.click.click.notifications

import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.models.ChatMessageType
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.MessageDeliveryState
import compose.project.click.click.data.models.previewLabel
import compose.project.click.click.data.repository.ChatSessionCaches
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.datetime.Clock

/**
 * Applies push-notification chat payloads to in-memory inbox state so connection-list
 * previews update before the user opens the thread.
 */
object ChatPushInboxBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pushWarmMessages = mutableMapOf<String, Message>()
    private val pushWarmConnectionIds = mutableMapOf<String, String>()

    private val _inboxPushEvents = MutableSharedFlow<Pair<String, Message>>(extraBufferCapacity = 16)
    val inboxPushEvents: SharedFlow<Pair<String, Message>> = _inboxPushEvents.asSharedFlow()

    fun consumeWarmMessage(connectionId: String): Message? =
        pushWarmMessages.remove(connectionId)

    fun peekWarmMessage(connectionId: String): Message? =
        pushWarmMessages[connectionId]

    fun peekWarmMessageForThread(threadId: String): Message? =
        pushWarmMessages[threadId]

    /** Resolve a connection/list key when navigation only has a `chats.id` from push/deep link. */
    fun resolveConnectionIdForThread(threadId: String): String? =
        pushWarmConnectionIds[threadId]?.takeIf { it.isNotBlank() }

    fun applyChatMessagePush(
        chatId: String,
        connectionId: String,
        senderUserId: String,
        previewText: String,
        messageId: String? = null,
        timeCreated: Long = Clock.System.now().toEpochMilliseconds(),
        messageType: String = ChatMessageType.TEXT,
    ) {
        val listKey = connectionId.ifBlank { chatId }.trim()
        if (listKey.isBlank()) return

        if (chatId.isNotBlank() && connectionId.isNotBlank()) {
            scope.launch { ChatSessionCaches.seedConnectionRouting(chatId, connectionId) }
        }

        AppDataManager.bumpInboxFromPush()

        val body = previewText.trim().ifBlank { "New message" }
        val message = Message(
            id = messageId?.takeIf { it.isNotBlank() } ?: "push-${listKey}-${timeCreated}",
            user_id = senderUserId.ifBlank { "unknown" },
            content = body,
            timeCreated = timeCreated,
            messageType = messageType,
            deliveryState = MessageDeliveryState.SENT,
        )

        pushWarmMessages[listKey] = message
        if (chatId.isNotBlank() && chatId != listKey) {
            pushWarmMessages[chatId] = message
        }
        if (connectionId.isNotBlank() && connectionId != listKey && connectionId != chatId) {
            pushWarmMessages[connectionId] = message
        }
        val resolvedConnectionId = connectionId.ifBlank { listKey }
        if (chatId.isNotBlank()) {
            pushWarmConnectionIds[chatId] = resolvedConnectionId
        }
        pushWarmConnectionIds[resolvedConnectionId] = resolvedConnectionId
        ChatSessionCaches.mergeTimeline(listKey, message)
        AppDataManager.updateConnectionChatActivity(listKey, timeCreated, message)
        AppDataManager.updateInboxFeedChatActivityFromPush(listKey, message)
        _inboxPushEvents.tryEmit(listKey to message)
    }

    fun previewLabelForPush(connectionId: String): String? =
        pushWarmMessages[connectionId]?.previewLabel()
}
