package compose.project.click.click.data.repository

import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.api.ChatApiClient
import compose.project.click.click.data.models.*
import compose.project.click.click.data.storage.TokenStorage
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.broadcast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.github.jan.supabase.realtime.RealtimeChannel

/**
 * Repository for chat operations
 * Uses the Python API for CRUD operations and Supabase Realtime for instant message updates
 */
class ChatRepository(
    private val apiClient: ChatApiClient = ChatApiClient(),
    private val tokenStorage: TokenStorage
) {
    private val supabase = SupabaseConfig.client

    private suspend fun fetchUsersByIdsSafe(userIds: List<String>): List<User> {
        if (userIds.isEmpty()) return emptyList()

        val usersWithFullName = runCatching {
            supabase.from("users")
                .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("id", "name", "full_name", "email", "image")) {
                    filter {
                        isIn("id", userIds)
                    }
                }
                .decodeList<UserCore>()
        }.getOrNull()

        val rows = usersWithFullName ?: supabase.from("users")
            .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("id", "name", "email", "image")) {
                filter {
                    isIn("id", userIds)
                }
            }
            .decodeList<UserCore>()

        return rows.map { it.toUser() }
    }

    @Serializable
    private data class ChatRow(
        val id: String,
        @SerialName("connection_id")
        val connectionId: String,
        @SerialName("created_at")
        val createdAt: Long,
        @SerialName("updated_at")
        val updatedAt: Long
    )

    @Serializable
    private data class ChatInsert(
        @SerialName("connection_id")
        val connectionId: String
    )

    @Serializable
    private data class MessageRow(
        val id: String,
        @SerialName("chat_id")
        val chatId: String,
        @SerialName("user_id")
        val userId: String,
        val content: String,
        @SerialName("time_created")
        val timeCreated: Long,
        @SerialName("time_edited")
        val timeEdited: Long? = null,
        @SerialName("is_read")
        val isRead: Boolean = false
    ) {
        fun toMessage(): Message = Message(
            id = id,
            user_id = userId,
            content = content,
            timeCreated = timeCreated,
            timeEdited = timeEdited,
            isRead = isRead
        )
    }

    // Fetch all chats for a user with details via API
    suspend fun fetchUserChatsWithDetails(userId: String): List<ChatWithDetails> {
        return try {
            val connections = supabase.from("connections")
                .select {
                    filter {
                        contains("user_ids", listOf(userId))
                    }
                    order("created", Order.DESCENDING)
                }
                .decodeList<Connection>()

            if (connections.isEmpty()) return emptyList()

            val connectionIds = connections.map { it.id }
            val otherUserIds = connections
                .flatMap { it.user_ids }
                .filter { it != userId }
                .distinct()

            val authToken = tokenStorage.getJwt()
            val displayNamesById = if (!authToken.isNullOrBlank() && otherUserIds.isNotEmpty()) {
                apiClient.getDisplayNames(otherUserIds, authToken).getOrElse { emptyMap() }
            } else {
                emptyMap()
            }

            val users = fetchUsersByIdsSafe(otherUserIds)
            val usersById = users.associateBy { it.id }

            val chats = supabase.from("chats")
                .select {
                    filter {
                        isIn("connection_id", connectionIds)
                    }
                }
                .decodeList<ChatRow>()
            val chatByConnectionId = chats.associateBy { it.connectionId }

            val chatIds = chats.map { it.id }
            val messages = if (chatIds.isNotEmpty()) {
                supabase.from("messages")
                    .select {
                        filter {
                            isIn("chat_id", chatIds)
                        }
                        order("time_created", Order.DESCENDING)
                    }
                    .decodeList<MessageRow>()
            } else {
                emptyList()
            }

            val firstByChatId = linkedMapOf<String, MessageRow>()
            messages.forEach { row ->
                if (!firstByChatId.containsKey(row.chatId)) {
                    firstByChatId[row.chatId] = row
                }
            }

            val unreadByChatId = mutableMapOf<String, Int>()
            messages.forEach { row ->
                if (row.userId != userId && !row.isRead) {
                    unreadByChatId[row.chatId] = (unreadByChatId[row.chatId] ?: 0) + 1
                }
            }

            connections.mapNotNull { connection ->
                val chatRow = chatByConnectionId[connection.id]
                val otherUserId = connection.user_ids.firstOrNull { it != userId } ?: return@mapNotNull null
                val otherUser = usersById[otherUserId] ?: User(
                    id = otherUserId,
                    name = displayNamesById[otherUserId] ?: "Connection",
                    email = null,
                    image = null,
                    createdAt = 0L
                )

                val lastMessage = chatRow?.let { firstByChatId[it.id]?.toMessage() }
                val unreadCount = chatRow?.let { unreadByChatId[it.id] ?: 0 } ?: 0

                ChatWithDetails(
                    chat = Chat(
                        id = chatRow?.id,
                        connectionId = connection.id,
                        messages = emptyList()
                    ),
                    connection = connection,
                    otherUser = otherUser,
                    lastMessage = lastMessage,
                    unreadCount = unreadCount
                )
            }.sortedByDescending { chatDetails ->
                chatDetails.lastMessage?.timeCreated
                    ?: chatDetails.connection.last_message_at
                    ?: chatDetails.connection.created
            }
        } catch (e: Exception) {
            println("Error fetching user chats: ${e.message}")
            emptyList()
        }
    }

    // Fetch messages for a specific chat via API
    suspend fun fetchMessagesForChat(chatId: String): List<Message> {
        return try {
            supabase.from("messages")
                .select {
                    filter {
                        eq("chat_id", chatId)
                    }
                    order("time_created", Order.ASCENDING)
                }
                .decodeList<Message>()
        } catch (e: Exception) {
            println("Error fetching messages: ${e.message}")
            emptyList()
        }
    }

    // Send a new message via API
    suspend fun sendMessage(chatId: String, userId: String, content: String): Message? {
        return try {
            val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            val inserted = supabase.from("messages")
                .insert(
                    buildJsonObject {
                        put("chat_id", chatId)
                        put("user_id", userId)
                        put("content", content)
                        put("time_created", now)
                    }
                ) {
                    select()
                }
                .decodeSingle<Message>()

            try {
                supabase.from("chats")
                    .update(
                        buildJsonObject {
                            put("updated_at", now)
                        }
                    ) {
                        filter {
                            eq("id", chatId)
                        }
                    }
            } catch (_: Exception) {
            }

            inserted
        } catch (e: Exception) {
            println("Error sending message: ${e.message}")
            null
        }
    }

    suspend fun ensureChatForConnection(connectionId: String): Chat? {
        return try {
            val existing = supabase.from("chats")
                .select {
                    filter {
                        eq("connection_id", connectionId)
                    }
                    limit(1)
                }
                .decodeList<ChatRow>()
                .firstOrNull()

            if (existing != null) {
                return Chat(id = existing.id, connectionId = existing.connectionId, messages = emptyList())
            }

            val inserted = supabase.from("chats")
                .insert(ChatInsert(connectionId = connectionId)) {
                    select()
                }
                .decodeSingle<ChatRow>()

            Chat(id = inserted.id, connectionId = inserted.connectionId, messages = emptyList())
        } catch (e: Exception) {
            println("Error ensuring chat for connection $connectionId: ${e.message}")
            null
        }
    }

    suspend fun sendMessageForConnection(connectionId: String, userId: String, content: String): Message? {
        val chat = ensureChatForConnection(connectionId) ?: return null
        return sendMessage(chat.id ?: return null, userId, content)
    }

    // Mark messages as read via API
    suspend fun markMessagesAsRead(chatId: String, userId: String) {
        try {
            supabase.from("messages")
                .update(
                    buildJsonObject {
                        put("is_read", true)
                    }
                ) {
                    filter {
                        eq("chat_id", chatId)
                        neq("user_id", userId)
                        eq("is_read", false)
                    }
                }
        } catch (e: Exception) {
            println("Error marking messages as read: ${e.message}")
        }
    }

    /**
     * Event emitted by the realtime messages subscription.
     */
    sealed class MessageChangeEvent {
        data class Insert(val message: Message) : MessageChangeEvent()
        data class Update(val message: Message) : MessageChangeEvent()
        data class Delete(val messageId: String) : MessageChangeEvent()
    }

    /**
     * Subscribe to messages in a chat using Supabase Realtime.
     * Returns a [Pair] of the [RealtimeChannel] (for cleanup) and a [Flow] of change events.
     * The caller MUST call `channel.subscribe()` after collecting the flow, or use the
     * convenience wrapper that does it automatically.
     */
    fun subscribeToMessages(chatId: String): Pair<RealtimeChannel, Flow<MessageChangeEvent>> {
        val channel = supabase.channel("messages:$chatId")

        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
        }.mapNotNull { action ->
            when (action) {
                is PostgresAction.Insert -> {
                    val row = action.decodeRecord<MessageRow>()
                    if (row.chatId == chatId) MessageChangeEvent.Insert(row.toMessage()) else null
                }
                is PostgresAction.Update -> {
                    val row = action.decodeRecord<MessageRow>()
                    if (row.chatId == chatId) MessageChangeEvent.Update(row.toMessage()) else null
                }
                is PostgresAction.Delete -> {
                    try {
                        // In supabase-kt v3, old record is accessed via the raw oldRecord JSON
                        val id = action.oldRecord["id"]?.toString()?.trim('"')
                        if (id != null) MessageChangeEvent.Delete(id) else null
                    } catch (_: Exception) {
                        null
                    }
                }
                else -> null
            }
        }

        return channel to changeFlow
    }

    // Fetch a specific chat by ID via API
    suspend fun fetchChatById(chatId: String): Chat? {
        return try {
            val row = supabase.from("chats")
                .select {
                    filter {
                        eq("id", chatId)
                    }
                    limit(1)
                }
                .decodeList<ChatRow>()
                .firstOrNull() ?: return null

            Chat(id = row.id, connectionId = row.connectionId, messages = emptyList())
        } catch (e: Exception) {
            println("Error fetching chat: ${e.message}")
            null
        }
    }

    // Fetch chat with details by chat ID via API
    suspend fun fetchChatWithDetails(chatId: String, currentUserId: String): ChatWithDetails? {
        return try {
            val chatDetails = fetchUserChatsWithDetails(currentUserId)
                .firstOrNull { it.connection.id == chatId || it.chat.id == chatId }
                ?: return null

            if (!chatDetails.chat.id.isNullOrBlank()) {
                return chatDetails
            }

            val ensuredChat = ensureChatForConnection(chatDetails.connection.id) ?: return chatDetails
            chatDetails.copy(
                chat = chatDetails.chat.copy(
                    id = ensuredChat.id,
                    connectionId = chatDetails.connection.id
                )
            )
        } catch (e: Exception) {
            println("Error fetching chat with details: ${e.message}")
            null
        }
    }

    // Get participants for a chat via API
    suspend fun fetchChatParticipants(chatId: String): List<User> {
        return try {
            val chat = supabase.from("chats")
                .select {
                    filter {
                        eq("id", chatId)
                    }
                    limit(1)
                }
                .decodeList<ChatRow>()
                .firstOrNull() ?: return emptyList()

            val connection = supabase.from("connections")
                .select {
                    filter {
                        eq("id", chat.connectionId)
                    }
                    limit(1)
                }
                .decodeList<Connection>()
                .firstOrNull() ?: return emptyList()

            if (connection.user_ids.isEmpty()) return emptyList()

            fetchUsersByIdsSafe(connection.user_ids)
        } catch (e: Exception) {
            println("Error fetching participants: ${e.message}")
            emptyList()
        }
    }

    // Get user by ID - helper method for getting user details
    suspend fun getUserById(userId: String): User? {
        return try {
            fetchUsersByIdsSafe(listOf(userId)).firstOrNull()
        } catch (e: Exception) {
            println("Error fetching user: ${e.message}")
            null
        }
    }

    // Update a message via API
    suspend fun updateMessage(chatId: String, messageId: String, userId: String, content: String): Message? {
        return try {
            val authToken = tokenStorage.getJwt() ?: return null
            val result = apiClient.updateMessage(chatId, messageId, userId, content, authToken)
            result.getOrElse {
                println("Error updating message: ${it.message}")
                null
            }
        } catch (e: Exception) {
            println("Error updating message: ${e.message}")
            null
        }
    }

    // Delete a message via API
    suspend fun deleteMessage(chatId: String, messageId: String, userId: String): Boolean {
        return try {
            val authToken = tokenStorage.getJwt() ?: return false
            val result = apiClient.deleteMessage(chatId, messageId, userId, authToken)
            result.getOrElse {
                println("Error deleting message: ${it.message}")
                false
            }
        } catch (e: Exception) {
            println("Error deleting message: ${e.message}")
            false
        }
    }

    // ── Reaction CRUD via direct Supabase (bypasses Python API) ──────────────

    @Serializable
    private data class ReactionRow(
        val id: String = "",
        @SerialName("message_id")
        val messageId: String,
        @SerialName("user_id")
        val userId: String,
        @SerialName("reaction_type")
        val reactionType: String,
        @SerialName("created_at")
        val createdAt: Long
    ) {
        fun toMessageReaction(): MessageReaction = MessageReaction(
            id = id,
            messageId = messageId,
            userId = userId,
            reactionType = reactionType,
            createdAt = createdAt
        )
    }

    /** Fetch all reactions for messages in a given chat. */
    suspend fun fetchReactionsForChat(chatId: String): List<MessageReaction> {
        return try {
            // Get all message IDs for this chat first
            val messageIds = supabase.from("messages")
                .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("id")) {
                    filter { eq("chat_id", chatId) }
                }
                .decodeList<MessageIdOnly>()
                .map { it.id }

            if (messageIds.isEmpty()) return emptyList()

            supabase.from("message_reactions")
                .select {
                    filter { isIn("message_id", messageIds) }
                }
                .decodeList<ReactionRow>()
                .map { it.toMessageReaction() }
        } catch (e: Exception) {
            println("Error fetching reactions: ${e.message}")
            emptyList()
        }
    }

    @Serializable
    private data class MessageIdOnly(val id: String)

    @Serializable
    private data class ChatIdOnly(val id: String)

    /** Add a reaction. Uses upsert with the unique constraint to avoid duplicates. */
    suspend fun addReaction(messageId: String, userId: String, reactionType: String): Boolean {
        return try {
            val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            supabase.from("message_reactions")
                .insert(
                    buildJsonObject {
                        put("message_id", messageId)
                        put("user_id", userId)
                        put("reaction_type", reactionType)
                        put("created_at", now)
                    }
                )
            true
        } catch (e: Exception) {
            println("Error adding reaction: ${e.message}")
            false
        }
    }

    /** Remove a reaction by matching message_id, user_id, reaction_type. */
    suspend fun removeReaction(messageId: String, userId: String, reactionType: String): Boolean {
        return try {
            supabase.from("message_reactions")
                .delete {
                    filter {
                        eq("message_id", messageId)
                        eq("user_id", userId)
                        eq("reaction_type", reactionType)
                    }
                }
            true
        } catch (e: Exception) {
            println("Error removing reaction: ${e.message}")
            false
        }
    }

    // ── Realtime subscription for reactions ────────────────────────────────────

    sealed class ReactionChangeEvent {
        data class Insert(val reaction: MessageReaction) : ReactionChangeEvent()
        data class Delete(val reactionId: String, val messageId: String) : ReactionChangeEvent()
    }

    /**
     * Subscribe to reaction changes via Supabase Realtime.
     * Returns a [Pair] of the channel (for cleanup) and a [Flow] of change events.
     */
    fun subscribeToReactions(chatId: String): Pair<RealtimeChannel, Flow<ReactionChangeEvent>> {
        val channel = supabase.channel("reactions:$chatId")

        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "message_reactions"
        }.mapNotNull { action ->
            when (action) {
                is PostgresAction.Insert -> {
                    try {
                        val row = action.decodeRecord<ReactionRow>()
                        ReactionChangeEvent.Insert(row.toMessageReaction())
                    } catch (_: Exception) { null }
                }
                is PostgresAction.Delete -> {
                    try {
                        val id = action.oldRecord["id"]?.toString()?.trim('"')
                        val msgId = action.oldRecord["message_id"]?.toString()?.trim('"')
                        if (id != null && msgId != null) ReactionChangeEvent.Delete(id, msgId) else null
                    } catch (_: Exception) { null }
                }
                else -> null
            }
        }

        return channel to changeFlow
    }

    private val typingChannels = mutableMapOf<String, RealtimeChannel>()

    suspend fun sendTypingStatus(chatId: String, userId: String, isTyping: Boolean) {
        try {
            var channel = typingChannels[chatId]
            if (channel == null) {
                channel = supabase.channel("typing:$chatId")
                // We don't call subscribe() here if it's causing issues, 
                // or we ensure it's called correctly.
                // In many versions of supabase-kt, broadcast() will auto-subscribe if needed.
                typingChannels[chatId] = channel
            }
            channel.broadcast(
                event = "typing",
                message = buildJsonObject {
                    put("userId", userId)
                    put("isTyping", isTyping)
                }
            )
        } catch (e: Exception) {
            println("Error sending typing status: ${e.message}")
        }
    }

    fun observeTypingStatus(chatId: String): Flow<TypingStatus> {
        val channel = typingChannels.getOrPut(chatId) {
            supabase.channel("typing:$chatId")
        }
        return channel.broadcastFlow<TypingStatus>("typing")
    }

    @Serializable
    data class TypingStatus(val userId: String, val isTyping: Boolean)

    suspend fun getTypingUsers(chatId: String): List<String> {
        // This can be kept as a fallback or removed if using observeTypingStatus exclusively
        return try {
            val authToken = tokenStorage.getJwt() ?: return emptyList()
            apiClient.getTypingUsers(chatId, authToken).getOrElse { emptyList() }
        } catch (e: Exception) { println("Error getting typing users: ${e.message}"); emptyList() }
    }

    suspend fun updateMessageStatus(messageId: String, status: String): Boolean {
        return try {
            val authToken = tokenStorage.getJwt() ?: return false
            apiClient.updateMessageStatus(messageId, status, authToken).getOrElse { false }
        } catch (e: Exception) { println("Error updating status: ${e.message}"); false }
    }

    suspend fun forwardMessage(messageId: String, targetChatId: String, userId: String): Message? {
        return try {
            val authToken = tokenStorage.getJwt() ?: return null
            apiClient.forwardMessage(messageId, targetChatId, userId, authToken).getOrElse { null }
        } catch (e: Exception) { println("Error forwarding message: ${e.message}"); null }
    }

    suspend fun searchMessages(chatId: String, query: String): List<Message> {
        // Use direct Supabase query with ilike for reliable search (bypasses Python API)
        return try {
            supabase.from("messages")
                .select {
                    filter {
                        eq("chat_id", chatId)
                        ilike("content", "%$query%")
                    }
                    order("time_created", Order.DESCENDING)
                    limit(50)
                }
                .decodeList<Message>()
        } catch (e: Exception) {
            println("Error searching messages: ${e.message}")
            emptyList()
        }
    }

    suspend fun resolveChatIdForConnection(connectionId: String): String? {
        return try {
            val rows = supabase.from("chats")
                .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("id")) {
                    filter {
                        eq("connection_id", connectionId)
                    }
                    limit(1)
                }
                .decodeList<ChatIdOnly>()
            rows.firstOrNull()?.id
        } catch (e: Exception) {
            println("Error resolving chat id for connection $connectionId: ${e.message}")
            null
        }
    }

    suspend fun searchMessagesByConnectionId(connectionId: String, query: String): Pair<String?, List<Message>> {
        val resolvedChatId = resolveChatIdForConnection(connectionId)
        val messages = when {
            !resolvedChatId.isNullOrBlank() -> searchMessages(resolvedChatId, query)
            else -> emptyList()
        }
        return resolvedChatId to messages
    }
}
