package compose.project.click.click.data.repository

import compose.project.click.click.crypto.MessageCrypto
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.api.ChatApiClient
import compose.project.click.click.data.models.*
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.notifications.ChatPushNotifier
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Presence
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.presenceChangeFlow
import io.github.jan.supabase.realtime.track
import io.github.jan.supabase.realtime.untrack
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Repository for chat operations
 * Uses the Python API for CRUD operations and Supabase Realtime for instant message updates
 */
class SupabaseChatRepository(
    private val apiClient: ChatApiClient = ChatApiClient(),
    private val tokenStorage: TokenStorage
) : ChatRepository {
    /** Lazy so [AppDataManager] construction does not eagerly create the Supabase client. */
    private val supabase by lazy { SupabaseConfig.client }
    private val supabaseRepository = SupabaseRepository()
    private val chatPushNotifier = ChatPushNotifier(tokenStorage)

    private val encryptionKeyCache = mutableMapOf<String, MessageCrypto.DerivedKeys>()

    private val ephemeralMutex = Mutex()
    private val ephemeralSessions = mutableMapOf<String, ChatEphemeralSession>()

    @Serializable
    private data class TypingBroadcastPayload(val userId: String)

    private data class ChatEphemeralSession(
        val channel: RealtimeChannel,
        val peerUserId: String,
        val typingFlow: MutableSharedFlow<TypingStatus>,
        val peerOnline: MutableStateFlow<Boolean>,
        val scope: CoroutineScope,
        val jobs: List<Job>,
    )

    private fun userIdFromPresence(p: Presence): String? =
        p.state["userId"]?.jsonPrimitive?.contentOrNull

    private suspend fun disposeEphemeralSession(session: ChatEphemeralSession) {
        session.jobs.forEach { it.cancel() }
        session.scope.cancel()
        runCatching { session.channel.untrack() }
        runCatching { session.channel.unsubscribe() }
    }

    private suspend fun awaitEphemeralSession(chatId: String): ChatEphemeralSession? {
        repeat(EPHEMERAL_SESSION_WAIT_STEPS) {
            ephemeralMutex.withLock { ephemeralSessions[chatId] }?.let { return it }
            delay(EPHEMERAL_SESSION_POLL_MS)
        }
        return null
    }

    @Serializable
    private data class ConnectionUserIdsRow(
        val id: String,
        val user_ids: List<String>
    )

    private suspend fun getEncryptionKeysForChat(chatId: String): MessageCrypto.DerivedKeys? {
        encryptionKeyCache[chatId]?.let { return it }
        return try {
            val chat = supabase.from("chats")
                .select(columns = Columns.list("connection_id")) {
                    filter { eq("id", chatId) }
                    limit(1)
                }
                .decodeList<ChatConnectionIdOnly>()
                .firstOrNull() ?: return null

            val connection = supabase.from("connections")
                .select(columns = Columns.list("id", "user_ids")) {
                    filter { eq("id", chat.connectionId) }
                    limit(1)
                }
                .decodeList<ConnectionUserIdsRow>()
                .firstOrNull() ?: return null

            val keys = MessageCrypto.deriveKeysForConnection(connection.id, connection.user_ids)
            encryptionKeyCache[chatId] = keys
            keys
        } catch (e: Exception) {
            println("ChatRepository: Failed to derive encryption keys: ${e.message}")
            null
        }
    }

    override fun cacheEncryptionKeys(chatId: String, connectionId: String, userIds: List<String>) {
        val keys = MessageCrypto.deriveKeysForConnection(connectionId, userIds)
        encryptionKeyCache[chatId] = keys
    }

    private fun decryptMessage(message: Message, keys: MessageCrypto.DerivedKeys?): Message {
        if (keys == null || !MessageCrypto.isEncrypted(message.content)) return message
        return message.copy(content = MessageCrypto.decryptContent(message.content, keys))
    }

    @Serializable
    private data class ChatConnectionIdOnly(
        @SerialName("connection_id")
        val connectionId: String
    )

    private suspend fun fetchUsersByIdsSafe(userIds: List<String>): List<User> {
        if (userIds.isEmpty()) return emptyList()

        return supabaseRepository.fetchUsersByIds(userIds)
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
    override suspend fun fetchUserChatsWithDetails(userId: String): List<ChatWithDetails> {
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

            val (usersById, chats) = coroutineScope {
                val usersDeferred = async { fetchUsersByIdsSafe(otherUserIds).associateBy { it.id } }
                val chatsDeferred = async {
                    supabase.from("chats")
                        .select {
                            filter {
                                isIn("connection_id", connectionIds)
                            }
                        }
                        .decodeList<ChatRow>()
                }

                usersDeferred.await() to chatsDeferred.await()
            }

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
                    name = "Connection",
                    email = null,
                    image = null,
                    createdAt = 0L
                )

                val rawLastMessage = chatRow?.let { firstByChatId[it.id]?.toMessage() }
                val keys = if (chatRow != null) {
                    val k = MessageCrypto.deriveKeysForConnection(connection.id, connection.user_ids)
                    encryptionKeyCache[chatRow.id] = k
                    k
                } else null
                val lastMessage = rawLastMessage?.let { decryptMessage(it, keys) }
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

    override suspend fun fetchMessagesForChat(chatId: String): List<Message> {
        return try {
            val keys = getEncryptionKeysForChat(chatId)
            supabase.from("messages")
                .select {
                    filter {
                        eq("chat_id", chatId)
                    }
                    order("time_created", Order.ASCENDING)
                }
                .decodeList<Message>()
                .map { decryptMessage(it, keys) }
        } catch (e: Exception) {
            println("Error fetching messages: ${e.message}")
            emptyList()
        }
    }

    override suspend fun sendMessage(chatId: String, userId: String, content: String): Message? {
        return try {
            val keys = getEncryptionKeysForChat(chatId)
            val wireContent = if (keys != null) MessageCrypto.encryptContent(content, keys) else content

            val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            val payload = buildJsonObject {
                put("chat_id", chatId)
                put("user_id", userId)
                put("content", wireContent)
                put("time_created", now)
            }

            val inserted = runCatching {
                supabase.from("messages")
                    .insert(payload) {
                        select()
                    }
                    .decodeSingle<Message>()
            }.getOrElse {
                supabase.from("messages")
                    .select {
                        filter {
                            eq("chat_id", chatId)
                            eq("user_id", userId)
                            eq("content", wireContent)
                            eq("time_created", now)
                        }
                        order("time_created", Order.DESCENDING)
                        limit(1)
                    }
                    .decodeList<Message>()
                    .firstOrNull()
                    ?: throw it
            }

            val decrypted = if (keys != null) decryptMessage(inserted, keys) else inserted

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

            runCatching {
                chatPushNotifier.notifyNewMessage(
                    chatId = chatId,
                    messageId = decrypted.id,
                    senderUserId = userId,
                ).getOrThrow()
            }.onFailure {
                println("ChatRepository: Failed to dispatch chat push: ${it.message}")
            }

            decrypted
        } catch (e: Exception) {
            println("Error sending message: ${e.message}")
            null
        }
    }

    override suspend fun ensureChatForConnection(connectionId: String): Chat? {
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

    override suspend fun sendMessageForConnection(connectionId: String, userId: String, content: String): Message? {
        val chat = ensureChatForConnection(connectionId) ?: return null
        return sendMessage(chat.id ?: return null, userId, content)
    }

    // Mark messages as read via API
    override suspend fun markMessagesAsRead(chatId: String, userId: String) {
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
     * Subscribe to messages in a chat using Supabase Realtime.
     * Returns a [Pair] of the [RealtimeChannel] (for cleanup) and a [Flow] of change events.
     * The caller MUST call `channel.subscribe()` after collecting the flow, or use the
     * convenience wrapper that does it automatically.
     *
     * Keys are eagerly resolved on the caller's coroutine context and captured in the
     * flow closure so that realtime callbacks (which may execute on background I/O
     * threads) never read the shared [encryptionKeyCache] directly — avoiding
     * thread-visibility issues in Kotlin/Native release builds.
     */
    override suspend fun subscribeToMessages(chatId: String): Pair<ChatMessageSubscription, Flow<MessageChangeEvent>> {
        val preloadedKeys = getEncryptionKeysForChat(chatId)

        val channel = supabase.channel("messages:$chatId")
        var resolvedKeys = preloadedKeys

        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
        }.mapNotNull { action ->
            val keys = resolvedKeys ?: getEncryptionKeysForChat(chatId).also { resolvedKeys = it }
            when (action) {
                is PostgresAction.Insert -> {
                    val row = action.decodeRecord<MessageRow>()
                    if (row.chatId == chatId) MessageChangeEvent.Insert(decryptMessage(row.toMessage(), keys)) else null
                }
                is PostgresAction.Update -> {
                    val row = action.decodeRecord<MessageRow>()
                    if (row.chatId == chatId) MessageChangeEvent.Update(decryptMessage(row.toMessage(), keys)) else null
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
        }

        return SupabaseMessageSubscription(channel) to changeFlow
    }

    // Fetch a specific chat by ID via API
    override suspend fun fetchChatById(chatId: String): Chat? {
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
    override suspend fun fetchChatWithDetails(chatId: String, currentUserId: String): ChatWithDetails? {
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
    override suspend fun fetchChatParticipants(chatId: String): List<User> {
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
    override suspend fun getUserById(userId: String): User? {
        return try {
            fetchUsersByIdsSafe(listOf(userId)).firstOrNull()
        } catch (e: Exception) {
            println("Error fetching user: ${e.message}")
            null
        }
    }

    // Update a message via API
    override suspend fun updateMessage(chatId: String, messageId: String, userId: String, content: String): Message? {
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
    override suspend fun deleteMessage(chatId: String, messageId: String, userId: String): Boolean {
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
    override suspend fun fetchReactionsForChat(chatId: String): List<MessageReaction> {
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
    override suspend fun addReaction(messageId: String, userId: String, reactionType: String): Boolean {
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
    override suspend fun removeReaction(messageId: String, userId: String, reactionType: String): Boolean {
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

    /**
     * Subscribe to reaction changes via Supabase Realtime.
     * Returns a [Pair] of the channel (for cleanup) and a [Flow] of change events.
     */
    override fun subscribeToReactions(chatId: String): Pair<ChatReactionSubscription, Flow<ReactionChangeEvent>> {
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

        return SupabaseReactionSubscription(channel) to changeFlow
    }

    override suspend fun sendTypingStatus(chatId: String, userId: String, isTyping: Boolean) {
        if (!isTyping) return
        val session = ephemeralMutex.withLock { ephemeralSessions[chatId] } ?: return
        try {
            session.channel.broadcast("typing", TypingBroadcastPayload(userId))
        } catch (e: Exception) {
            println("ChatRepository: typing broadcast failed: ${e.message}")
        }
    }

    override fun observeTypingStatus(chatId: String): Flow<TypingStatus> = flow {
        val session = awaitEphemeralSession(chatId) ?: run {
            awaitCancellation()
            return@flow
        }
        emitAll(session.typingFlow)
    }

    override suspend fun getTypingUsers(chatId: String): List<String> = emptyList()

    override suspend fun joinChatEphemeralChannel(chatId: String, currentUserId: String, peerUserId: String) {
        ephemeralMutex.withLock {
            ephemeralSessions[chatId]?.let { existing ->
                if (existing.peerUserId == peerUserId) return
                disposeEphemeralSession(existing)
                ephemeralSessions.remove(chatId)
            }

            val channel = supabase.channel("chat:$chatId") {
                broadcast {
                    receiveOwnBroadcasts = false
                }
            }

            val typingFlow = MutableSharedFlow<TypingStatus>(
                extraBufferCapacity = 32,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
            val peerOnline = MutableStateFlow(false)
            val trackedPresence = mutableSetOf<String>()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val broadcastFlow = channel.broadcastFlow<TypingBroadcastPayload>(event = "typing")
            val presenceFlow = channel.presenceChangeFlow()

            try {
                channel.subscribe(blockUntilSubscribed = true)
                channel.track(buildJsonObject { put("userId", currentUserId) })
            } catch (e: Exception) {
                println("ChatRepository: join chat ephemeral failed: ${e.message}")
                scope.cancel()
                return
            }

            val broadcastJob = scope.launch {
                try {
                    broadcastFlow.collect { payload ->
                        if (payload.userId != currentUserId) {
                            typingFlow.emit(TypingStatus(userId = payload.userId, isTyping = true))
                        }
                    }
                } catch (_: Exception) {
                }
            }
            val presenceJob = scope.launch {
                try {
                    presenceFlow.collect { action ->
                        action.joins.values.forEach { p ->
                            userIdFromPresence(p)?.let { trackedPresence.add(it) }
                        }
                        action.leaves.values.forEach { p ->
                            userIdFromPresence(p)?.let { trackedPresence.remove(it) }
                        }
                        peerOnline.value = peerUserId in trackedPresence
                    }
                } catch (_: Exception) {
                }
            }

            ephemeralSessions[chatId] = ChatEphemeralSession(
                channel = channel,
                peerUserId = peerUserId,
                typingFlow = typingFlow,
                peerOnline = peerOnline,
                scope = scope,
                jobs = listOf(broadcastJob, presenceJob),
            )
        }
    }

    override suspend fun leaveChatEphemeralChannel(chatId: String) {
        ephemeralMutex.withLock {
            val session = ephemeralSessions.remove(chatId) ?: return
            disposeEphemeralSession(session)
        }
    }

    override fun observePeerOnline(chatId: String, peerUserId: String): Flow<Boolean> = flow {
        val session = awaitEphemeralSession(chatId)
        if (session == null || session.peerUserId != peerUserId) {
            emit(false)
            return@flow
        }
        emitAll(session.peerOnline)
    }

    override suspend fun updateMessageStatus(messageId: String, status: String): Boolean {
        return try {
            val authToken = tokenStorage.getJwt() ?: return false
            apiClient.updateMessageStatus(messageId, status, authToken).getOrElse { false }
        } catch (e: Exception) { println("Error updating status: ${e.message}"); false }
    }

    override suspend fun forwardMessage(messageId: String, targetChatId: String, userId: String): Message? {
        return try {
            val authToken = tokenStorage.getJwt() ?: return null
            apiClient.forwardMessage(messageId, targetChatId, userId, authToken).getOrElse { null }
        } catch (e: Exception) { println("Error forwarding message: ${e.message}"); null }
    }

    override suspend fun searchMessages(chatId: String, query: String): List<Message> {
        return try {
            val allMessages = fetchMessagesForChat(chatId)
            allMessages.filter { it.content.contains(query, ignoreCase = true) }
        } catch (e: Exception) {
            println("Error searching messages: ${e.message}")
            emptyList()
        }
    }

    override suspend fun resolveChatIdForConnection(connectionId: String): String? {
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

    override suspend fun searchMessagesByConnectionId(connectionId: String, query: String): Pair<String?, List<Message>> {
        val resolvedChatId = resolveChatIdForConnection(connectionId)
        val messages = when {
            !resolvedChatId.isNullOrBlank() -> searchMessages(resolvedChatId, query)
            else -> emptyList()
        }
        return resolvedChatId to messages
    }
}

private class SupabaseMessageSubscription(private val channel: RealtimeChannel) : ChatMessageSubscription {
    override suspend fun attach() = channel.subscribe()
    override suspend fun detach() = channel.unsubscribe()
}

private class SupabaseReactionSubscription(private val channel: RealtimeChannel) : ChatReactionSubscription {
    override suspend fun attach() = channel.subscribe()
    override suspend fun detach() = channel.unsubscribe()
}

private const val EPHEMERAL_SESSION_POLL_MS = 50L
private const val EPHEMERAL_SESSION_WAIT_STEPS = 100
