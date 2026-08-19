@file:Suppress("ktlint:standard:no-wildcard-imports", "ktlint:standard:backing-property-naming")

package compose.project.click.click.data.repository

import compose.project.click.click.chat.attachments.AttachmentCrypto
import compose.project.click.click.crypto.MessageCrypto
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.CHAT_ATTACHMENTS_BUCKET
import compose.project.click.click.data.CHAT_MEDIA_BUCKET
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.api.ChatApiClient
import compose.project.click.click.data.models.*
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.notifications.ChatPushNotifier
import compose.project.click.click.util.chatMediaDispatcher
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Repository for chat operations
 * Uses the Python API for CRUD operations and Supabase Realtime for instant message updates
 */
class SupabaseChatRepository(
    internal val tokenStorage: TokenStorage,
) : ChatRepository {
    internal val apiClient = ChatApiClient(tokenStorage = tokenStorage)

    /** Lazy so [AppDataManager] construction does not eagerly create the Supabase client. */
    internal val supabase by lazy { SupabaseConfig.client }
    internal val supabaseRepository = SupabaseRepository()
    internal val authRepository = AuthRepository(tokenStorage = tokenStorage)
    internal val chatPushNotifier = ChatPushNotifier(tokenStorage)
    internal val connectionEncountersPerConnection = 25L
    internal val connectionEncountersTable = "connection_encounters"
    internal val connectionsSelectWithEncounters = Columns.raw("*, connection_encounters(*)")

    internal fun Connection.withEncountersSortedNewestFirst(): Connection =
        copy(connectionEncounters = connectionEncounters.mergeRichestEncounterEvents().sortedByDescending { it.encounteredAt })

    internal val ephemeralMutex = Mutex()
    internal val ephemeralSessions = mutableMapOf<String, ChatEphemeralSession>()

    /** Bumped on leave / supersede so an in-flight join does not reinstall a disposed session. */
    internal val ephemeralJoinGeneration = mutableMapOf<String, Int>()

    internal val globalPresenceMutex = Mutex()
    internal var globalPresenceSession: GlobalPresenceSession? = null
    internal var presenceReconnectJob: Job? = null
    internal val _onlineUsers = MutableStateFlow<Set<String>>(emptySet())
    override val onlineUsers: StateFlow<Set<String>> = _onlineUsers.asStateFlow()
    internal val _presenceHealth = MutableStateFlow(PresenceHealth.Idle)
    override val presenceHealth: StateFlow<PresenceHealth> = _presenceHealth.asStateFlow()

    override val messageTimelineCache: ChatTimelineCache
        get() = ChatSessionCaches.messageTimelineCache

    override fun peekCachedMessageTimeline(connectionId: String): List<Message>? = ChatSessionCaches.messageTimelineCache.peek(connectionId)

    override fun storeCachedMessageTimeline(
        connectionId: String,
        messages: List<Message>,
    ) {
        ChatSessionCaches.messageTimelineCache.store(connectionId, messages)
    }

    override fun mergeCachedTimelineMessage(
        connectionId: String,
        message: Message,
    ) {
        ChatSessionCaches.mergeTimeline(connectionId, message)
    }

    override suspend fun seedInboxChatRouting(chats: List<ChatWithDetails>) = seedInboxChatRoutingImpl(chats = chats)

    internal data class GlobalPresenceSession(
        val channel: RealtimeChannel,
        val trackedUserId: String,
        val scope: CoroutineScope,
        val jobs: List<Job>,
    )

    @Serializable
    internal data class TypingBroadcastPayload(
        val userId: String,
    )

    internal data class ChatEphemeralSession(
        val channel: RealtimeChannel,
        val peerUserId: String,
        val typingFlow: MutableSharedFlow<TypingStatus>,
        val peerOnline: MutableStateFlow<Boolean>,
        val scope: CoroutineScope,
        val jobs: List<Job>,
    )

    override suspend fun startGlobalPresence(userId: String) = startGlobalPresenceImpl(userId = userId)

    override suspend fun stopGlobalPresence() {
        presenceReconnectJob?.cancel()
        presenceReconnectJob = null
        globalPresenceMutex.withLock {
            val session = globalPresenceSession ?: return@withLock
            globalPresenceSession = null
            disposeGlobalPresenceSession(session)
            _onlineUsers.value = emptySet()
            _presenceHealth.value = PresenceHealth.Idle
        }
    }

    @Serializable
    internal data class ConnectionUserIdsRow(
        val id: String,
        val user_ids: List<String>,
    )

    @Serializable
    internal data class GroupRow(
        val id: String,
        val name: String,
        @SerialName("created_by") val createdBy: String,
        @SerialName("key_anchor_user_id") val keyAnchorUserId: String? = null,
        @SerialName("avatar_url") val avatarUrl: String? = null,
    )

    @Serializable
    internal data class GroupMemberKeyRow(
        @SerialName("encrypted_group_key") val encryptedGroupKey: String,
    )

    @Serializable
    internal data class GroupMemberUidRow(
        @SerialName("user_id") val userId: String,
    )

    @Serializable
    internal data class GroupMemberGroupIdRow(
        @SerialName("group_id") val groupId: String,
    )

    @Serializable
    internal data class GroupMemberFullRow(
        @SerialName("group_id") val groupId: String,
        @SerialName("user_id") val userId: String,
    )

    internal data class GroupChatsParallelResult(
        val latestByChatId: Map<String, MessageRow>,
        val unreadByChatId: Map<String, Int>,
        val allGroups: List<GroupRow>,
        val allMemberRows: List<GroupMemberFullRow>,
        val allUsers: List<User>,
    )

    override suspend fun cacheEncryptionKeys(
        chatId: String,
        connectionId: String,
        userIds: List<String>,
    ) {
        val keys = MessageCrypto.deriveKeysForConnection(connectionId, userIds)
        ChatSessionCaches.putPairwiseCrypto(chatId, keys)
    }

    override suspend fun cacheGroupMasterKey(
        chatId: String,
        masterKey: ByteArray,
    ) {
        ChatSessionCaches.putGroupCrypto(chatId, masterKey)
    }

    override suspend fun clearSessionCaches() {
        // Stop long-lived realtime sessions first so callbacks can't re-populate
        // the cache mid-clear.
        runCatching { stopGlobalPresence() }
        ephemeralMutex.withLock {
            val sessions = ephemeralSessions.values.toList()
            ephemeralSessions.clear()
            for (chatId in ephemeralJoinGeneration.keys.toList()) {
                ephemeralJoinGeneration[chatId] = (ephemeralJoinGeneration[chatId] ?: 0) + 1
            }
            for (session in sessions) {
                disposeEphemeralSession(session)
            }
        }
        ChatSessionCaches.clearAll()
    }

    @Serializable
    internal data class ChatRoutingRow(
        @SerialName("connection_id") val connectionId: String? = null,
        @SerialName("group_id") val groupId: String? = null,
    )

    @Serializable
    internal data class ChatRow(
        val id: String,
        @SerialName("connection_id")
        val connectionId: String? = null,
        @SerialName("group_id")
        val groupId: String? = null,
        @SerialName("created_at")
        val createdAt: Long,
        @SerialName("updated_at")
        val updatedAt: Long,
    )

    @Serializable
    internal data class ChatInsert(
        @SerialName("connection_id")
        val connectionId: String,
    )

    @Serializable
    internal data class GroupChatInsert(
        @SerialName("group_id")
        val groupId: String,
    )

    @Serializable
    internal data class MessageRow(
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
        val isRead: Boolean = false,
        @SerialName("message_type")
        val messageType: String = "text",
        val metadata: JsonElement? = null,
        @SerialName("local_sent_at")
        val localSentAt: Long? = null,
        @SerialName("read_at")
        val readAt: Long? = null,
        @SerialName("delivered_at")
        val deliveredAt: Long? = null,
    ) {
        fun toMessage(): Message =
            Message(
                id = id,
                user_id = userId,
                content = content,
                timeCreated = timeCreated,
                timeEdited = timeEdited,
                isRead = isRead,
                messageType = messageType,
                metadata = metadata,
                localSentAt = localSentAt,
                readAt = readAt,
                deliveredAt = deliveredAt,
            ).withDbDerivedDeliveryState()
    }

    /**
     * Lightweight chat row for verified cliques — mirrors click-web DashboardView
     * (`id, group_id, updated_at` only) so listing never depends on full ChatRow / crypto.
     */
    @Serializable
    internal data class GroupChatListRow(
        val id: String,
        @SerialName("group_id") val groupId: String,
        @SerialName("updated_at") val updatedAt: Long? = null,
    )

    override suspend fun fetchGroupUserChatsWithDetails(userId: String): List<ChatWithDetails> =
        fetchGroupUserChatsWithDetailsImpl(userId = userId)

    override suspend fun decryptGroupChatPreview(
        chatId: String,
        viewerUserId: String,
    ): Message? = decryptGroupChatPreviewImpl(chatId = chatId, viewerUserId = viewerUserId)

    /**
     * Short-lived cache so that back-to-back calls to [fetchUserChatsWithDetails] and
     * [fetchArchivedUserChatsWithDetails] (e.g. from [ChatViewModel.loadChats]) share a
     * single set of connection + junction queries instead of doubling network round-trips.
     */
    internal var cachedJunctionData: Triple<List<Connection>, Set<String>, Set<String>>? = null
    internal var cachedJunctionUserId: String? = null
    internal var cachedJunctionTimestamp: Long = 0L
    internal val junctionCacheTtlMs = 300_000L // 5 minutes

    override suspend fun fetchUserChatsWithDetails(userId: String): List<ChatWithDetails> = fetchUserChatsWithDetailsImpl(userId = userId)

    override suspend fun fetchDirectUserChatsWithDetails(userId: String): List<ChatWithDetails> =
        fetchDirectUserChatsWithDetailsImpl(userId = userId)

    override suspend fun fetchArchivedUserChatsWithDetails(userId: String): List<ChatWithDetails> =
        fetchArchivedUserChatsWithDetailsImpl(userId = userId)

    override suspend fun fetchMessagesForChat(
        chatId: String,
        viewerUserId: String?,
        limit: Int?,
        beforeTimeCreated: Long?,
    ): List<Message>? =
        fetchMessagesForChatImpl(chatId = chatId, viewerUserId = viewerUserId, limit = limit, beforeTimeCreated = beforeTimeCreated)

    override suspend fun ensureFreshAuthToken(): String? = ensureFreshJwtForChat()

    override suspend fun sendMessage(
        chatId: String,
        userId: String,
        content: String,
        messageType: String,
        metadata: JsonElement?,
        clientLocalSentAtMs: Long?,
        connectionId: String?,
    ): Message? =
        sendMessageImpl(
            chatId = chatId,
            userId = userId,
            content = content,
            messageType = messageType,
            metadata = metadata,
            clientLocalSentAtMs = clientLocalSentAtMs,
            connectionId = connectionId,
        )

    override suspend fun ensureChatForConnection(connectionId: String): Chat? = ensureChatForConnectionImpl(connectionId = connectionId)

    override suspend fun ensureChatForGroup(groupId: String): Chat? = ensureChatForGroupImpl(groupId = groupId)

    override suspend fun sendMessageForConnection(
        connectionId: String,
        userId: String,
        content: String,
        messageType: String,
        metadata: JsonElement?,
    ): Message? {
        val chat = ensureChatForConnection(connectionId) ?: return null
        return sendMessage(chat.id ?: return null, userId, content, messageType, metadata)
    }

    override suspend fun markMessagesAsRead(
        chatId: String,
        userId: String,
    ) = markMessagesAsReadImpl(chatId = chatId, userId = userId)

    override suspend fun markChatAsUnread(chatId: String) = markChatAsUnreadImpl(chatId = chatId)

    override suspend fun markMessagesDelivered(
        chatId: String,
        messageIds: List<String>,
    ) = markMessagesDeliveredImpl(chatId = chatId, messageIds = messageIds)

    override suspend fun subscribeToMessages(
        chatId: String,
        viewerUserId: String,
    ): Pair<ChatMessageSubscription, Flow<ChatRealtimeEvent>> = subscribeToMessagesImpl(chatId = chatId, viewerUserId = viewerUserId)

    override suspend fun subscribeToMessageInserts(): Pair<ChatMessageSubscription, Flow<MessageListInsertEvent>> =
        subscribeToMessageInsertsImpl()

    // Fetch a specific chat by ID via API
    override suspend fun fetchChatById(chatId: String): Chat? {
        return try {
            val row =
                supabase
                    .from("chats")
                    .select {
                        filter {
                            eq("id", chatId)
                        }
                        limit(1)
                    }.decodeList<ChatRow>()
                    .firstOrNull() ?: return null

            Chat(id = row.id, connectionId = row.connectionId, groupId = row.groupId, messages = emptyList())
        } catch (e: Exception) {
            println("Error fetching chat: ${e.redactedRestMessage()}")
            null
        }
    }

    override suspend fun fetchChatWithDetails(
        chatId: String,
        currentUserId: String,
    ): ChatWithDetails? = fetchChatWithDetailsImpl(chatId = chatId, currentUserId = currentUserId)

    override suspend fun fetchChatParticipants(chatId: String): List<User> = fetchChatParticipantsImpl(chatId = chatId)

    // Get user by ID - helper method for getting user details
    override suspend fun getUserById(userId: String): User? =
        try {
            fetchUsersByIdsSafe(listOf(userId)).firstOrNull()
        } catch (e: Exception) {
            println("Error fetching user: ${e.redactedRestMessage()}")
            null
        }

    override suspend fun updateMessage(
        chatId: String,
        messageId: String,
        userId: String,
        content: String,
    ): Message? = updateMessageImpl(chatId = chatId, messageId = messageId, userId = userId, content = content)

    override suspend fun deleteMessage(
        chatId: String,
        messageId: String,
        userId: String,
    ): Boolean = deleteMessageImpl(chatId = chatId, messageId = messageId, userId = userId)

    // ── Reaction CRUD via direct Supabase (bypasses Python API) ──────────────

    @Serializable
    internal data class ReactionRow(
        val id: String = "",
        @SerialName("message_id")
        val messageId: String,
        @SerialName("user_id")
        val userId: String,
        @SerialName("reaction_type")
        val reactionType: String,
        @SerialName("created_at")
        val createdAt: Long,
    ) {
        fun toMessageReaction(): MessageReaction =
            MessageReaction(
                id = id,
                messageId = messageId,
                userId = userId,
                reactionType = reactionType,
                createdAt = createdAt,
            )
    }

    override suspend fun fetchReactionsForChat(
        chatId: String,
        messageIds: List<String>?,
    ): List<MessageReaction> = fetchReactionsForChatImpl(chatId = chatId, messageIds = messageIds)

    @Serializable
    internal data class MessageIdOnly(
        val id: String,
    )

    @Serializable
    internal data class ChatIdOnly(
        val id: String,
    )

    /** Add a reaction via Next.js gatekeeper. */
    override suspend fun addReaction(
        messageId: String,
        userId: String,
        reactionType: String,
    ): Boolean {
        return try {
            val jwt = ensureFreshJwtForChat() ?: return false
            apiClient
                .sendReaction(messageId, userId, reactionType, jwt)
                .recoverCatching {
                    val retried = refreshedJwtAfterAuthFailure() ?: throw it
                    apiClient.sendReaction(messageId, userId, reactionType, retried).getOrThrow()
                }.isSuccess
        } catch (e: Exception) {
            println("Error adding reaction: ${e.redactedRestMessage()}")
            false
        }
    }

    /** Remove a reaction via Next.js gatekeeper. */
    override suspend fun removeReaction(
        messageId: String,
        userId: String,
        reactionType: String,
    ): Boolean {
        return try {
            val jwt = ensureFreshJwtForChat() ?: return false
            apiClient
                .removeReaction(messageId, userId, reactionType, jwt)
                .recoverCatching {
                    val retried = refreshedJwtAfterAuthFailure() ?: throw it
                    apiClient.removeReaction(messageId, userId, reactionType, retried).getOrThrow()
                }.getOrElse { false }
        } catch (e: Exception) {
            println("Error removing reaction: ${e.redactedRestMessage()}")
            false
        }
    }

    override suspend fun sendTypingStatus(
        chatId: String,
        userId: String,
        isTyping: Boolean,
    ) {
        if (!isTyping) return
        val session = ephemeralMutex.withLock { ephemeralSessions[chatId] } ?: return
        try {
            session.channel.broadcast(
                event = "typing",
                message = buildJsonObject { put("userId", userId) },
            )
        } catch (e: Exception) {
            println("ChatRepository: typing broadcast failed: ${e.redactedRestMessage()}")
        }
    }

    override fun observeTypingStatus(chatId: String): Flow<TypingStatus> =
        flow {
            val session =
                awaitEphemeralSession(chatId) ?: run {
                    awaitCancellation()
                    return@flow
                }
            emitAll(session.typingFlow)
        }

    override suspend fun getTypingUsers(chatId: String): List<String> = emptyList()

    override suspend fun joinChatEphemeralChannel(
        chatId: String,
        currentUserId: String,
        peerUserId: String,
    ) = joinChatEphemeralChannelImpl(chatId = chatId, currentUserId = currentUserId, peerUserId = peerUserId)

    override suspend fun leaveChatEphemeralChannel(chatId: String) {
        ephemeralMutex.withLock {
            ephemeralJoinGeneration[chatId] = (ephemeralJoinGeneration[chatId] ?: 0) + 1
            val session = ephemeralSessions.remove(chatId) ?: return
            disposeEphemeralSession(session)
        }
    }

    override fun observePeerOnline(
        chatId: String,
        peerUserId: String,
    ): Flow<Boolean> =
        flow {
            val session = awaitEphemeralSession(chatId)
            if (session == null || session.peerUserId != peerUserId) {
                emit(false)
                return@flow
            }
            emitAll(session.peerOnline)
        }

    override suspend fun updateMessageStatus(
        messageId: String,
        status: String,
    ): Boolean {
        return try {
            val authToken = ensureFreshJwtForChat() ?: return false
            apiClient
                .updateMessageStatus(messageId, status, authToken)
                .recoverCatching {
                    val retried = refreshedJwtAfterAuthFailure() ?: throw it
                    apiClient.updateMessageStatus(messageId, status, retried).getOrThrow()
                }.getOrElse { false }
        } catch (e: Exception) {
            println("Error updating status: ${e.redactedRestMessage()}")
            false
        }
    }

    override suspend fun forwardMessage(
        messageId: String,
        targetChatId: String,
        userId: String,
    ): Message? = forwardMessageImpl(messageId = messageId, targetChatId = targetChatId, userId = userId)

    override suspend fun searchMessages(
        chatId: String,
        query: String,
    ): List<Message> = searchMessagesImpl(chatId = chatId, query = query)

    override suspend fun resolveChatIdForConnection(connectionId: String): String? =
        try {
            val rows =
                supabase
                    .from("chats")
                    .select(
                        columns =
                            io.github.jan.supabase.postgrest.query.Columns
                                .list("id"),
                    ) {
                        filter {
                            eq("connection_id", connectionId)
                        }
                        limit(1)
                    }.decodeList<ChatIdOnly>()
            rows.firstOrNull()?.id
        } catch (e: Exception) {
            println("Error resolving chat id for connection $connectionId: ${e.redactedRestMessage()}")
            null
        }

    override suspend fun resolveChatIdForGroupId(groupId: String): String? =
        try {
            supabase
                .from("chats")
                .select(columns = Columns.list("id")) {
                    filter { eq("group_id", groupId) }
                    limit(1)
                }.decodeList<ChatIdOnly>()
                .firstOrNull()
                ?.id
        } catch (e: Exception) {
            println("Error resolving chat id for group $groupId: ${e.redactedRestMessage()}")
            null
        }

    override suspend fun searchMessagesByConnectionId(
        connectionId: String,
        query: String,
    ): Pair<String?, List<Message>> {
        val resolvedChatId = resolveChatIdForConnection(connectionId)
        val messages =
            when {
                !resolvedChatId.isNullOrBlank() -> searchMessages(resolvedChatId, query)
                else -> emptyList()
            }
        return resolvedChatId to messages
    }

    override suspend fun unifiedSearchSupplement(
        viewerUserId: String,
        peerUserIds: List<String>,
    ): UnifiedSearchSupplement = unifiedSearchSupplementImpl(viewerUserId = viewerUserId, peerUserIds = peerUserIds)

    @Serializable
    internal data class UserInterestRowDb(
        @SerialName("user_id") val userId: String,
        val tags: List<String> = emptyList(),
    )

    override suspend fun createVerifiedClique(
        memberUserIds: List<String>,
        encryptedKeysByUserId: Map<String, String>,
        initialGroupName: String,
    ): Result<String> =
        runCatching {
            val ids = memberUserIds.distinct().sorted()
            require(ids.size >= 2) { "Clique needs at least two members" }
            val body =
                buildJsonObject {
                    put("target_user_ids", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
                    put(
                        "encrypted_keys",
                        buildJsonObject {
                            encryptedKeysByUserId.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                        },
                    )
                    put("initial_group_name", JsonPrimitive(initialGroupName.trim().ifBlank { "Clique" }))
                }
            val rpcResult = supabase.postgrest.rpc("create_verified_clique", body)
            decodeUuidScalarFromRpc(rpcResult.data)
        }

    override suspend fun leaveClique(groupId: String): Result<Unit> =
        runCatching {
            require(groupId.isNotBlank())
            val body = buildJsonObject { put("target_group_id", JsonPrimitive(groupId)) }
            supabase.postgrest.rpc("leave_clique", body)
        }

    override suspend fun deleteClique(groupId: String): Result<Unit> =
        runCatching {
            require(groupId.isNotBlank())
            val body = buildJsonObject { put("target_group_id", JsonPrimitive(groupId)) }
            supabase.postgrest.rpc("delete_clique", body)
        }

    override suspend fun renameClique(
        groupId: String,
        newName: String,
    ): Result<Unit> =
        runCatching {
            require(groupId.isNotBlank())
            val body =
                buildJsonObject {
                    put("target_group_id", JsonPrimitive(groupId))
                    put("new_name", JsonPrimitive(newName))
                }
            supabase.postgrest.rpc("rename_clique", body)
        }

    override suspend fun addCliqueMember(
        groupId: String,
        newMemberUserId: String,
    ): Result<Unit> =
        runCatching {
            require(groupId.isNotBlank())
            require(newMemberUserId.isNotBlank())
            val authToken =
                ensureFreshJwtForChat()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalStateException("Not authenticated")
            apiClient
                .addCliqueMember(
                    groupId = groupId,
                    newMemberUserId = newMemberUserId,
                    authToken = authToken,
                ).getOrThrow()
        }

    override suspend fun peekGroupMasterKey(
        chatId: String,
        viewerUserId: String,
    ): ByteArray? {
        val cached = ChatSessionCaches.getCrypto(chatId)
        if (cached is ChatSessionCaches.ResolvedChatCrypto.GroupMaster) {
            return cached.masterKey.copyOf()
        }
        return try {
            val row =
                supabase
                    .from("chats")
                    .select(columns = Columns.list("group_id")) {
                        filter { eq("id", chatId) }
                        limit(1)
                    }.decodeList<ChatRoutingRow>()
                    .firstOrNull()
            val groupId = row?.groupId ?: return null
            unwrapGroupMasterKeyFromDb(groupId, viewerUserId)
        } catch (e: Exception) {
            println("ChatRepository: peekGroupMasterKey failed: ${e.redactedRestMessage()}")
            null
        }
    }

    override suspend fun removeCliqueMember(
        groupId: String,
        memberUserId: String,
    ): Result<Unit> =
        runCatching {
            require(groupId.isNotBlank())
            require(memberUserId.isNotBlank())
            val authToken =
                ensureFreshJwtForChat()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalStateException("Not authenticated")
            apiClient
                .removeCliqueMember(
                    groupId = groupId,
                    memberUserId = memberUserId,
                    authToken = authToken,
                ).getOrThrow()
        }

    override fun clearChatListLocalCaches() {
        cachedJunctionData = null
        cachedJunctionUserId = null
        cachedJunctionTimestamp = 0L
    }

    override fun seedConnectionJunctionCache(
        userId: String,
        connections: List<Connection>,
        archivedConnectionIds: Set<String>,
        hiddenConnectionIds: Set<String>,
    ) {
        if (userId.isBlank()) return
        cachedJunctionData = Triple(connections, archivedConnectionIds, hiddenConnectionIds)
        cachedJunctionUserId = userId
        cachedJunctionTimestamp = Clock.System.now().toEpochMilliseconds()
    }

    override suspend fun verifiedCliqueEdgesExist(memberUserIds: List<String>): Boolean =
        runCatching {
            val ids = memberUserIds.distinct().sorted()
            if (ids.size < 2) return@runCatching false
            val body =
                buildJsonObject {
                    put("p_member_ids", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
                }
            val rpcResult = supabase.postgrest.rpc("verified_clique_edges_exist", body)
            parseRpcBoolean(rpcResult.data)
        }.getOrElse { e ->
            println("ChatRepository: verifiedCliqueEdgesExist failed: ${e.redactedRestMessage()}")
            false
        }

    override suspend fun uploadChatMedia(
        bytes: ByteArray,
        objectPath: String,
        contentType: String,
    ): String? = uploadChatMediaImpl(bytes = bytes, objectPath = objectPath, contentType = contentType)

    override suspend fun uploadEncryptedBlob(
        bucketName: String,
        chatId: String,
        senderUserId: String,
        plainBytes: ByteArray,
        mimeType: String,
        fileName: String,
    ): ChatRepository.EncryptedAttachmentUpload? {
        if (plainBytes.isEmpty()) return null
        val trimmedChatId = chatId.trim()
        val trimmedSender = senderUserId.trim()
        val trimmedName = fileName.trim()
        if (trimmedChatId.isEmpty() || trimmedSender.isEmpty()) return null

        return when (bucketName) {
            CHAT_MEDIA_BUCKET ->
                uploadEncryptedMediaBlob(
                    chatId = trimmedChatId,
                    senderUserId = trimmedSender,
                    plainBytes = plainBytes,
                    mimeType = mimeType,
                    fileName = trimmedName.ifEmpty { "media" },
                )
            CHAT_ATTACHMENTS_BUCKET ->
                uploadEncryptedAttachmentBlob(
                    chatId = trimmedChatId,
                    senderUserId = trimmedSender,
                    plainBytes = plainBytes,
                    mimeType = mimeType,
                    fileName = trimmedName,
                )
            else -> {
                println("ChatRepository: uploadEncryptedBlob refused unknown bucket=$bucketName")
                null
            }
        }
    }

    override suspend fun downloadAttachmentPlaintext(
        path: String,
        fileMasterKeyBase64: String,
        expectedSha256Base64: String,
    ): ByteArray? {
        if (path.isBlank() || fileMasterKeyBase64.isBlank()) return null
        return try {
            val jwt = ensureFreshJwtForChat() ?: return null
            val signedUrl =
                apiClient
                    .signAttachmentUrl(path, jwt)
                    .recoverCatching { firstErr ->
                        val retriedJwt = refreshedJwtAfterAuthFailure() ?: throw firstErr
                        apiClient.signAttachmentUrl(path, retriedJwt).getOrThrow()
                    }.getOrElse { err ->
                        println("ChatRepository: signAttachmentUrl failed: ${err.redactedRestMessage()}")
                        return null
                    }
            val cipher =
                apiClient.downloadAttachmentBytes(signedUrl).getOrElse { err ->
                    println("ChatRepository: attachment download failed: ${err.redactedRestMessage()}")
                    return null
                }
            val key = AttachmentCrypto.decodeFileMasterKeyBase64(fileMasterKeyBase64)
            val plain = AttachmentCrypto.decryptFileBytes(cipher, key)
            val actualSha = AttachmentCrypto.sha256Base64(plain)
            if (!actualSha.equals(expectedSha256Base64, ignoreCase = false)) {
                println("ChatRepository: attachment SHA-256 mismatch — object may have been swapped")
                return null
            }
            plain
        } catch (e: Exception) {
            println("ChatRepository: downloadAttachmentPlaintext failed: ${e.redactedRestMessage()}")
            null
        }
    }

    override suspend fun vaultEncryptedMediaMessages(
        chatId: String,
        viewerUserId: String,
        messages: List<Message>,
    ): List<Message> {
        if (chatId.isBlank() || viewerUserId.isBlank() || messages.isEmpty()) return messages
        return withContext(chatMediaDispatcher) {
            val concurrency = Semaphore(4)
            coroutineScope {
                messages
                    .map { message ->
                        async {
                            concurrency.withPermit {
                                vaultEncryptedMediaMessage(chatId, viewerUserId, message)
                            }
                        }
                    }.awaitAll()
            }
        }
    }

    override suspend fun downloadAndDecryptChatMedia(
        chatId: String,
        viewerUserId: String,
        mediaUrl: String,
    ): ByteArray? = downloadAndDecryptChatMediaImpl(chatId = chatId, viewerUserId = viewerUserId, mediaUrl = mediaUrl)
}

internal class SupabaseMessageSubscription(
    private val channel: RealtimeChannel,
) : ChatMessageSubscription {
    override suspend fun attach() {
        if (!channel.subscribeWithTimeout()) {
            throw IllegalStateException("Realtime channel subscribe timed out")
        }
    }

    override suspend fun detach() = channel.unsubscribe()
}

internal suspend fun RealtimeChannel.subscribeWithTimeout(timeoutMs: Long = REALTIME_SUBSCRIBE_TIMEOUT_MS): Boolean =
    withTimeoutOrNull(timeoutMs) {
        subscribe(blockUntilSubscribed = true)
        true
    } ?: run {
        println("ChatRepository: channel subscribe timed out after ${timeoutMs}ms")
        false
    }

internal const val REALTIME_SUBSCRIBE_TIMEOUT_MS = 8_000L

internal const val EPHEMERAL_SESSION_POLL_MS = 50L
internal const val EPHEMERAL_SESSION_WAIT_STEPS = 100
internal const val PRESENCE_TRACK_REFRESH_MS = 25_000L
internal const val GLOBAL_PRESENCE_CHANNEL = "room:presence"
