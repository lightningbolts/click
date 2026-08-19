package compose.project.click.click.viewmodel // pragma: allowlist secret

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.data.api.ChatApiClient // pragma: allowlist secret
import compose.project.click.click.data.auth.EnsureFreshAccessToken // pragma: allowlist secret
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.MessageDeliveryState // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.realtime.rebindRealtimeSocket // pragma: allowlist secret
import compose.project.click.click.ui.chat.deleteSecureChatAudioTempFile // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.Presence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.random.Random

internal fun HubChatViewModel.launchRealtimeSession() {
    sessionJob?.cancel()
    participantDenied = false
    _realtimeState.value = HubRealtimeState.Loading
    sessionJob =
        viewModelScope.launch {
            try {
                coroutineScope {
                    val override = realtimeSessionOverride
                    val realtimeJob =
                        launch {
                            if (override != null) {
                                override()
                                _realtimeState.value = HubRealtimeState.Ready
                            } else {
                                try {
                                    runRealtimeSession()
                                } catch (first: CancellationException) {
                                    throw first
                                } catch (first: Exception) {
                                    println(
                                        "HubChatViewModel: realtime connect failed, retrying: " +
                                            first.redactedRestMessage(),
                                    )
                                    runRealtimeSession()
                                }
                            }
                        }
                    if (override == null) {
                        loadInitialMessages()
                    }
                    realtimeJob.join()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _realtimeState.value =
                    HubRealtimeState.Error(
                        e.message?.takeIf { it.isNotBlank() } ?: "Couldn't connect to this hub",
                    )
                println("HubChatViewModel: session error: ${e.redactedRestMessage()}")
            }
        }
}

internal fun HubChatViewModel.userIdFromPresence(p: Presence): String? {
    fun fromObject(obj: JsonObject): String? {
        val el = obj["userId"] ?: obj["user_id"] ?: return null
        return (el as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }
    fromObject(p.state)?.let { return it }
    val nested = p.state["state"]?.let { it as? JsonObject } ?: return null
    return fromObject(nested)
}

internal suspend fun HubChatViewModel.prefetchSenderUi(userIds: Collection<String>) {
    val missing =
        userIds
            .filter { it != currentUserId && !senderUiCache.containsKey(it) }
            .distinct()
    if (missing.isEmpty()) return
    userRepository.fetchUsersByIds(missing).forEach { user ->
        val label = user.name?.takeIf { it.isNotBlank() } ?: "Member"
        val avatar = user.image?.trim()?.takeIf { it.isNotEmpty() }
        senderUiCache[user.id] = label to avatar
    }
}

internal fun HubChatViewModel.rowToMessageWithUser(row: HubMessageRow): MessageWithUser {
    val mine = row.userId == currentUserId
    val (label, avatar) =
        if (mine) {
            "You" to null
        } else {
            senderUiCache[row.userId] ?: ("Member" to null)
        }
    val message =
        Message(
            id = row.id,
            user_id = row.userId,
            content = row.body,
            timeCreated = hubCreatedAtToEpoch(row.createdAt),
            timeEdited = null,
            isRead = false,
            messageType = row.messageType,
            metadata = row.metadata,
        )
    val user =
        if (mine) {
            User(id = row.userId, name = "You", image = null, createdAt = 0L)
        } else {
            User(id = row.userId, name = label, image = avatar, createdAt = 0L)
        }
    return MessageWithUser(message = message, user = user, isSent = mine)
}

internal fun HubChatViewModel.messageWithUserFromCached(
    message: Message,
    participants: List<User>,
): MessageWithUser {
    val mine = message.user_id == currentUserId
    val participant = participants.firstOrNull { it.id == message.user_id }
    val (label, avatar) =
        when {
            mine -> "You" to null
            participant != null -> (participant.name?.takeIf { it.isNotBlank() } ?: "Member") to participant.image
            else -> senderUiCache[message.user_id] ?: ("Member" to null)
        }
    val user =
        if (mine) {
            User(id = message.user_id, name = "You", image = null, createdAt = 0L)
        } else {
            User(id = message.user_id, name = label, image = avatar, createdAt = 0L)
        }
    return MessageWithUser(message = message, user = user, isSent = mine)
}

internal fun HubChatViewModel.hydrateFromDiskCache() {
    val cached = AppDataManager.cachedHubThreadFor(hubId) ?: return
    if (cached.messages.isEmpty()) return
    cached.participants.forEach { user ->
        if (user.id != currentUserId) {
            val label = user.name?.takeIf { it.isNotBlank() } ?: "Member"
            val avatar = user.image?.trim()?.takeIf { it.isNotEmpty() }
            senderUiCache[user.id] = label to avatar
        }
    }
    _messages.value = cached.messages.map { messageWithUserFromCached(it, cached.participants) }
}

internal fun HubChatViewModel.persistHubMessagesToDisk(messages: List<MessageWithUser>) {
    if (messages.isEmpty()) return
    AppDataManager.cacheHubThread(
        hubId = hubId,
        realtimeChannel = realtimeChannelName,
        messages = messages.map { it.message },
        participants = messages.map { it.user }.distinctBy { it.id },
    )
}

internal fun HubChatViewModel.pendingOptimisticOutgoing(serverMessages: List<MessageWithUser>): List<MessageWithUser> {
    return _messages.value.filter { mwu ->
        val message = mwu.message
        if (!message.id.startsWith("temp-") || message.user_id != currentUserId) return@filter false
        if (message.deliveryState != MessageDeliveryState.PENDING) return@filter false
        val stamp = message.localSentAt
        if (stamp != null &&
            serverMessages.any { s ->
                s.message.user_id == message.user_id && s.message.localSentAt == stamp
            }
        ) {
            return@filter false
        }
        !serverMessages.any { s ->
            s.message.user_id == message.user_id && s.message.content == message.content
        }
    }
}

internal fun HubChatViewModel.stripOptimisticMatchingServerRow(
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

internal fun HubChatViewModel.findPendingOptimisticTempId(
    messages: List<MessageWithUser>,
    serverMessage: Message,
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
                mwu.message.deliveryState == MessageDeliveryState.PENDING &&
                mwu.message.content == serverMessage.content
        }?.message
        ?.id
}

internal fun HubChatViewModel.resolveInsertedMessage(
    serverMessage: Message,
    messages: List<MessageWithUser>,
    tempId: String?,
): Message {
    if (serverMessage.localSentAt != null) {
        return serverMessage.copy(deliveryState = MessageDeliveryState.SENT)
    }
    val optimistic = tempId?.let { id -> messages.find { it.message.id == id }?.message }
    val stamp = optimistic?.localSentAt ?: return serverMessage.copy(deliveryState = MessageDeliveryState.SENT)
    return serverMessage.copy(localSentAt = stamp, deliveryState = MessageDeliveryState.SENT)
}

internal fun HubChatViewModel.applyInsertedHubMessage(
    serverMessage: Message,
    optimisticTempId: String? = null,
) {
    val current = _messages.value
    val tempIdToReplace =
        optimisticTempId
            ?: findPendingOptimisticTempId(current, serverMessage)
    val mergedMessage = resolveInsertedMessage(serverMessage, current, tempIdToReplace)
    val user =
        if (mergedMessage.user_id == currentUserId) {
            User(id = currentUserId, name = "You", image = null, createdAt = 0L)
        } else {
            val (label, avatar) = senderUiCache[mergedMessage.user_id] ?: ("Member" to null)
            User(id = mergedMessage.user_id, name = label, image = avatar, createdAt = 0L)
        }

    if (tempIdToReplace != null) {
        val idx = current.indexOfFirst { it.message.id == tempIdToReplace }
        if (idx >= 0) {
            val replaced = current.toMutableList()
            replaced[idx] =
                MessageWithUser(
                    message = mergedMessage,
                    user = user,
                    isSent = mergedMessage.user_id == currentUserId,
                )
            _messages.value = replaced
            persistHubMessagesToDisk(replaced)
            return
        }
    }

    val baseList = stripOptimisticMatchingServerRow(current, mergedMessage)
    val existingIdx = baseList.indexOfFirst { it.message.id == mergedMessage.id }
    if (existingIdx >= 0) {
        val updated = baseList.toMutableList()
        val prior = updated[existingIdx].message
        updated[existingIdx] =
            MessageWithUser(
                message =
                    mergedMessage.copy(
                        localSentAt = mergedMessage.localSentAt ?: prior.localSentAt,
                    ),
                user = user,
                isSent = mergedMessage.user_id == currentUserId,
            )
        _messages.value = updated
        persistHubMessagesToDisk(updated)
        return
    }

    val next =
        baseList +
            MessageWithUser(
                message = mergedMessage,
                user = user,
                isSent = mergedMessage.user_id == currentUserId,
            )
    _messages.value = next
    persistHubMessagesToDisk(next)
}

internal fun HubChatViewModel.markOptimisticSendFailed(tempId: String) {
    val next =
        _messages.value.map { mwu ->
            if (mwu.message.id == tempId) {
                mwu.copy(message = mwu.message.copy(deliveryState = MessageDeliveryState.ERROR))
            } else {
                mwu
            }
        }
    _messages.value = next
    persistHubMessagesToDisk(next)
}

internal fun HubChatViewModel.appendOptimisticOutgoing(text: String): String {
    val localMs = Clock.System.now().toEpochMilliseconds()
    val tempId = "temp-$localMs-${Random.nextLong()}"
    val optimistic =
        MessageWithUser(
            message =
                Message(
                    id = tempId,
                    user_id = currentUserId,
                    content = text,
                    timeCreated = localMs,
                    timeEdited = null,
                    isRead = false,
                    messageType = ChatMessageType.TEXT,
                    metadata = null,
                    localSentAt = localMs,
                    deliveryState = MessageDeliveryState.PENDING,
                ),
            user = User(id = currentUserId, name = "You", image = null, createdAt = 0L),
            isSent = true,
        )
    val next = _messages.value + optimistic
    _messages.value = next
    persistHubMessagesToDisk(next)
    return tempId
}

internal suspend fun HubChatViewModel.mergeMessages(rows: List<HubMessageRow>) {
    val filtered =
        rows
            .filter { it.hubId == hubId }
            .sortedBy { it.createdAt }
    prefetchSenderUi(filtered.map { it.userId })
    val merged = filtered.map { rowToMessageWithUser(it) }
    val next = merged + pendingOptimisticOutgoing(merged)
    _messages.value = next
    persistHubMessagesToDisk(next)
}

internal fun HubChatViewModel.clearHubSecureMediaCache(purgePersistentCache: Boolean = false) {
    _secureChatMediaLoadState.value = emptyMap()
    if (purgePersistentCache) {
        secureAudioPathCache.valuesSnapshot().forEach { path ->
            deleteSecureChatAudioTempFile(path)
        }
        secureAudioPathCache.clear()
        secureImageBytesCache.clear()
    }
}

internal fun HubChatViewModel.clearLocalHubState(clearDiskCache: Boolean = false) {
    sessionJob?.cancel()
    sessionJob = null
    _messages.value = emptyList()
    _draft.value = ""
    _occupantCount.value = 1
    _outOfBounds.value = false
    clearHubSecureMediaCache(purgePersistentCache = true)
    activeHubCache.removeActiveHub(hubId)
    if (clearDiskCache) {
        AppDataManager.clearHubThreadCache(hubId)
    }
}

internal suspend fun HubChatViewModel.prepareHubRealtimeAuth(forceRefresh: Boolean = true) {
    if (supabase.auth.currentSessionOrNull() == null) {
        runCatching { SupabaseConfig.importStoredSessionIfSdkEmpty(tokenStorage) }
    }
    runCatching { EnsureFreshAccessToken.get(tokenStorage, forceRefresh = forceRefresh) }
    tokenStorage.requireFreshHubJwt(forceRefresh = forceRefresh)
    rebindRealtimeSocket()
}

internal fun ChatApiClient.HubMessageApiDto.toHubMessageRow(): HubMessageRow =
    HubMessageRow(
        id = id,
        hubId = hubId,
        userId = userId,
        body = body,
        createdAt = createdAt,
        messageType = messageType,
        metadata = metadata,
    )

internal suspend fun HubChatViewModel.loadInitialMessages() {
    withContext(Dispatchers.Default) {
        try {
            val token = tokenStorage.requireFreshHubJwt()
            val thread = chatApi.fetchHubThread(hubId, token)
            thread.fold(
                onSuccess = { snapshot ->
                    if (snapshot.occupantCount > 0) {
                        _occupantCount.value = snapshot.occupantCount.coerceAtLeast(1)
                    }
                    prefetchSenderUi(snapshot.participantIds)
                    mergeMessages(snapshot.messages.map { it.toHubMessageRow() })
                    return@withContext
                },
                onFailure = { err ->
                    val msg = err.message.orEmpty()
                    if (msg.contains("NOT_A_PARTICIPANT")) {
                        participantDenied = true
                        _realtimeState.value =
                            HubRealtimeState.Error("Join this hub from the map to chat")
                        return@withContext
                    }
                    println("HubChatViewModel: hub thread API failed: ${err.redactedRestMessage()}")
                },
            )
            val rows =
                supabase
                    .from("hub_messages")
                    .select {
                        filter {
                            eq("hub_id", hubId)
                        }
                        order("created_at", Order.DESCENDING)
                        limit(HUB_INITIAL_MESSAGE_LIMIT)
                    }.decodeList<HubMessageRow>()
                    .asReversed()
            mergeMessages(rows)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("HubChatViewModel: load messages failed: ${e.redactedRestMessage()}")
        }
    }
}

internal suspend fun HubChatViewModel.loadMessagesAround(messageId: String) {
    withContext(Dispatchers.Default) {
        try {
            val token = runCatching { tokenStorage.requireFreshHubJwt() }.getOrNull()
            if (!token.isNullOrBlank()) {
                val thread = chatApi.fetchHubThread(hubId, token, aroundMessageId = messageId)
                thread.getOrNull()?.let { snapshot ->
                    prefetchSenderUi(snapshot.participantIds)
                    mergeMessages(snapshot.messages.map { it.toHubMessageRow() })
                    if (_messages.value.any { it.message.id == messageId }) return@withContext
                }
            }
            val target =
                supabase
                    .from("hub_messages")
                    .select {
                        filter {
                            eq("hub_id", hubId)
                            eq("id", messageId)
                        }
                        limit(1)
                    }.decodeList<HubMessageRow>()
                    .firstOrNull() ?: return@withContext
            val older =
                supabase
                    .from("hub_messages")
                    .select {
                        filter {
                            eq("hub_id", hubId)
                            lte("created_at", target.createdAt)
                        }
                        order("created_at", Order.DESCENDING)
                        limit(HUB_INITIAL_MESSAGE_LIMIT)
                    }.decodeList<HubMessageRow>()
                    .asReversed()
            val newer =
                supabase
                    .from("hub_messages")
                    .select {
                        filter {
                            eq("hub_id", hubId)
                            gt("created_at", target.createdAt)
                        }
                        order("created_at", Order.ASCENDING)
                        limit(40)
                    }.decodeList<HubMessageRow>()
            mergeMessages((older + newer + listOf(target)).distinctBy { it.id })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("HubChatViewModel: load around $messageId failed: ${e.redactedRestMessage()}")
        }
    }
}
