package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.chat.PendingSend // pragma: allowlist secret
import compose.project.click.click.data.chat.PendingSendStore // pragma: allowlist secret
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.MessageDeliveryState // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/*
 * Persisted outbox (04 §10, iOS `PendingSendStore`): unconfirmed text sends survive a restart,
 * reappear as optimistic rows when their chat opens, resend when the network returns, and keep
 * their `client_message_id` across retries.
 */

private val outboxJson = Json { ignoreUnknownKeys = true }
private val outboxFlushMutex = Mutex()

/** Pure: the optimistic rows [pending] adds to [existing] (skips ones already shown or confirmed). */
fun pendingSendRows(
    existing: List<MessageWithUser>,
    pending: List<PendingSend>,
    sender: User,
): List<MessageWithUser> {
    val shownIds = existing.mapTo(HashSet()) { it.message.id }
    return pending
        .filter { it.tempId !in shownIds }
        .map { item ->
            MessageWithUser(
                message =
                    Message(
                        id = item.tempId,
                        user_id = item.userId,
                        content = item.content,
                        timeCreated = item.localSentAtMs,
                        isRead = false,
                        messageType = ChatMessageType.TEXT,
                        metadata = item.metadataJson?.let { runCatching { outboxJson.parseToJsonElement(it) }.getOrNull() },
                        localSentAt = item.localSentAtMs,
                        deliveryState = if (item.failed) MessageDeliveryState.ERROR else MessageDeliveryState.PENDING,
                    ),
                user = sender,
                isSent = true,
            )
        }
}

internal fun JsonElement?.toOutboxJson(): String? = this?.let { outboxJson.encodeToString(JsonElement.serializer(), it) }

/** Started from `init`: loads the outbox per user, restores rows into the open chat, flushes when online. */
internal fun ChatViewModel.startOutbox() {
    viewModelScope.launch {
        _currentUserId.collect { userId ->
            if (userId.isNullOrBlank()) return@collect
            PendingSendStore.load(tokenStorage, userId)
            flushOutbox()
        }
    }
    viewModelScope.launch {
        combine(_chatMessagesState, PendingSendStore.items) { state, _ -> state }
            .collect { restoreOutboxRows() }
    }
    viewModelScope.launch {
        connectivityMonitor.isOnline
            .filter { it }
            .collect { flushOutbox() }
    }
}

private fun ChatViewModel.restoreOutboxRows() {
    val state = _chatMessagesState.value as? ChatMessagesState.Success ?: return
    val userId = _currentUserId.value ?: return
    val threadKey = currentConnectionId ?: return
    val pending = PendingSendStore.forThread(threadKey, userId)
    if (pending.isEmpty()) return
    val sender = AppDataManager.currentUser.value?.takeIf { it.id == userId } ?: User(id = userId, name = "You", createdAt = 0L)
    val extra = pendingSendRows(state.messages, pending, sender)
    if (extra.isEmpty()) return
    _chatMessagesState.value = state.copy(messages = (state.messages + extra).sortedBy { it.message.timeCreated })
}

/** Resends every unconfirmed (not failed) send, oldest first, reusing its client message id. */
internal fun ChatViewModel.flushOutbox() {
    viewModelScope.launch {
        if (!connectivityMonitor.isOnline.value) return@launch
        outboxFlushMutex.withLock {
            val userId = _currentUserId.value ?: return@launch
            val queued =
                PendingSendStore.items.value
                    .filter { it.userId == userId && !it.failed }
                    .sortedBy { it.localSentAtMs }
            for (item in queued) {
                // A send still in flight in this process owns its row.
                if (item.tempId in inFlightOutboxIds) continue
                resendOutboxItem(item)
            }
        }
    }
}

/** Sends [item] again; on success swaps in the server row, on failure marks it failed for Retry. */
internal suspend fun ChatViewModel.resendOutboxItem(item: PendingSend): Boolean {
    inFlightOutboxIds += item.tempId
    try {
        val sent =
            runCatching {
                chatRepository.sendMessage(
                    chatId = item.apiChatId,
                    userId = item.userId,
                    content = item.content,
                    messageType = ChatMessageType.TEXT,
                    metadata = item.metadataJson?.let { outboxJson.parseToJsonElement(it) },
                    clientLocalSentAtMs = item.localSentAtMs,
                    connectionId = item.sendConnectionId,
                    clientMessageId = item.clientMessageId,
                )
            }.getOrNull()
        if (sent == null) {
            if (connectivityMonitor.isOnline.value) {
                PendingSendStore.markFailed(tokenStorage, item.tempId)
                markOptimisticSendFailed(item.tempId)
            }
            return false
        }
        PendingSendStore.remove(tokenStorage, item.tempId)
        if (currentConnectionId == item.threadKey) {
            val sender = AppDataManager.currentUser.value ?: User(id = item.userId, name = "You", createdAt = 0L)
            applyInsertedMessage(sent, sender, item.userId, optimisticTempId = item.tempId)
        }
        return true
    } finally {
        inFlightOutboxIds -= item.tempId
    }
}
