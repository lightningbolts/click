@file:Suppress(
    "ktlint:standard:backing-property-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.MessageDeliveryState // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.replySnippetForMessage // pragma: allowlist secret
import compose.project.click.click.util.isOfflineNetworkFailure
import compose.project.click.click.util.isPersistedApiChatId
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

internal suspend fun ChatViewModel.resolveOrCreateApiChatId(
    connectionId: String,
    detailsOverride: ChatWithDetails? = null,
): String? {
    val currentState = _chatMessagesState.value as? ChatMessagesState.Success
    val details =
        detailsOverride
            ?: currentState?.chatDetails?.takeIf {
                it.connection.id == connectionId ||
                    it.groupClique?.groupId == connectionId ||
                    it.chat.groupId == connectionId
            }
    val groupClique = details?.groupClique
    if (groupClique != null) {
        val existingId = details.chat.id?.takeIf { isPersistedApiChatId(it) }
        if (existingId != null) {
            currentApiChatId = existingId
            return existingId
        }
        val ensured = chatRepository.ensureChatForGroup(groupClique.groupId) ?: return null
        val ensuredId = ensured.id?.takeIf { isPersistedApiChatId(it) } ?: return null
        currentApiChatId = ensuredId
        if (currentState != null &&
            (
                currentState.chatDetails.groupClique?.groupId == groupClique.groupId ||
                    currentState.chatDetails.connection.id == connectionId
            )
        ) {
            _chatMessagesState.value =
                currentState.copy(
                    chatDetails =
                        currentState.chatDetails.copy(
                            chat =
                                currentState.chatDetails.chat.copy(
                                    id = ensuredId,
                                    groupId = groupClique.groupId,
                                ),
                        ),
                )
        }
        return ensuredId
    }
    val existingChatId =
        details?.chat?.id?.takeIf { isPersistedApiChatId(it) }
            ?: currentState
                ?.takeIf { it.chatDetails.connection.id == connectionId }
                ?.chatDetails
                ?.chat
                ?.id
                ?.takeIf { isPersistedApiChatId(it) }
            ?: currentApiChatId?.takeIf { isPersistedApiChatId(it) }

    if (existingChatId != null) {
        currentApiChatId = existingChatId
        return existingChatId
    }

    val ensuredChat = chatRepository.ensureChatForConnection(connectionId) ?: return null
    val ensuredId = ensuredChat.id?.takeIf { isPersistedApiChatId(it) } ?: return null
    currentApiChatId = ensuredId

    if (currentState != null && currentState.chatDetails.connection.id == connectionId) {
        _chatMessagesState.value =
            currentState.copy(
                chatDetails =
                    currentState.chatDetails.copy(
                        chat =
                            currentState.chatDetails.chat.copy(
                                id = ensuredId,
                                connectionId = connectionId,
                            ),
                    ),
            )
    }

    return ensuredId
}

internal fun ChatViewModel.sendMessageImpl() {
    // If in edit mode, confirm the edit instead of posting a new message
    val editId = _editingMessageId.value
    if (editId != null) {
        confirmEditMessage(editId)
        return
    }
    val connectionId = currentConnectionId ?: return
    val userId = _currentUserId.value ?: return
    val content = _messageInput.value.trim()
    if (content.isEmpty()) return

    val offlineAtSend = !connectivityMonitor.isOnline.value
    if (offlineAtSend) {
        _messageSendError.value = ChatViewModel.OFFLINE_SEND_NOTICE
    } else {
        _messageSendError.value = null
    }
    val replyTargetCaptured = _replyingTo.value
    val metadataCaptured =
        if (replyTargetCaptured != null) {
            buildJsonObject {
                put("reply_to_id", replyTargetCaptured.message.id)
                put("reply_to_content", replySnippetForMessage(replyTargetCaptured.message))
            }
        } else {
            null
        }
    _messageInput.value = ""
    localTypingIdleJob?.cancel()
    localTypingIdleJob = null
    _isLocalTypingActive.value = false
    val successState = _chatMessagesState.value as? ChatMessagesState.Success
    val typingChatId =
        successState
            ?.chatDetails
            ?.chat
            ?.id
            ?.takeIf { isPersistedApiChatId(it) }
            ?: currentApiChatId?.takeIf { isPersistedApiChatId(it) }
    if (typingChatId != null) {
        onUserStoppedTyping(typingChatId)
    }

    viewModelScope.launch(Dispatchers.Main.immediate) {
        val openThreadReady =
            successState != null &&
                (
                    successState.chatDetails.connection.id == connectionId ||
                        successState.chatDetails.groupClique?.groupId == connectionId
                )
        val localMs = Clock.System.now().toEpochMilliseconds()
        val tempId = "temp-$localMs-${Random.nextLong()}"
        val currentUserFast =
            AppDataManager.currentUser.value?.takeIf { it.id == userId }
                ?: User(id = userId, name = "You", createdAt = 0L)

        if (openThreadReady) {
            val optimistic =
                Message(
                    id = tempId,
                    user_id = userId,
                    content = content,
                    timeCreated = localMs,
                    isRead = false,
                    messageType = ChatMessageType.TEXT,
                    metadata = metadataCaptured,
                    localSentAt = localMs,
                    readAt = null,
                    deliveryState = MessageDeliveryState.PENDING,
                )
            appendOutgoingOptimistic(optimistic, currentUserFast)
        }

        val apiChatId =
            resolveOrCreateApiChatId(connectionId) ?: run {
                if (openThreadReady) {
                    markOptimisticSendFailed(tempId)
                }
                _messageSendError.value = "Failed to send — unable to start chat"
                _messageInput.value = content
                updateMessageInput(content)
                return@launch
            }
        if (!isPersistedApiChatId(apiChatId)) {
            if (openThreadReady) {
                markOptimisticSendFailed(tempId)
            }
            _messageSendError.value = "Failed to send — unable to start chat"
            _messageInput.value = content
            updateMessageInput(content)
            return@launch
        }
        onUserStoppedTyping(apiChatId)
        val currentUser = resolveMessageUser(userId, apiChatId) ?: currentUserFast
        // Prefer real connection UUID for gatekeeper fallback (never a group id / blank).
        val sendConnectionId =
            successState
                ?.chatDetails
                ?.chat
                ?.connectionId
                ?.takeIf { it.isNotBlank() && it != apiChatId }
                ?: successState
                    ?.chatDetails
                    ?.connection
                    ?.id
                    ?.takeIf { it.isNotBlank() && successState.chatDetails.groupClique == null }
                ?: connectionId.takeIf { successState?.chatDetails?.groupClique == null }

        if (!openThreadReady) {
            val optimistic =
                Message(
                    id = tempId,
                    user_id = userId,
                    content = content,
                    timeCreated = localMs,
                    isRead = false,
                    messageType = ChatMessageType.TEXT,
                    metadata = metadataCaptured,
                    localSentAt = localMs,
                    readAt = null,
                    deliveryState = MessageDeliveryState.PENDING,
                )
            appendOutgoingOptimistic(optimistic, currentUser)
        }

        outboundChatMessageMutex.withLock {
            _isMessageSubmitInProgress.value = true
            try {
                val message =
                    chatRepository.sendMessage(
                        chatId = apiChatId,
                        userId = userId,
                        content = content,
                        metadata = metadataCaptured,
                        clientLocalSentAtMs = localMs,
                        connectionId = sendConnectionId,
                    )
                if (message != null) {
                    _replyingTo.value = null
                    applyInsertedMessage(message, currentUser, userId, optimisticTempId = tempId)
                    activateConnectionIfPending(connectionId)
                } else {
                    if (offlineAtSend || !connectivityMonitor.isOnline.value) {
                        _messageSendError.value = ChatViewModel.OFFLINE_SEND_NOTICE
                    } else {
                        markOptimisticSendFailed(tempId)
                        _messageSendError.value = "Failed to send message"
                        _messageInput.value = content
                        updateMessageInput(content)
                        println("Failed to send message")
                    }
                }
            } catch (e: Exception) {
                if (offlineAtSend || !connectivityMonitor.isOnline.value || e.isOfflineNetworkFailure()) {
                    _messageSendError.value = ChatViewModel.OFFLINE_SEND_NOTICE
                } else {
                    markOptimisticSendFailed(tempId)
                    val detail = e.redactedRestMessage().ifBlank { "encryption or network error" }
                    _messageSendError.value = "Failed to send — $detail"
                    _messageInput.value = content
                    updateMessageInput(content)
                    println("Error sending message: $detail")
                }
            } finally {
                _isMessageSubmitInProgress.value = false
            }
        }
    }
}

internal fun ChatViewModel.stageMediaForUploadImpl(
    bytes: ByteArray,
    mimeType: String,
) {
    if (bytes.isEmpty()) return
    _stagedChatImages.update { cur ->
        if (cur.size >= CHAT_STAGED_MEDIA_MAX) {
            cur
        } else {
            cur +
                StagedChatImage(
                    id = "stg-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(1_000_000_000)}",
                    bytes = bytes,
                    mimeType = mimeType,
                )
        }
    }
}

internal fun ChatViewModel.removeStagedMediaImpl(id: String) {
    _stagedChatImages.update { it.filterNot { s -> s.id == id } }
}

internal fun ChatViewModel.stageBeaconForShareImpl(beacon: compose.project.click.click.data.models.MapBeacon) {
    _stagedBeacon.value = beacon
    _stagedChatImages.value = emptyList()
}

internal fun ChatViewModel.clearStagedBeaconImpl() {
    _stagedBeacon.value = null
}

internal fun ChatViewModel.commitStagedBeaconImpl() {
    val beacon = _stagedBeacon.value ?: return
    _stagedBeacon.value = null
    sendBeaconMessage(beacon)
}

internal fun ChatViewModel.commitStagedMediaToUploadImpl() {
    val connectionId = currentConnectionId ?: return
    val userId = _currentUserId.value ?: return
    val batch = _stagedChatImages.value
    if (batch.isEmpty()) return
    val caption = _messageInput.value.trim()
    val replyTarget = _replyingTo.value
    _stagedChatImages.value = emptyList()
    _messageSendError.value = null
    viewModelScope.launch {
        val apiChatId =
            resolveOrCreateApiChatId(connectionId) ?: run {
                _stagedChatImages.value = batch
                _messageSendError.value = "Failed to send — unable to start chat"
                return@launch
            }
        val currentUser =
            resolveMessageUser(userId, apiChatId)
                ?: AppDataManager.currentUser.value?.takeIf { it.id == userId }
                ?: User(id = userId, name = "You", createdAt = 0L)
        batch.forEachIndexed { index, item ->
            var progressJob: Job? = null
            outboundChatMessageMutex.withLock {
                _isMessageSubmitInProgress.value = true
                try {
                    val tempId = "temp-img-${item.id}"
                    val localMs = Clock.System.now().toEpochMilliseconds()
                    val optimistic =
                        Message(
                            id = tempId,
                            user_id = userId,
                            content = if (caption.isEmpty() || index > 0) " " else caption,
                            timeCreated = localMs,
                            messageType = ChatMessageType.IMAGE,
                            metadata =
                                buildJsonObject {
                                    put("is_encrypted_media", true)
                                    put("original_mime_type", item.mimeType)
                                },
                            localSentAt = localMs,
                            deliveryState = MessageDeliveryState.PENDING,
                        )
                    appendOutgoingOptimistic(optimistic, currentUser)
                    secureImageBytesCache.put(tempId, item.bytes)
                    _secureChatMediaLoadState.update {
                        it + (
                            tempId to
                                SecureChatMediaLoadState(
                                    loading = false,
                                    imageBytes = item.bytes,
                                    uploadProgress = 0f,
                                )
                        )
                    }
                    var progress = 0f
                    progressJob =
                        launch {
                            while (isActive && progress < 0.9f) {
                                delay(110)
                                progress = (progress + 0.045f).coerceAtMost(0.9f)
                                _secureChatMediaLoadState.update { m ->
                                    val cur = m[tempId]
                                    val bytes = cur?.imageBytes ?: item.bytes
                                    m + (
                                        tempId to
                                            SecureChatMediaLoadState(
                                                loading = false,
                                                imageBytes = bytes,
                                                uploadProgress = progress,
                                            )
                                    )
                                }
                            }
                        }
                    try {
                        val ext = extensionForChatMedia(item.mimeType, isImage = true)
                        val unique = "${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(1_000_000_000)}"
                        val path = "$userId/$apiChatId/$unique.$ext"
                        val url =
                            chatRepository.uploadChatMedia(item.bytes, path, item.mimeType) ?: run {
                                progressJob?.cancel()
                                markOptimisticSendFailed(tempId)
                                secureImageBytesCache.remove(tempId)
                                _secureChatMediaLoadState.update { m -> m - tempId }
                                _messageSendError.value = "Failed to upload photo"
                                return@withLock
                            }
                        progressJob?.cancel()
                        _secureChatMediaLoadState.update {
                            val cur = it[tempId]
                            val bytes = cur?.imageBytes ?: item.bytes
                            it + (
                                tempId to
                                    SecureChatMediaLoadState(
                                        loading = false,
                                        imageBytes = bytes,
                                        uploadProgress = 1f,
                                    )
                            )
                        }
                        delay(60)
                        val meta =
                            when {
                                replyTarget != null && index == 0 -> {
                                    buildJsonObject {
                                        put("media_url", url)
                                        put("original_mime_type", item.mimeType)
                                        put("is_encrypted_media", true)
                                        put("reply_to_id", replyTarget.message.id)
                                        put("reply_to_content", replySnippetForMessage(replyTarget.message))
                                    }
                                }
                                else -> {
                                    buildJsonObject {
                                        put("media_url", url)
                                        put("original_mime_type", item.mimeType)
                                        put("is_encrypted_media", true)
                                    }
                                }
                            }
                        val message =
                            chatRepository.sendMessage(
                                chatId = apiChatId,
                                userId = userId,
                                content = if (caption.isEmpty() || index > 0) " " else caption,
                                messageType = ChatMessageType.IMAGE,
                                metadata = meta,
                                clientLocalSentAtMs = localMs,
                            )
                        if (message != null) {
                            if (index == 0) {
                                _messageInput.value = ""
                                updateMessageInput("")
                                _replyingTo.value = null
                            }
                            applyInsertedMessage(message, currentUser, userId, optimisticTempId = tempId)
                            activateConnectionIfPending(connectionId)
                        } else {
                            markOptimisticSendFailed(tempId)
                            secureImageBytesCache.remove(tempId)
                            _secureChatMediaLoadState.update { m -> m - tempId }
                            _messageSendError.value = "Failed to send photo"
                        }
                    } catch (e: Exception) {
                        progressJob?.cancel()
                        markOptimisticSendFailed(tempId)
                        secureImageBytesCache.remove(tempId)
                        _secureChatMediaLoadState.update { m -> m - tempId }
                        _messageSendError.value = "Failed to send photo — ${e.redactedRestMessage().ifBlank { "error" }}"
                    }
                } finally {
                    progressJob?.cancel()
                    _isMessageSubmitInProgress.value = false
                }
            }
        }
    }
}

internal fun ChatViewModel.clearMessageSendErrorImpl() {
    _messageSendError.value = null
}

internal fun ChatViewModel.startReplyToImpl(target: MessageWithUser) {
    _editingMessageId.value = null
    _replyingTo.value = target
}

internal fun ChatViewModel.clearReplyTargetImpl() {
    _replyingTo.value = null
}

/**
 * Transition a pending connection to active when the first message is sent.
 * This sets expiry_state = 'active' server-side, starting the 7-day rolling window.
 */
internal suspend fun ChatViewModel.activateConnectionIfPending(connectionId: String) {
    val currentState = _chatMessagesState.value
    if (currentState is ChatMessagesState.Success) {
        val connection = currentState.chatDetails.connection
        if (connection.isPending()) {
            if (supabaseRepository.updateConnectionExpiryState(connectionId, "active")) {
                updateConnectionState(connectionId) { it.copy(expiry_state = "active", status = "active") }
            }
        }
    }
}

internal fun ChatViewModel.updateMessageInputImpl(text: String) {
    _messageInput.value = text.take(CHAT_MESSAGE_INPUT_MAX_LENGTH)
    val success = _chatMessagesState.value as? ChatMessagesState.Success ?: return
    if (success.chatDetails.connection.id != currentConnectionId) return
    val apiChatId =
        success.chatDetails.chat.id
            ?.takeIf { it.isNotBlank() }
            ?: currentApiChatId?.takeIf { it.isNotBlank() }
            ?: return
    if (text.isBlank()) {
        localTypingIdleJob?.cancel()
        localTypingIdleJob = null
        _isLocalTypingActive.value = false
        onUserStoppedTyping(apiChatId)
    } else {
        _isLocalTypingActive.value = true
        localTypingIdleJob?.cancel()
        localTypingIdleJob =
            viewModelScope.launch {
                delay(3000)
                _isLocalTypingActive.value = false
            }
        onUserTyping(apiChatId)
    }
}

/**
 * @param clearMessageSurface When false, skips forcing [ChatMessagesState.Loading] after teardown.
 * Use when the chat composable may still be attached for a frame (e.g. iOS interactive back)
 * so the UI does not flash a full-screen loading state over the list.
 */
internal fun ChatViewModel.leaveChatRoomImpl(clearMessageSurface: Boolean = true) {
    val departingState = _chatMessagesState.value as? ChatMessagesState.Success
    val departingConnectionId = currentConnectionId ?: departingState?.chatDetails?.connection?.id
    val chatId = departingState?.chatDetails?.chat?.id
    val userId = _currentUserId.value
    if (chatId != null && userId != null) {
        onUserStoppedTyping(chatId)
    }
    departingConnectionId?.let { connId ->
        departingState
            ?.messages
            ?.map { it.message }
            ?.sortedWith(compareBy({ it.timeCreated }, { it.id }))
            ?.takeIf { it.isNotEmpty() }
            ?.let { rows ->
                chatRepository.storeCachedMessageTimeline(connId, rows)
                // Repair inbox if an older realtime UPDATE rewrote the preview while in-thread.
                rows.maxByOrNull { it.timeCreated }?.let { newest ->
                    bumpConnectionInChatList(connId, newest)
                }
            }
    }
    clearSecureChatMediaCache()
    realtimeJob?.cancel()
    realtimeJob = null
    // Remove the realtime channels from Supabase. Track the detach as the
    // current realtimeJob so a quick re-entry (subscribeToNewMessages joins
    // the previous job) can't open a duplicate channel while detach is in
    // flight. NonCancellable keeps the teardown running through that join.
    val departingSubscription = activeMessageSubscription
    activeMessageSubscription = null
    if (departingSubscription != null) {
        realtimeJob =
            viewModelScope.launch {
                withContext(NonCancellable) {
                    try {
                        departingSubscription.detach()
                    } catch (_: Exception) {
                    }
                }
            }
    }
    activeChatSyncJob?.cancel()
    activeChatSyncJob = null
    _messageReactions.value = emptyMap()
    typingPollingJob?.cancel()
    typingPollingJob = null
    peerTypingTimeoutJob?.cancel()
    peerTypingTimeoutJob = null
    peerOnlineJob?.cancel()
    peerOnlineJob = null
    localTypingIdleJob?.cancel()
    localTypingIdleJob = null
    currentApiChatId?.let { id ->
        viewModelScope.launch { chatRepository.leaveChatEphemeralChannel(id) }
    }
    currentConnectionId = null
    currentApiChatId = null
    _stagedChatImages.value = emptyList()
    _stagedBeacon.value = null
    _isPeerTyping.value = false
    _isPeerOnline.value = false
    _isLocalTypingActive.value = false
    _isMessageSubmitInProgress.value = false
    if (clearMessageSurface) {
        val hasRetainedTimeline =
            departingConnectionId?.let { connId ->
                chatRepository.peekCachedMessageTimeline(connId)?.isNotEmpty() == true ||
                    prefetchedChatPayloads[connId]?.messages?.isNotEmpty() == true
            } == true
        if (!hasRetainedTimeline) {
            _chatMessagesState.value = ChatMessagesState.Loading
        }
    }
    resetVibeCheckState()
    resetIcebreakerState()
}
