@file:Suppress(
    "ktlint:standard:backing-property-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.MessageDeliveryState // pragma: allowlist secret
import compose.project.click.click.data.models.MessageReaction // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.previewLabel // pragma: allowlist secret
import compose.project.click.click.data.models.withDbDerivedDeliveryState // pragma: allowlist secret
import compose.project.click.click.data.repository.ChatRealtimeEvent // pragma: allowlist secret
import compose.project.click.click.data.repository.MessageChangeEvent // pragma: allowlist secret
import compose.project.click.click.data.repository.ReactionChangeEvent // pragma: allowlist secret
import compose.project.click.click.ui.chat.secureChatImageBitmapCache // pragma: allowlist secret
import compose.project.click.click.util.isPersistedApiChatId
import compose.project.click.click.util.isPersistedApiUuid
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.put

// Subscribe to real-time message updates.
//
// Contract (post-R0.2 refactor):
// - previous `realtimeJob` is cancelled and **awaited** before a new
//   subscription is opened. Without this, detach() and a new subscribe()
//   can run concurrently on the same topic, producing duplicate realtime
//   events and racing echo dedupe (see audit §1 #3).
// - `CancellationException` is never swallowed; the retry loop exits
//   cleanly on scope cancellation (R0.3).
// - the retry loop is bounded by `MESSAGE_SUBSCRIPTION_MAX_ATTEMPTS`
//   (NASA P10: every loop has a fixed upper bound).
internal fun ChatViewModel.subscribeToNewMessages(
    chatId: String,
    userId: String,
) {
    val previousJob = realtimeJob
    val previousSubscription = activeMessageSubscription
    activeMessageSubscription = null
    currentApiChatId = chatId

    realtimeJob =
        viewModelScope.launch {
            // 1) Await previous job cancellation + detach previous subscription
            //    *before* opening a new channel. Serializing here is what
            //    prevents the brief overlap where two channels are subscribed
            //    to the same topic simultaneously.
            if (previousJob != null) {
                previousJob.cancel()
                try {
                    previousJob.join()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // join() of a cancelled job surfaces the cancellation
                    // cause on some platforms; ignore non-cancellation errors.
                }
            }
            if (previousSubscription != null) {
                try {
                    previousSubscription.detach()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // best-effort teardown
                }
            }

            // 2) Open the new subscription with bounded retry.
            var attempt = 0
            while (attempt < MESSAGE_SUBSCRIPTION_MAX_ATTEMPTS && currentApiChatId == chatId) {
                try {
                    val (subscription, changeFlow) = chatRepository.subscribeToMessages(chatId, userId)
                    activeMessageSubscription = subscription

                    changeFlow
                        .onEach { envelope ->
                            when (envelope) {
                                is ChatRealtimeEvent.Message ->
                                    when (val event = envelope.event) {
                                        is MessageChangeEvent.Insert -> {
                                            val vaulted = vaultMessagesForUi(chatId, userId, listOf(event.message)).first()
                                            val user =
                                                resolveMessageUser(vaulted.user_id, chatId)
                                                    ?: User(id = vaulted.user_id, name = null, createdAt = 0L)
                                            applyInsertedMessage(vaulted, user, userId)
                                            if (vaulted.user_id != userId) {
                                                if (vaulted.deliveredAt == null) {
                                                    enqueueInboundDeliveredAck(chatId, userId, listOf(vaulted))
                                                }
                                                val active = _chatMessagesState.value as? ChatMessagesState.Success
                                                val activeApiChatId = active?.chatDetails?.chat?.id
                                                if (active != null && activeApiChatId == chatId) {
                                                    markMessagesReadOptimistically(
                                                        connectionId = active.chatDetails.connection.id,
                                                        chatId = chatId,
                                                        userId = userId,
                                                    )
                                                }
                                            }
                                        }
                                        is MessageChangeEvent.Update -> {
                                            val currentState = _chatMessagesState.value
                                            if (currentState is ChatMessagesState.Success) {
                                                val normalized =
                                                    vaultMessagesForUi(chatId, userId, listOf(event.message))
                                                        .first()
                                                        .withDbDerivedDeliveryState()
                                                val updatedMessages =
                                                    currentState.messages.map { mwu ->
                                                        if (mwu.message.id == normalized.id) {
                                                            mwu.copy(message = normalized)
                                                        } else {
                                                            mwu
                                                        }
                                                    }
                                                _chatMessagesState.value = currentState.copy(messages = updatedMessages)
                                                updatedMessages
                                                    .maxByOrNull { it.message.timeCreated }
                                                    ?.message
                                                    ?.takeIf { it.id == normalized.id }
                                                    ?.let { newest ->
                                                        bumpConnectionInChatList(currentState.chatDetails.connection.id, newest)
                                                    }
                                            }
                                        }
                                        is MessageChangeEvent.Delete -> {
                                            val currentState = _chatMessagesState.value
                                            if (currentState is ChatMessagesState.Success) {
                                                val filtered = currentState.messages.filter { it.message.id != event.messageId }
                                                _chatMessagesState.value = currentState.copy(messages = filtered)
                                            }
                                        }
                                    }
                                is ChatRealtimeEvent.Reaction -> {
                                    applyReactionChangeEvent(envelope.event)
                                }
                            }
                        }.launchIn(this)

                    subscription.attach()
                    return@launch
                } catch (e: CancellationException) {
                    // Scope was cancelled (chat closed / VM cleared). Do NOT retry.
                    throw e
                } catch (e: Exception) {
                    attempt += 1
                    activeMessageSubscription?.let { sub ->
                        try {
                            sub.detach()
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            // best effort
                        }
                    }
                    activeMessageSubscription = null
                    println("Error subscribing to messages (attempt $attempt): ${e.redactedRestMessage()}")
                    if (attempt < MESSAGE_SUBSCRIPTION_MAX_ATTEMPTS && currentApiChatId == chatId) {
                        delay(MESSAGE_SUBSCRIPTION_RETRY_DELAY_MS * attempt)
                    }
                }
            }
        }
}

internal fun ChatViewModel.restoreActiveChatSubscriptionsIfNeeded() {
    val userId = _currentUserId.value ?: return
    val currentState = _chatMessagesState.value as? ChatMessagesState.Success ?: return
    val apiChatId = currentState.chatDetails.chat.id ?: return
    val peerUserId = currentState.chatDetails.otherUser.id
    val needsMessageSubscription = currentApiChatId != apiChatId || activeMessageSubscription == null || realtimeJob?.isActive != true
    val needsTypingSubscription = typingPollingJob?.isActive != true
    val needsPeerPresence = peerOnlineJob?.isActive != true

    currentApiChatId = apiChatId

    viewModelScope.launch {
        if (needsPeerPresence || needsTypingSubscription) {
            chatRepository.joinChatEphemeralChannel(apiChatId, userId, peerUserId)
        }
        if (needsMessageSubscription) {
            subscribeToNewMessages(apiChatId, userId)
        }
        if (needsTypingSubscription) {
            startTypingMonitoring(apiChatId)
        }
        if (needsPeerPresence) {
            startPeerOnlineMonitoring(apiChatId, peerUserId)
        }
        startActiveChatSync(apiChatId, userId)
    }
}

internal fun ChatViewModel.startActiveChatSync(
    chatId: String,
    userId: String,
) {
    activeChatSyncJob?.cancel()
    activeChatSyncJob =
        viewModelScope.launch {
            while (currentApiChatId == chatId) {
                syncActiveChatReactions(chatId)
                delay(ACTIVE_CHAT_SYNC_INTERVAL_MS)
            }
        }
}

/**
 * Merges server-fetched reactions with in-flight optimistic rows (`temp-…` ids) so polling does not
 * wipe the UI while a toggle is in flight.
 */
internal fun ChatViewModel.mergeReactionMapsPreserveOptimistic(
    local: Map<String, List<MessageReaction>>,
    server: Map<String, List<MessageReaction>>,
): Map<String, List<MessageReaction>> {
    val out = server.toMutableMap()
    for ((msgId, localList) in local) {
        val optimistic = localList.filter { it.id.startsWith("temp-") }
        if (optimistic.isEmpty()) continue
        val base = out[msgId].orEmpty()
        val additions =
            optimistic.filter { opt ->
                base.none { it.userId == opt.userId && it.reactionType == opt.reactionType }
            }
        if (additions.isNotEmpty()) {
            out[msgId] = base + additions
        }
    }
    for ((msgId, localList) in local) {
        if (out.containsKey(msgId)) continue
        val onlyTemp = localList.filter { it.id.startsWith("temp-") }
        if (onlyTemp.isNotEmpty()) {
            out[msgId] = onlyTemp
        }
    }
    return out
}

internal suspend fun ChatViewModel.syncActiveChatReactions(chatId: String) {
    if ((_chatMessagesState.value as? ChatMessagesState.Success)?.chatDetails?.chat?.id != chatId) return
    if (!isPersistedApiChatId(chatId)) return
    val messageIds =
        (_chatMessagesState.value as? ChatMessagesState.Success)
            ?.messages
            ?.map { it.message.id }
            ?.filter { isPersistedApiUuid(it) }
            .orEmpty()
    if (messageIds.isEmpty()) return
    val server =
        runCatching {
            chatRepository.fetchReactionsForChat(chatId, messageIds).groupBy { it.messageId }
        }.getOrElse { return }
    val merged = mergeReactionMapsPreserveOptimistic(_messageReactions.value, server)
    if (merged != _messageReactions.value) {
        _messageReactions.value = merged
    }
}

internal suspend fun ChatViewModel.syncActiveChatMessages(
    chatId: String,
    userId: String,
) {
    val currentState = _chatMessagesState.value as? ChatMessagesState.Success ?: return
    if (currentState.chatDetails.chat.id != chatId) return

    val fetchedMessages = chatRepository.fetchMessagesForChat(chatId, userId) ?: return
    val latestMessages = vaultMessagesForUi(chatId, userId, fetchedMessages)
    val pendingOptimistic =
        currentState.messages.filter { mwu ->
            val m = mwu.message
            m.id.startsWith("temp-") &&
                m.deliveryState == MessageDeliveryState.PENDING &&
                (
                    m.localSentAt == null ||
                        latestMessages.none { s ->
                            s.user_id == m.user_id && s.localSentAt == m.localSentAt
                        }
                )
        }
    val currentSansPending =
        currentState.messages
            .filterNot { mwu ->
                val m = mwu.message
                m.id.startsWith("temp-") && m.deliveryState == MessageDeliveryState.PENDING
            }.map { it.message }
    if (latestMessages == currentSansPending && pendingOptimistic.isEmpty()) return

    val knownUsers =
        buildMap {
            put(currentState.chatDetails.otherUser.id, currentState.chatDetails.otherUser)
            currentState.messages.forEach { messageWithUser ->
                put(messageWithUser.user.id, messageWithUser.user)
            }
            AppDataManager.currentUser.value?.let { currentUser ->
                put(currentUser.id, currentUser)
            }
        }.toMutableMap()

    val missingUserIds =
        latestMessages
            .map { it.user_id }
            .distinct()
            .filterNot { knownUsers.containsKey(it) }

    if (missingUserIds.isNotEmpty()) {
        chatRepository.fetchChatParticipants(chatId).forEach { participant ->
            knownUsers[participant.id] = participant
        }
    }

    val refreshedMessages =
        latestMessages.map { message ->
            val user =
                knownUsers[message.user_id] ?: User(
                    id = message.user_id,
                    name = "Unknown",
                    createdAt = 0L,
                )
            MessageWithUser(
                message = message,
                user = user,
                isSent = message.user_id == userId,
            )
        }

    val mergedTimeline = (refreshedMessages + pendingOptimistic).sortedBy { it.message.timeCreated }
    _chatMessagesState.value = currentState.copy(messages = mergedTimeline)

    latestMessages.lastOrNull()?.let { newest ->
        bumpConnectionInChatList(currentState.chatDetails.connection.id, newest)
    }

    if (latestMessages.any { it.user_id != userId && !it.isRead }) {
        markMessagesReadOptimistically(
            connectionId = currentState.chatDetails.connection.id,
            chatId = chatId,
            userId = userId,
        )
    }

    enqueueInboundDeliveredAck(chatId, userId, latestMessages)
}

internal suspend fun ChatViewModel.vaultMessagesForUi(
    chatId: String,
    userId: String,
    messages: List<Message>,
): List<Message> {
    if (messages.isEmpty()) return messages
    return chatRepository.vaultEncryptedMediaMessages(chatId, userId, messages)
}

internal fun ChatViewModel.enqueueInboundDeliveredAck(
    chatId: String,
    viewerUserId: String,
    messages: List<Message>,
) {
    val ids =
        messages
            .asSequence()
            .filter { it.user_id != viewerUserId && it.deliveredAt == null }
            .map { it.id }
            .distinct()
            .take(200)
            .toList()
    if (ids.isEmpty()) return
    viewModelScope.launch {
        ids.chunked(80).forEach { chunk ->
            runCatching { chatRepository.markMessagesDelivered(chatId, chunk) }
        }
    }
}

internal suspend fun ChatViewModel.resolveMessageUser(
    userId: String,
    chatId: String,
): User? {
    val currentState = _chatMessagesState.value as? ChatMessagesState.Success
    if (currentState != null) {
        currentState.messages.firstOrNull { it.user.id == userId }?.let { return it.user }
        if (currentState.chatDetails.otherUser.id == userId) {
            return currentState.chatDetails.otherUser
        }
    }

    AppDataManager.currentUser.value
        ?.takeIf { it.id == userId }
        ?.let { return it }

    return chatRepository.getUserById(userId)
}

internal fun ChatViewModel.migrateOptimisticSecureImage(
    tempId: String,
    serverMessageId: String,
) {
    val cachedBytes =
        secureImageBytesCache.get(tempId)
            ?: _secureChatMediaLoadState.value[tempId]?.imageBytes
    // Keep decoded bitmaps across temp→server id so Click Drop send does not flash blank.
    compose.project.click.click.ui.chat.secureChatImageBitmapCache.get(tempId)?.let { bmp ->
        compose.project.click.click.ui.chat.secureChatImageBitmapCache
            .put(serverMessageId, bmp)
        compose.project.click.click.ui.chat.secureChatImageBitmapCache
            .remove(tempId)
    }
    compose.project.click.click.ui.chat
        .migrateLockedDropBlurCacheKey(tempId, serverMessageId)
    if (cachedBytes != null && cachedBytes.isNotEmpty()) {
        secureImageBytesCache.put(serverMessageId, cachedBytes)
        secureImageBytesCache.remove(tempId)
        val prior = _secureChatMediaLoadState.value[tempId]
        _secureChatMediaLoadState.update { map ->
            val withoutTemp = map - tempId
            if (prior != null) {
                withoutTemp + (
                    serverMessageId to
                        prior.copy(
                            loading = false,
                            imageBytes = cachedBytes,
                        )
                )
            } else {
                withoutTemp + (
                    serverMessageId to
                        SecureChatMediaLoadState(
                            loading = false,
                            imageBytes = cachedBytes,
                        )
                )
            }
        }
    } else {
        _secureChatMediaLoadState.update { it - tempId }
    }
}

internal fun ChatViewModel.stripOptimisticMatchingServerRow(
    messages: List<MessageWithUser>,
    serverMessage: Message,
): List<MessageWithUser> {
    val stamp = serverMessage.localSentAt
    return messages.filterNot { mwu ->
        mwu.message.id.startsWith("temp-") &&
            mwu.message.user_id == serverMessage.user_id &&
            stamp != null &&
            mwu.message.localSentAt == stamp
    }
}

internal fun ChatViewModel.findPendingOptimisticTempId(
    messages: List<MessageWithUser>,
    serverMessage: Message,
    currentUserId: String,
): String? {
    if (serverMessage.user_id != currentUserId) return null
    serverMessage.localSentAt?.let { stamp ->
        messages
            .firstOrNull { mwu ->
                mwu.message.id.startsWith("temp-") &&
                    mwu.message.user_id == currentUserId &&
                    mwu.message.localSentAt == stamp
            }?.message
            ?.id
            ?.let { return it }
    }
    return messages
        .lastOrNull { mwu ->
            mwu.message.id.startsWith("temp-") &&
                mwu.message.user_id == currentUserId &&
                mwu.message.messageType == serverMessage.messageType &&
                mwu.message.deliveryState == MessageDeliveryState.PENDING
        }?.message
        ?.id
}

internal fun ChatViewModel.resolveInsertedMessage(
    serverMessage: Message,
    messages: List<MessageWithUser>,
    tempId: String?,
): Message {
    if (serverMessage.localSentAt != null) return serverMessage
    val optimistic = tempId?.let { id -> messages.find { it.message.id == id }?.message }
    val stamp = optimistic?.localSentAt ?: return serverMessage
    return serverMessage.copy(localSentAt = stamp)
}

internal fun ChatViewModel.appendOutgoingOptimistic(
    message: Message,
    currentUser: User,
) {
    val currentState = _chatMessagesState.value as? ChatMessagesState.Success ?: return
    val connectionId = currentState.chatDetails.connection.id
    _chatMessagesState.value =
        currentState.copy(
            messages =
                normalizeChatTimeline(
                    currentState.messages +
                        MessageWithUser(
                            message = message,
                            user = currentUser,
                            isSent = true,
                        ),
                ),
        )
    bumpConnectionInChatList(connectionId, message)
}

internal fun ChatViewModel.markOptimisticSendFailed(tempId: String) {
    val currentState = _chatMessagesState.value as? ChatMessagesState.Success ?: return
    _chatMessagesState.value =
        currentState.copy(
            messages =
                currentState.messages.map { mwu ->
                    if (mwu.message.id == tempId) {
                        mwu.copy(message = mwu.message.copy(deliveryState = MessageDeliveryState.ERROR))
                    } else {
                        mwu
                    }
                },
        )
}

internal fun ChatViewModel.applyInsertedMessage(
    message: Message,
    user: User,
    currentUserId: String,
    optimisticTempId: String? = null,
) {
    val currentState = _chatMessagesState.value as? ChatMessagesState.Success ?: return
    val connectionId = currentState.chatDetails.connection.id
    val tempIdToReplace =
        optimisticTempId
            ?: findPendingOptimisticTempId(currentState.messages, message, currentUserId)
    tempIdToReplace?.let { migrateOptimisticSecureImage(it, message.id) }
    val mergedMessage = resolveInsertedMessage(message, currentState.messages, tempIdToReplace)

    if (tempIdToReplace != null) {
        val idx = currentState.messages.indexOfFirst { it.message.id == tempIdToReplace }
        if (idx >= 0) {
            val replaced = currentState.messages.toMutableList()
            replaced[idx] =
                MessageWithUser(
                    message = mergedMessage,
                    user = user,
                    isSent = mergedMessage.user_id == currentUserId,
                )
            _chatMessagesState.value = currentState.copy(messages = normalizeChatTimeline(replaced))
            bumpConnectionInChatList(connectionId, mergedMessage)
            return
        }
    }

    val baseList = stripOptimisticMatchingServerRow(currentState.messages, mergedMessage)
    val existingIdx = baseList.indexOfFirst { it.message.id == mergedMessage.id }
    if (existingIdx >= 0) {
        val updated = baseList.toMutableList()
        updated[existingIdx] =
            MessageWithUser(
                message = mergedMessage,
                user = user,
                isSent = mergedMessage.user_id == currentUserId,
            )
        _chatMessagesState.value = currentState.copy(messages = normalizeChatTimeline(updated))
        updated
            .maxByOrNull { it.message.timeCreated }
            ?.message
            ?.takeIf { it.id == mergedMessage.id }
            ?.let { bumpConnectionInChatList(connectionId, it) }
        return
    }

    _chatMessagesState.value =
        currentState.copy(
            messages =
                normalizeChatTimeline(
                    baseList +
                        MessageWithUser(
                            message = mergedMessage,
                            user = user,
                            isSent = mergedMessage.user_id == currentUserId,
                        ),
                ),
        )
    bumpConnectionInChatList(connectionId, mergedMessage)
}

/**
 * Refresh list row + reorder so the active thread moves up when a message arrives or is sent.
 *
 * Preview / `last_message_at` only move forward (or refresh the same last-message id).
 * Older realtime UPDATEs (e.g. load-older delivery/read) must not rewrite the inbox snippet.
 */
internal fun ChatViewModel.bumpConnectionInChatList(
    connectionId: String,
    message: Message,
    chatId: String? = null,
) {
    val preview = message.previewLabel()
    val viewerId = _currentUserId.value
    val isInbound = viewerId != null && message.user_id != viewerId
    val state =
        _chatListState.value as? ChatListState.Success ?: run {
            AppDataManager.updateConnectionChatActivity(connectionId, message.timeCreated, message)
            AppDataManager.updateInboxFeedChatActivity(connectionId, message)
            loadChats(isForced = true)
            return
        }
    val rowIndex = findInboxRowIndex(state.chats, connectionId, chatId)
    val resolvedListKey =
        if (rowIndex >= 0) {
            state.chats[rowIndex].connection.id
        } else {
            connectionId
        }
    val existingLast = if (rowIndex >= 0) state.chats[rowIndex].lastMessage else null
    val isSameLastMessage = existingLast != null && existingLast.id == message.id
    val isNewerThanLast =
        existingLast == null ||
            message.timeCreated > existingLast.timeCreated ||
            (message.timeCreated == existingLast.timeCreated && message.id >= existingLast.id)
    if (!isSameLastMessage && !isNewerThanLast) {
        return
    }
    val isViewingThread = resolvedListKey == currentConnectionId
    if (isInbound) {
        _readClearedConnectionIds.update { it - resolvedListKey }
    }
    if (preview.isNotBlank() && preview != "New message") {
        _decryptedPreviews.value = _decryptedPreviews.value + (resolvedListKey to preview)
    }
    val rowExists = rowIndex >= 0
    val updated =
        if (rowExists) {
            state.chats.mapIndexed { index, chat ->
                if (index == rowIndex) {
                    val prevLastId = chat.lastMessage?.id
                    val nextUnread =
                        when {
                            isViewingThread -> 0
                            isInbound && !message.isRead && message.id != prevLastId -> chat.unreadCount + 1
                            isInbound && !message.isRead && message.id == prevLastId -> maxOf(chat.unreadCount, 1)
                            else -> chat.unreadCount
                        }
                    val previewMessage =
                        if (isInbound && !message.isRead) {
                            message.copy(isRead = false)
                        } else {
                            message
                        }
                    val nextLastAt =
                        maxOf(
                            chat.connection.last_message_at ?: 0L,
                            message.timeCreated,
                        )
                    chat.copy(
                        lastMessage = previewMessage,
                        unreadCount = nextUnread,
                        connection =
                            chat.connection.copy(
                                last_message_at = nextLastAt,
                                chat = chat.connection.chat.copy(messages = listOf(previewMessage)),
                            ),
                    )
                } else {
                    chat
                }
            }
        } else {
            loadChats(isForced = true)
            state.chats
        }
    val sorted = updated.sortedByDescending { chatListActivityTimestamp(it) }
    val filtered = applyChatListVisibility(sorted)
    pruneStaleReadClearedHints(filtered)
    _chatListState.value = ChatListState.Success(applyUnreadClearHintsToInboxRows(filtered))
    AppDataManager.updateConnectionChatActivity(resolvedListKey, message.timeCreated, message)
    AppDataManager.updateInboxFeedChatActivity(resolvedListKey, message)
}

internal fun ChatViewModel.startTypingMonitoringImpl(chatId: String) {
    typingPollingJob?.cancel()
    peerTypingTimeoutJob?.cancel()
    typingPollingJob =
        viewModelScope.launch {
            chatRepository.observeTypingStatus(chatId).collect { status ->
                val currentUser = _currentUserId.value
                if (status.userId != currentUser && status.isTyping) {
                    _isPeerTyping.value = true
                    peerTypingTimeoutJob?.cancel()
                    peerTypingTimeoutJob =
                        launch {
                            delay(3000)
                            _isPeerTyping.value = false
                        }
                }
            }
        }
}

internal fun ChatViewModel.startPeerOnlineMonitoring(
    apiChatId: String,
    peerUserId: String,
) {
    peerOnlineJob?.cancel()
    peerOnlineJob =
        viewModelScope.launch {
            chatRepository.observePeerOnline(apiChatId, peerUserId).collect { online ->
                _isPeerOnline.value = online
            }
        }
}

internal fun ChatViewModel.onUserTypingImpl(chatId: String) {
    val userId = _currentUserId.value ?: return
    val now = Clock.System.now().toEpochMilliseconds()
    if (now - lastTypingSent > 2000L) {
        lastTypingSent = now
        viewModelScope.launch {
            chatRepository.sendTypingStatus(chatId, userId, true)
        }
    }
}

internal fun ChatViewModel.onUserStoppedTypingImpl(chatId: String) {
    lastTypingSent = 0L
}

internal fun ChatViewModel.applyReactionChangeEvent(event: ReactionChangeEvent) {
    val current = _messageReactions.value.toMutableMap()
    when (event) {
        is ReactionChangeEvent.Insert -> {
            val list = current.getOrElse(event.reaction.messageId) { emptyList() }
            val withoutDuplicates =
                list.filterNot {
                    it.id == event.reaction.id ||
                        (
                            it.userId == event.reaction.userId &&
                                it.reactionType == event.reaction.reactionType
                        )
                }
            current[event.reaction.messageId] = withoutDuplicates + event.reaction
            _messageReactions.value = current
        }
        is ReactionChangeEvent.Delete -> {
            val list = current[event.messageId]
            if (list != null) {
                current[event.messageId] = list.filter { it.id != event.reactionId }
                _messageReactions.value = current
            }
        }
    }
}
