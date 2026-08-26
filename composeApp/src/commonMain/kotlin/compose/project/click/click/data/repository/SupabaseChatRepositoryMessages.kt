@file:Suppress("ktlint:standard:no-wildcard-imports", "ktlint:standard:max-line-length")

package compose.project.click.click.data.repository

import compose.project.click.click.crypto.MessageCrypto
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.models.*
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

internal suspend fun SupabaseChatRepository.fetchMessagesForChatImpl(
    chatId: String,
    viewerUserId: String?,
    limit: Int?,
    beforeTimeCreated: Long?,
): List<Message>? =
    try {
        // Ensure SDK session is imported so PostgREST RLS returns rows (not empty []).
        ensureFreshJwtForChat()
        val crypto = resolveChatCrypto(chatId, viewerUserId)
        val rows =
            when {
                limit != null && limit > 0 -> {
                    supabase
                        .from("messages")
                        .select {
                            filter {
                                eq("chat_id", chatId)
                                beforeTimeCreated?.let { ts -> lt("time_created", ts) }
                            }
                            order("time_created", Order.DESCENDING)
                            limit(limit.toLong())
                        }.decodeList<Message>()
                        .asReversed()
                }
                else -> {
                    supabase
                        .from("messages")
                        .select {
                            filter {
                                eq("chat_id", chatId)
                            }
                            order("time_created", Order.ASCENDING)
                        }.decodeList<Message>()
                }
            }
        val decrypted =
            withContext(Dispatchers.Default) {
                rows.map { decryptMessageOnCurrentThread(it, crypto) }
            }
        resolveListKeyForChat(chatId)?.let { connectionId ->
            // Pagination returns an older page only — merge into hot cache instead of
            // replacing the full timeline (which would drop newer messages until leave).
            if (beforeTimeCreated != null) {
                ChatSessionCaches.messageTimelineCache.mergeMessages(connectionId, decrypted)
            } else {
                storeCachedMessageTimeline(connectionId, decrypted)
            }
        }
        decrypted
    } catch (e: Exception) {
        println("Error fetching messages: ${e.redactedRestMessage()}")
        null
    }

/**
 * Subscribe to [messages] and [message_reactions] on a **single** Realtime channel, then
 * merge both Postgres change streams. This mirrors the web client (one `subscribe()` after
 * registering all `postgres_changes` listeners) and avoids missing peer reaction events that
 * can occur when reactions use a separate channel on mobile.
 */
internal suspend fun SupabaseChatRepository.subscribeToMessagesImpl(
    chatId: String,
    viewerUserId: String,
): Pair<ChatMessageSubscription, Flow<ChatRealtimeEvent>> {
    ensureFreshJwtForChat()
    runCatching { supabase.realtime.connect() }
    val preloaded = resolveChatCrypto(chatId, viewerUserId)

    val channel = supabase.channel("messages:$chatId")
    var resolvedCrypto = preloaded

    val messageFlow =
        channel
            .postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "messages"
            }.mapNotNull { action ->
                val crypto =
                    resolvedCrypto
                        ?: resolveChatCrypto(chatId, viewerUserId).also { resolvedCrypto = it }
                when (action) {
                    is PostgresAction.Insert -> {
                        val row = action.decodeRecord<SupabaseChatRepository.MessageRow>()
                        if (row.chatId == chatId) MessageChangeEvent.Insert(decryptMessage(row.toMessage(), crypto)) else null
                    }
                    is PostgresAction.Update -> {
                        val row = action.decodeRecord<SupabaseChatRepository.MessageRow>()
                        if (row.chatId == chatId) MessageChangeEvent.Update(decryptMessage(row.toMessage(), crypto)) else null
                    }
                    is PostgresAction.Delete -> {
                        try {
                            val id = action.oldRecord["id"]?.toString()?.trim('"')
                            if (id != null) MessageChangeEvent.Delete(id) else null
                        } catch (_: Exception) {
                            null
                        }
                    }
                    else -> null
                }
            }.map { ChatRealtimeEvent.Message(it) }

    val reactionFlow =
        channel
            .postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "message_reactions"
            }.mapNotNull { action ->
                when (action) {
                    is PostgresAction.Insert -> {
                        try {
                            val row = action.decodeRecord<SupabaseChatRepository.ReactionRow>()
                            ChatRealtimeEvent.Reaction(ReactionChangeEvent.Insert(row.toMessageReaction()))
                        } catch (_: Exception) {
                            null
                        }
                    }
                    is PostgresAction.Delete -> {
                        try {
                            val id = action.oldRecord["id"]?.toString()?.trim('"')
                            val msgId = action.oldRecord["message_id"]?.toString()?.trim('"')
                            if (id != null && msgId != null) {
                                ChatRealtimeEvent.Reaction(ReactionChangeEvent.Delete(id, msgId))
                            } else {
                                null
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }
                    else -> null
                }
            }

    val merged = merge(messageFlow, reactionFlow)
    return SupabaseMessageSubscription(channel) to merged
}

internal suspend fun SupabaseChatRepository.subscribeToMessageInsertsImpl(): Pair<ChatMessageSubscription, Flow<MessageListInsertEvent>> {
    ensureFreshJwtForChat()
    runCatching { supabase.realtime.connect() }
    val channel = supabase.channel("clicks:msg-list:${Clock.System.now().toEpochMilliseconds()}")
    // Register postgres listeners before subscribe() — same contract as [subscribeToMessages].
    // Do NOT defer postgresChangeFlow into channelFlow/callbackFlow collect; attach() runs first
    // in RealtimeCoordinator and Supabase rejects listeners registered after join.
    val flow =
        channel
            .postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "messages"
            }.transform { action ->
                val (row, emitToInbox) =
                    when (action) {
                        // Inbox list bumps are Insert-only. Updates (read/delivered on older rows after
                        // load-older) must not emit as list events — they regress preview/"Nw ago".
                        // Per-chat subscribeToMessages still handles Update for the open thread.
                        is PostgresAction.Insert ->
                            (runCatching { action.decodeRecord<SupabaseChatRepository.MessageRow>() }.getOrNull() ?: return@transform) to
                                true
                        is PostgresAction.Update ->
                            (runCatching { action.decodeRecord<SupabaseChatRepository.MessageRow>() }.getOrNull() ?: return@transform) to
                                false
                        else -> return@transform
                    }
                val listKey =
                    resolveListKeyForChat(row.chatId)
                        ?: ChatSessionCaches.peekListKeyForChatSync(row.chatId)
                        ?: row.chatId
                val cached = ChatSessionCaches.getCrypto(row.chatId)
                // For group cliques listKey is a groupId, so pairwise key derivation
                // can never succeed — fall back to resolveChatCrypto, which routes
                // group chats through the wrapped master key and caches the result.
                val crypto =
                    cached
                        ?: getEncryptionKeysForConnection(listKey)?.let { ChatSessionCaches.ResolvedChatCrypto.Pairwise(it) }
                        ?: resolveChatCrypto(row.chatId, supabase.auth.currentUserOrNull()?.id)
                val rawMessage = row.toMessage()
                val message =
                    when {
                        crypto != null -> decryptMessage(rawMessage, crypto)
                        MessageCrypto.isAnyE2eeWireContent(rawMessage.content) ->
                            rawMessage.copy(content = "New message")
                        else -> rawMessage
                    }
                mergeCachedTimelineMessage(listKey, message)
                if (emitToInbox) {
                    emit(MessageListInsertEvent(connectionId = listKey, chatId = row.chatId, message = message))
                }
            }
    return SupabaseMessageSubscription(channel) to flow
}

// Update a message via API
internal suspend fun SupabaseChatRepository.updateMessageImpl(
    chatId: String,
    messageId: String,
    userId: String,
    content: String,
): Message? {
    return try {
        val authToken = ensureFreshJwtForChat() ?: return null
        val result = apiClient.updateMessage(chatId, messageId, userId, content, authToken)
        result
            .recoverCatching { firstErr ->
                val retried = refreshedJwtAfterAuthFailure() ?: throw firstErr
                apiClient.updateMessage(chatId, messageId, userId, content, retried).getOrThrow()
            }.getOrElse {
                println("Error updating message: ${it.redactedRestMessage()}")
                null
            }
    } catch (e: Exception) {
        println("Error updating message: ${e.redactedRestMessage()}")
        null
    }
}

// Delete a message via API
internal suspend fun SupabaseChatRepository.deleteMessageImpl(
    chatId: String,
    messageId: String,
    userId: String,
): Boolean {
    return try {
        val authToken = ensureFreshJwtForChat() ?: return false
        val result = apiClient.deleteMessage(chatId, messageId, userId, authToken)
        result
            .recoverCatching { firstErr ->
                val retried = refreshedJwtAfterAuthFailure() ?: throw firstErr
                apiClient.deleteMessage(chatId, messageId, userId, retried).getOrThrow()
            }.getOrElse {
                println("Error deleting message: ${it.redactedRestMessage()}")
                false
            }
    } catch (e: Exception) {
        println("Error deleting message: ${e.redactedRestMessage()}")
        false
    }
}

/** Fetch reactions for messages in a given chat (optionally scoped to [messageIds]). */
internal suspend fun SupabaseChatRepository.fetchReactionsForChatImpl(
    chatId: String,
    messageIds: List<String>?,
): List<MessageReaction> {
    return try {
        // Never query uuid columns with optimistic temp-* ids (PostgREST 22P02).
        val ids =
            messageIds
                ?.map { it.trim() }
                ?.filter {
                    compose.project.click.click.util
                        .isPersistedApiUuid(it)
                }?.distinct()
                ?.takeIf { it.isNotEmpty() }
                ?: run {
                    if (!compose.project.click.click.util
                            .isPersistedApiChatId(chatId)
                    ) {
                        return emptyList()
                    }
                    supabase
                        .from("messages")
                        .select(
                            columns =
                                io.github.jan.supabase.postgrest.query.Columns
                                    .list("id"),
                        ) {
                            filter { eq("chat_id", chatId) }
                        }.decodeList<SupabaseChatRepository.MessageIdOnly>()
                        .map { it.id }
                        .filter {
                            compose.project.click.click.util
                                .isPersistedApiUuid(it)
                        }
                }

        if (ids.isEmpty()) return emptyList()

        supabase
            .from("message_reactions")
            .select {
                filter { isIn("message_id", ids) }
            }.decodeList<SupabaseChatRepository.ReactionRow>()
            .map { it.toMessageReaction() }
    } catch (e: Exception) {
        println("Error fetching reactions: ${e.redactedRestMessage()}")
        emptyList()
    }
}

internal suspend fun SupabaseChatRepository.forwardMessageImpl(
    messageId: String,
    targetChatId: String,
    userId: String,
): Message? {
    return try {
        val authToken = ensureFreshJwtForChat() ?: return null
        apiClient
            .forwardMessage(messageId, targetChatId, userId, authToken)
            .recoverCatching {
                val retried = refreshedJwtAfterAuthFailure() ?: throw it
                apiClient.forwardMessage(messageId, targetChatId, userId, retried).getOrThrow()
            }.getOrElse { null }
    } catch (e: Exception) {
        println("Error forwarding message: ${e.redactedRestMessage()}")
        null
    }
}

internal suspend fun SupabaseChatRepository.searchMessagesImpl(
    chatId: String,
    query: String,
): List<Message> {
    return try {
        val allMessages = fetchMessagesForChat(chatId, null) ?: return emptyList()
        allMessages.filter { it.content.contains(query, ignoreCase = true) }
    } catch (e: Exception) {
        println("Error searching messages: ${e.redactedRestMessage()}")
        emptyList()
    }
}

internal suspend fun SupabaseChatRepository.unifiedSearchSupplementImpl(
    viewerUserId: String,
    peerUserIds: List<String>,
): UnifiedSearchSupplement {
    if (viewerUserId.isBlank() || peerUserIds.isEmpty()) return UnifiedSearchSupplement.EMPTY
    val peers = distinctPeerIdsForSearch(peerUserIds)
    if (peers.isEmpty()) return UnifiedSearchSupplement.EMPTY
    return try {
        UnifiedSearchSupplement(
            peerInterestTagsByUserId = loadInterestTagsForPeers(peers),
            activePeerIntentsByUserId = loadActiveIntentsForPeers(peers),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("ChatRepository: unifiedSearchSupplement failed: ${e.redactedRestMessage()}")
        UnifiedSearchSupplement.EMPTY
    }
}

// Fetch chat with details by chat ID via API
internal suspend fun SupabaseChatRepository.fetchChatWithDetailsImpl(
    chatId: String,
    currentUserId: String,
): ChatWithDetails? {
    compose.project.click.click.data.AppDataManager
        .chatInboxRowForThread(chatId, currentUserId)
        ?.let { row -> return normalizeChatDetailsRow(row) }

    val byChatRow =
        runCatching {
            loadChatWithDetailsByRawId(chatId, currentUserId)
        }.onFailure { e ->
            println("ChatRepository: loadChatWithDetailsByRawId failed: ${e.redactedRestMessage()}")
        }.getOrNull()
    if (byChatRow != null) return byChatRow

    val byConnection =
        runCatching {
            loadChatWithDetailsByConnectionId(chatId, currentUserId)
        }.onFailure { e ->
            println("ChatRepository: loadChatWithDetailsByConnectionId failed: ${e.redactedRestMessage()}")
        }.getOrNull()
    if (byConnection != null) return byConnection

    val fromList =
        runCatching {
            fetchUserChatsWithDetails(currentUserId)
                .firstOrNull { it.connection.id == chatId || it.chat.id == chatId }
        }.onFailure { e ->
            println(
                "ChatRepository: inbox lookup failed for chat details " +
                    "(${e.redactedRestMessage()}); trying direct id paths",
            )
        }.getOrNull()

    if (fromList != null) {
        return normalizeChatDetailsRow(fromList)
    }

    return null
}

// Get participants for a chat via API
internal suspend fun SupabaseChatRepository.fetchChatParticipantsImpl(chatId: String): List<User> {
    return try {
        val chat =
            supabase
                .from("chats")
                .select {
                    filter {
                        eq("id", chatId)
                    }
                    limit(1)
                }.decodeList<SupabaseChatRepository.ChatRow>()
                .firstOrNull() ?: return emptyList()

        val userIds =
            when {
                chat.groupId != null -> {
                    supabase
                        .from("group_members")
                        .select(columns = Columns.list("user_id")) {
                            filter { eq("group_id", chat.groupId) }
                            limit(100)
                        }.decodeList<SupabaseChatRepository.GroupMemberUidRow>()
                        .map { it.userId }
                }
                !chat.connectionId.isNullOrBlank() -> {
                    val connection =
                        supabase
                            .from("connections")
                            .select(columns = connectionsSelectWithEncounters) {
                                filter {
                                    eq("id", chat.connectionId)
                                }
                                order("encountered_at", Order.DESCENDING, referencedTable = connectionEncountersTable)
                                limit(connectionEncountersPerConnection, referencedTable = connectionEncountersTable)
                                limit(1)
                            }.decodeList<Connection>()
                            .map { it.withEncountersSortedNewestFirst() }
                            .firstOrNull() ?: return emptyList()
                    connection.user_ids
                }
                else -> return emptyList()
            }
        if (userIds.isEmpty()) return emptyList()
        fetchUsersByIdsSafe(userIds)
    } catch (e: Exception) {
        println("Error fetching participants: ${e.redactedRestMessage()}")
        emptyList()
    }
}

internal suspend fun SupabaseChatRepository.fetchArchivedUserChatsWithDetailsImpl(userId: String): List<ChatWithDetails> =
    try {
        val (connections, archivedIds, hiddenIds) = getOrFetchJunctionData(userId)
        val archivedRows =
            connections.filter {
                it.isArchivedChannelForUser(archivedIds, hiddenIds)
            }

        buildChatsWithDetailsForConnections(userId, archivedRows)
    } catch (e: Exception) {
        println("Error fetching archived user chats: ${e.redactedRestMessage()}")
        emptyList()
    }

internal suspend fun SupabaseChatRepository.decryptGroupChatPreviewImpl(
    chatId: String,
    viewerUserId: String,
): Message? {
    return try {
        val crypto = resolveChatCrypto(chatId, viewerUserId) ?: return null
        val rows = fetchLatestMessageRowPerChat(listOf(chatId))
        val raw = rows[chatId]?.toMessage() ?: return null
        decryptMessage(raw, crypto)
    } catch (_: Exception) {
        null
    }
}

// Mark messages as read via click-web (service role); direct PostgREST updates hit RLS on mobile.
internal suspend fun SupabaseChatRepository.markMessagesAsReadImpl(
    chatId: String,
    userId: String,
) {
    if (chatId.isBlank() || userId.isBlank()) return
    try {
        val jwt =
            ensureFreshJwtForChat()
                ?: tokenStorage.getJwt()?.trim()?.takeIf { it.isNotEmpty() }
                ?: return
        apiClient.markChatAsRead(chatId, jwt).onFailure { e ->
            // Do not fall back to the legacy Flask /api/chats/:id/mark_read host — it often
            // times out on simulator LAN and is not the read-receipt SSOT anymore.
            println("markChatAsRead failed: ${e.redactedRestMessage()}")
        }
    } catch (e: Exception) {
        println("Error marking messages as read: ${e.redactedRestMessage()}")
    }
}

internal suspend fun SupabaseChatRepository.markChatAsUnreadImpl(chatId: String) {
    if (chatId.isBlank()) return
    try {
        val jwt = tokenStorage.getJwt()?.trim()?.takeIf { it.isNotEmpty() } ?: return
        apiClient.markChatAsUnread(chatId, jwt).onFailure { e ->
            println("markChatAsUnread failed: ${e.redactedRestMessage()}")
        }
    } catch (e: Exception) {
        println("Error marking chat as unread: ${e.redactedRestMessage()}")
    }
}

internal suspend fun SupabaseChatRepository.markMessagesDeliveredImpl(
    chatId: String,
    messageIds: List<String>,
) {
    if (chatId.isBlank() || messageIds.isEmpty()) return
    try {
        val jwt = tokenStorage.getJwt()?.trim()?.takeIf { it.isNotEmpty() } ?: return
        apiClient.markMessagesDelivered(chatId, messageIds, jwt).onFailure { e ->
            println("markMessagesDelivered failed: ${e.redactedRestMessage()}")
        }
    } catch (e: Exception) {
        println("Error marking messages delivered: ${e.redactedRestMessage()}")
    }
}
