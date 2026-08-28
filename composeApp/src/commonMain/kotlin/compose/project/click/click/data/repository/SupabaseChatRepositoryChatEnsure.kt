@file:Suppress("ktlint:standard:no-wildcard-imports")

package compose.project.click.click.data.repository

import compose.project.click.click.crypto.MessageCrypto
import compose.project.click.click.data.models.*
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal suspend fun SupabaseChatRepository.ensureChatForConnectionOnce(connectionId: String): Chat? {
    val existing =
        supabase
            .from("chats")
            .select {
                filter {
                    eq("connection_id", connectionId)
                }
                limit(1)
            }.decodeList<SupabaseChatRepository.ChatRow>()
            .firstOrNull()

    if (existing != null) {
        existing.connectionId?.let { rememberChatConnectionRouting(existing.id, it) }
        return Chat(
            id = existing.id,
            connectionId = existing.connectionId,
            groupId = existing.groupId,
            messages = emptyList(),
        )
    }

    return try {
        val inserted =
            supabase
                .from("chats")
                .insert(SupabaseChatRepository.ChatInsert(connectionId = connectionId)) {
                    select()
                }.decodeSingle<SupabaseChatRepository.ChatRow>()

        inserted.connectionId?.let { rememberChatConnectionRouting(inserted.id, it) }
        Chat(
            id = inserted.id,
            connectionId = inserted.connectionId,
            groupId = inserted.groupId,
            messages = emptyList(),
        )
    } catch (e: Exception) {
        // Unique-constraint race: another client created the row first.
        val raced =
            supabase
                .from("chats")
                .select {
                    filter { eq("connection_id", connectionId) }
                    limit(1)
                }.decodeList<SupabaseChatRepository.ChatRow>()
                .firstOrNull()
        if (raced != null) {
            raced.connectionId?.let { rememberChatConnectionRouting(raced.id, it) }
            return Chat(
                id = raced.id,
                connectionId = raced.connectionId,
                groupId = raced.groupId,
                messages = emptyList(),
            )
        }
        throw e
    }
}

internal suspend fun SupabaseChatRepository.ensureChatForGroupOnce(groupId: String): Chat? {
    val existing =
        supabase
            .from("chats")
            .select {
                filter { eq("group_id", groupId) }
                limit(1)
            }.decodeList<SupabaseChatRepository.ChatRow>()
            .firstOrNull()

    if (existing != null) {
        return Chat(
            id = existing.id,
            connectionId = existing.connectionId,
            groupId = existing.groupId,
            messages = emptyList(),
        )
    }

    return try {
        val inserted =
            supabase
                .from("chats")
                .insert(SupabaseChatRepository.GroupChatInsert(groupId = groupId)) {
                    select()
                }.decodeSingle<SupabaseChatRepository.ChatRow>()
        Chat(
            id = inserted.id,
            connectionId = inserted.connectionId,
            groupId = inserted.groupId,
            messages = emptyList(),
        )
    } catch (e: Exception) {
        val raced =
            supabase
                .from("chats")
                .select {
                    filter { eq("group_id", groupId) }
                    limit(1)
                }.decodeList<SupabaseChatRepository.ChatRow>()
                .firstOrNull()
        if (raced != null) {
            return Chat(
                id = raced.id,
                connectionId = raced.connectionId,
                groupId = raced.groupId,
                messages = emptyList(),
            )
        }
        throw e
    }
}

internal suspend fun SupabaseChatRepository.normalizeChatDetailsRow(row: ChatWithDetails): ChatWithDetails {
    return runCatching {
        if (!row.chat.id.isNullOrBlank()) {
            row
        } else if (row.groupClique != null) {
            val groupId = row.groupClique.groupId
            val ensured = ensureChatForGroup(groupId) ?: return@runCatching row
            row.copy(
                chat =
                    row.chat.copy(
                        id = ensured.id,
                        groupId = groupId,
                    ),
            )
        } else {
            val ensured = ensureChatForConnection(row.connection.id) ?: return@runCatching row
            row.copy(
                chat =
                    row.chat.copy(
                        id = ensured.id,
                        connectionId = row.connection.id,
                    ),
            )
        }
    }.getOrElse { e ->
        println("ChatRepository: normalize inbox chat row failed: ${e.redactedRestMessage()}")
        row
    }
}

/**
 * Resolves a thread when [chatId] is a **connection id** (deep links / push) rather than a `chats.id` row.
 * Inbox list fetch can still be empty on cold start, so this path must not depend on [fetchUserChatsWithDetails].
 */
internal suspend fun SupabaseChatRepository.loadChatWithDetailsByConnectionId(
    connectionId: String,
    currentUserId: String,
): ChatWithDetails? {
    val conn =
        supabase
            .from("connections")
            .select(columns = connectionsSelectWithEncounters) {
                filter { eq("id", connectionId) }
                order("encountered_at", Order.DESCENDING, referencedTable = connectionEncountersTable)
                limit(connectionEncountersPerConnection, referencedTable = connectionEncountersTable)
                limit(1)
            }.decodeList<Connection>()
            .map { it.withEncountersSortedNewestFirst() }
            .firstOrNull() ?: return null
    if (!conn.user_ids.contains(currentUserId)) return null
    val built =
        buildChatsWithDetailsForConnections(currentUserId, listOf(conn))
            .firstOrNull { it.connection.id == connectionId } ?: return null
    val ensured = ensureChatForConnection(connectionId) ?: return built
    return built.copy(
        chat =
            built.chat.copy(
                id = ensured.id,
                connectionId = connectionId,
            ),
    )
}

internal suspend fun SupabaseChatRepository.loadChatWithDetailsByRawId(
    chatId: String,
    currentUserId: String,
): ChatWithDetails? {
    val row =
        supabase
            .from("chats")
            .select {
                filter { eq("id", chatId) }
                limit(1)
            }.decodeList<SupabaseChatRepository.ChatRow>()
            .firstOrNull() ?: return null
    return when {
        row.groupId != null -> {
            rememberChatGroupRouting(row.id, row.groupId)
            fetchGroupUserChatsWithDetails(currentUserId).firstOrNull { it.chat.id == row.id }
        }
        !row.connectionId.isNullOrBlank() -> {
            rememberChatConnectionRouting(row.id, row.connectionId)
            val conn =
                supabase
                    .from("connections")
                    .select(columns = connectionsSelectWithEncounters) {
                        filter { eq("id", row.connectionId) }
                        order("encountered_at", Order.DESCENDING, referencedTable = connectionEncountersTable)
                        limit(connectionEncountersPerConnection, referencedTable = connectionEncountersTable)
                        limit(1)
                    }.decodeList<Connection>()
                    .map { it.withEncountersSortedNewestFirst() }
                    .firstOrNull() ?: return null
            buildChatsWithDetailsForConnections(currentUserId, listOf(conn))
                .firstOrNull { it.chat.id == row.id || it.connection.id == row.connectionId }
        }
        else -> null
    }
}

internal suspend fun SupabaseChatRepository.sendMessageImpl(
    chatId: String,
    userId: String,
    content: String,
    messageType: String,
    metadata: JsonElement?,
    clientLocalSentAtMs: Long?,
    connectionId: String?,
): Message? {
    return try {
        val crypto = resolveChatCrypto(chatId, userId)
        val wireContent =
            when {
                messageType == "call_log" || messageType == "beacon" -> content
                crypto is ChatSessionCaches.ResolvedChatCrypto.GroupMaster ->
                    MessageCrypto.encryptGroupMessageContent(content, crypto.masterKey)
                crypto is ChatSessionCaches.ResolvedChatCrypto.Pairwise ->
                    MessageCrypto.encryptContent(content, crypto.keys)
                else -> content
            }

        val now =
            kotlinx.datetime.Clock.System
                .now()
                .toEpochMilliseconds()
        val authToken =
            ensureFreshJwtForChat()
                ?: return null
        val enrichedMetadata = enrichMediaEncryptionMetadata(messageType, metadata)
        val resolvedConnectionId =
            connectionId
                ?: ChatSessionCaches.peekConnectionIdForChat(chatId)

        val persistedChatId =
            chatId.takeIf {
                compose.project.click.click.util
                    .isPersistedApiChatId(it)
            }

        suspend fun postOnce(
            token: String,
            useChatId: String?,
        ) = apiClient.sendMessage(
            chatId = useChatId.orEmpty(),
            userId = userId,
            content = wireContent,
            authToken = token,
            messageType = messageType,
            metadata = enrichedMetadata,
            localSentAtMs = clientLocalSentAtMs,
            connectionId = resolvedConnectionId,
        )

        var activeToken = authToken
        val firstSend = postOnce(activeToken, persistedChatId)
        val insertedWire =
            firstSend.getOrElse { firstError ->
                println(
                    "ChatRepository: sendMessage failed chatId=$chatId: ${firstError.redactedRestMessage()}",
                )
                var lastError: Throwable = firstError
                if (firstError.isAuthFailure()) {
                    val refreshed = refreshedJwtAfterAuthFailure()
                    if (refreshed != null) {
                        activeToken = refreshed
                        postOnce(activeToken, persistedChatId)
                            .getOrElse { authRetryError ->
                                lastError = authRetryError
                                null
                            }?.let { return@getOrElse it }
                    }
                }
                // Omit chat_id so gatekeeper resolves via connection_id (stale/missing chat rows).
                if (!resolvedConnectionId.isNullOrBlank()) {
                    postOnce(activeToken, useChatId = null).getOrElse { connError ->
                        println(
                            "ChatRepository: sendMessage connection fallback failed: " +
                                connError.redactedRestMessage(),
                        )
                        throw lastError
                    }
                } else {
                    throw lastError
                }
            }

        val decrypted = decryptMessage(insertedWire, crypto)

        try {
            supabase
                .from("chats")
                .update(
                    buildJsonObject {
                        put("updated_at", now)
                    },
                ) {
                    filter {
                        eq("id", chatId)
                    }
                }
        } catch (_: Exception) {
        }

        if (messageType != "call_log") {
            runCatching {
                chatPushNotifier
                    .notifyNewMessage(
                        chatId = chatId,
                        messageId = decrypted.id,
                        senderUserId = userId,
                        messagePreviewPlaintext = content,
                    ).getOrThrow()
            }.onFailure {
                println("ChatRepository: Failed to dispatch chat push: ${it.redactedRestMessage()}")
            }
        }

        decrypted
    } catch (e: Exception) {
        println("Error sending message: ${e.redactedRestMessage()}")
        // Propagate so ChatViewModel can surface the gatekeeper/API error body.
        throw e
    }
}

internal suspend fun SupabaseChatRepository.seedInboxChatRoutingImpl(chats: List<ChatWithDetails>) {
    for (chat in chats) {
        val chatId = chat.chat.id?.takeIf { it.isNotBlank() } ?: continue
        val groupId = chat.groupClique?.groupId
        if (groupId != null) {
            ChatSessionCaches.seedGroupRouting(chatId, groupId)
        } else {
            ChatSessionCaches.seedConnectionRouting(chatId, chat.connection.id)
        }
    }
}

// Fetch all chats for a user with details via API
internal suspend fun SupabaseChatRepository.fetchUserChatsWithDetailsImpl(userId: String): List<ChatWithDetails> =
    try {
        coroutineScope {
            val direct = async { fetchDirectUserChatsWithDetails(userId) }
            val groups =
                async {
                    runCatching { fetchGroupUserChatsWithDetails(userId) }
                        .onFailure { e ->
                            println("Error fetching group chats: ${e.redactedRestMessage()}")
                        }.getOrElse { emptyList() }
                }
            (direct.await() + groups.await()).sortedByDescending { d ->
                d.lastMessage?.timeCreated
                    ?: d.connection.last_message_at
                    ?: d.connection.created
            }
        }
    } catch (e: Exception) {
        println("Error fetching user chats: ${e.redactedRestMessage()}")
        emptyList()
    }

internal suspend fun SupabaseChatRepository.fetchDirectUserChatsWithDetailsImpl(userId: String): List<ChatWithDetails> =
    try {
        val (connections, archivedIds, hiddenIds) = getOrFetchJunctionData(userId)
        val activeRows = connections.filter { it.isActiveForUser(archivedIds, hiddenIds) }
        buildChatsWithDetailsForConnections(userId, activeRows)
    } catch (e: Exception) {
        println("Error fetching direct chats: ${e.redactedRestMessage()}")
        emptyList()
    }

internal suspend fun SupabaseChatRepository.ensureChatForConnectionImpl(connectionId: String): Chat? {
    return try {
        // Best-effort session prep — never hard-block with "no fresh JWT".
        runCatching { ensureFreshJwtForChat() }
        ensureChatForConnectionOnce(connectionId)
    } catch (e: Exception) {
        if (e.isAuthFailure()) {
            val refreshed = refreshedJwtAfterAuthFailure()
            if (refreshed != null) {
                return try {
                    ensureChatForConnectionOnce(connectionId)
                } catch (retry: Exception) {
                    println(
                        "Error ensuring chat for connection $connectionId after refresh: " +
                            retry.redactedRestMessage(),
                    )
                    null
                }
            }
        }
        println("Error ensuring chat for connection $connectionId: ${e.redactedRestMessage()}")
        null
    }
}

internal suspend fun SupabaseChatRepository.ensureChatForGroupImpl(groupId: String): Chat? {
    return try {
        runCatching { ensureFreshJwtForChat() }
        ensureChatForGroupOnce(groupId)
    } catch (e: Exception) {
        if (e.isAuthFailure()) {
            val refreshed = refreshedJwtAfterAuthFailure()
            if (refreshed != null) {
                return try {
                    ensureChatForGroupOnce(groupId)
                } catch (retry: Exception) {
                    println(
                        "Error ensuring chat for group $groupId after refresh: " +
                            retry.redactedRestMessage(),
                    )
                    null
                }
            }
        }
        println("Error ensuring chat for group $groupId: ${e.redactedRestMessage()}")
        null
    }
}
