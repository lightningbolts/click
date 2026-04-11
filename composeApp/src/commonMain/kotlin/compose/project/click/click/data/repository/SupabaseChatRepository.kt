package compose.project.click.click.data.repository

import compose.project.click.click.crypto.MessageCrypto
import compose.project.click.click.data.CHAT_MEDIA_BUCKET
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.api.ChatApiClient
import compose.project.click.click.data.models.*
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.notifications.ChatPushNotifier
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Presence
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.track
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret

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
    private val connectionsSelectWithEncounters = Columns.raw("*, connection_encounters(*)")

    private fun Connection.withEncountersSortedNewestFirst(): Connection =
        copy(connectionEncounters = connectionEncounters.sortedByDescending { it.encounteredAt })

    private sealed class ResolvedChatCrypto {
        data class Pairwise(val keys: MessageCrypto.DerivedKeys) : ResolvedChatCrypto()
        data class GroupMaster(val masterKey: ByteArray) : ResolvedChatCrypto()
    }

    private val chatCryptoCache = mutableMapOf<String, ResolvedChatCrypto>()

    private val chatConnectionRouteMutex = Mutex()
    private val chatIdToConnectionId = mutableMapOf<String, String>()
    private val chatIdToGroupId = mutableMapOf<String, String>()

    private val ephemeralMutex = Mutex()
    private val ephemeralSessions = mutableMapOf<String, ChatEphemeralSession>()

    private val globalPresenceMutex = Mutex()
    private var globalPresenceSession: GlobalPresenceSession? = null
    private val _onlineUsers = MutableStateFlow<Set<String>>(emptySet())
    override val onlineUsers: StateFlow<Set<String>> = _onlineUsers.asStateFlow()

    private data class GlobalPresenceSession(
        val channel: RealtimeChannel,
        val trackedUserId: String,
        val scope: CoroutineScope,
        val jobs: List<Job>,
    )

    private suspend fun disposeGlobalPresenceSession(session: GlobalPresenceSession) {
        session.jobs.forEach { it.cancel() }
        session.scope.cancel()
        try {
            session.channel.untrack()
        } catch (_: Exception) {
        }
        try {
            session.channel.unsubscribe()
        } catch (_: Exception) {
        }
    }

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

    private fun userIdFromPresence(p: Presence): String? {
        fun fromObject(obj: JsonObject): String? {
            val el = obj["userId"] ?: obj["user_id"] ?: return null
            return (el as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }
        fromObject(p.state)?.let { return it }
        val nested = p.state["state"]?.let { it as? JsonObject } ?: return null
        return fromObject(nested)
    }

    private suspend fun disposeEphemeralSession(session: ChatEphemeralSession) {
        session.jobs.forEach { it.cancel() }
        session.scope.cancel()
        runCatching { session.channel.untrack() }
        runCatching { session.channel.unsubscribe() }
    }

    override suspend fun startGlobalPresence(userId: String) {
        if (userId.isBlank()) return
        globalPresenceMutex.withLock {
            globalPresenceSession?.let { existing ->
                if (existing.trackedUserId == userId) return@withLock
                disposeGlobalPresenceSession(existing)
                globalPresenceSession = null
            }

            val channel = supabase.channel(GLOBAL_PRESENCE_CHANNEL) {
                presence {
                    key = userId
                }
            }

            val presenceKeysOnline = mutableSetOf<String>()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val presenceFlow = channel.presenceChangeFlow()

            /** Register before subscribe so the initial presence sync is not dropped (matches web `sync` handler). */
            val presenceJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    presenceFlow.collect { action ->
                        action.leaves.keys.forEach { key -> presenceKeysOnline.remove(key) }
                        action.joins.keys.forEach { key -> presenceKeysOnline.add(key) }
                        action.joins.values.forEach { p ->
                            userIdFromPresence(p)?.let { presenceKeysOnline.add(it) }
                        }
                        action.leaves.values.forEach { p ->
                            userIdFromPresence(p)?.let { presenceKeysOnline.remove(it) }
                        }
                        _onlineUsers.value = presenceKeysOnline.toSet()
                    }
                } catch (_: Exception) {
                }
            }

            try {
                channel.subscribe(blockUntilSubscribed = true)
                channel.track(buildJsonObject { put("userId", userId) })
            } catch (e: Exception) {
                println("ChatRepository: startGlobalPresence failed: ${e.redactedRestMessage()}")
                presenceJob.cancel()
                scope.cancel()
                return@withLock
            }

            val presenceRefreshJob = scope.launch {
                while (isActive) {
                    delay(PRESENCE_TRACK_REFRESH_MS)
                    runCatching {
                        channel.track(buildJsonObject { put("userId", userId) })
                    }
                }
            }

            globalPresenceSession = GlobalPresenceSession(
                channel = channel,
                trackedUserId = userId,
                scope = scope,
                jobs = listOf(presenceJob, presenceRefreshJob),
            )
        }
    }

    override suspend fun stopGlobalPresence() {
        globalPresenceMutex.withLock {
            val session = globalPresenceSession ?: return@withLock
            globalPresenceSession = null
            disposeGlobalPresenceSession(session)
            _onlineUsers.value = emptySet()
        }
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

    @Serializable
    private data class GroupRow(
        val id: String,
        val name: String,
        @SerialName("created_by") val createdBy: String,
        @SerialName("key_anchor_user_id") val keyAnchorUserId: String? = null,
    )

    @Serializable
    private data class GroupMemberKeyRow(
        @SerialName("encrypted_group_key") val encryptedGroupKey: String,
    )

    @Serializable
    private data class GroupMemberUidRow(
        @SerialName("user_id") val userId: String,
    )

    @Serializable
    private data class GroupMemberGroupIdRow(
        @SerialName("group_id") val groupId: String,
    )

    private suspend fun findConnectionIdBetween(userA: String, userB: String): String? {
        if (userA.isBlank() || userB.isBlank()) return null
        return try {
            supabase.from("connections")
                .select(columns = Columns.list("id", "user_ids")) {
                    filter {
                        contains("user_ids", listOf(userA, userB))
                        isIn("status", listOf("active", "kept"))
                    }
                    limit(8)
                }
                .decodeList<ConnectionUserIdsRow>()
                .firstOrNull {
                    it.user_ids.size == 2 &&
                        it.user_ids.contains(userA) &&
                        it.user_ids.contains(userB)
                }
                ?.id
        } catch (e: Exception) {
            println("ChatRepository: findConnectionIdBetween failed: ${e.redactedRestMessage()}")
            null
        }
    }

    private suspend fun unwrapGroupMasterKeyFromDb(
        groupId: String,
        viewerUserId: String,
    ): ByteArray? {
        return try {
            val group = supabase.from("groups")
                .select(columns = Columns.list("id", "name", "created_by", "key_anchor_user_id")) {
                    filter { eq("id", groupId) }
                    limit(1)
                }
                .decodeList<GroupRow>()
                .firstOrNull() ?: return null

            val memberRow = supabase.from("group_members")
                .select(columns = Columns.list("encrypted_group_key")) {
                    filter {
                        eq("group_id", groupId)
                        eq("user_id", viewerUserId)
                    }
                    limit(1)
                }
                .decodeList<GroupMemberKeyRow>()
                .firstOrNull() ?: return null

            val wrapPeer = when {
                viewerUserId == group.createdBy -> group.keyAnchorUserId
                else -> group.createdBy
            } ?: return null

            val connId = findConnectionIdBetween(viewerUserId, wrapPeer) ?: return null
            val keys = MessageCrypto.deriveKeysForConnection(
                connId,
                listOf(viewerUserId, wrapPeer).sorted(),
            )
            val plain = MessageCrypto.decryptContent(memberRow.encryptedGroupKey, keys)
            MessageCrypto.decodeGroupMasterKeyBase64(plain)
        } catch (e: Exception) {
            println("ChatRepository: unwrap group key failed: ${e.redactedRestMessage()}")
            null
        }
    }

    private suspend fun resolveChatCrypto(chatId: String, viewerUserId: String?): ResolvedChatCrypto? {
        chatCryptoCache[chatId]?.let { return it }
        return try {
            val row = supabase.from("chats")
                .select(columns = Columns.list("id", "connection_id", "group_id")) {
                    filter { eq("id", chatId) }
                    limit(1)
                }
                .decodeList<ChatRoutingRow>()
                .firstOrNull() ?: return null

            when {
                row.groupId != null -> {
                    rememberChatGroupRouting(chatId, row.groupId)
                    val uid = viewerUserId ?: return null
                    val master = unwrapGroupMasterKeyFromDb(row.groupId, uid) ?: return null
                    ResolvedChatCrypto.GroupMaster(master).also { chatCryptoCache[chatId] = it }
                }
                !row.connectionId.isNullOrBlank() -> {
                    rememberChatConnectionRouting(chatId, row.connectionId)
                    val connection = supabase.from("connections")
                        .select(columns = Columns.list("id", "user_ids")) {
                            filter { eq("id", row.connectionId) }
                            limit(1)
                        }
                        .decodeList<ConnectionUserIdsRow>()
                        .firstOrNull() ?: return null
                    val keys = MessageCrypto.deriveKeysForConnection(connection.id, connection.user_ids)
                    ResolvedChatCrypto.Pairwise(keys).also { chatCryptoCache[chatId] = it }
                }
                else -> null
            }
        } catch (e: Exception) {
            println("ChatRepository: resolveChatCrypto failed: ${e.redactedRestMessage()}")
            null
        }
    }

    private suspend fun getEncryptionKeysForConnection(connectionId: String): MessageCrypto.DerivedKeys? {
        if (connectionId.isBlank()) return null
        return try {
            val connection = supabase.from("connections")
                .select(columns = Columns.list("id", "user_ids")) {
                    filter { eq("id", connectionId) }
                    limit(1)
                }
                .decodeList<ConnectionUserIdsRow>()
                .firstOrNull() ?: return null
            MessageCrypto.deriveKeysForConnection(connection.id, connection.user_ids)
        } catch (e: Exception) {
            println("ChatRepository: Failed to derive connection keys: ${e.redactedRestMessage()}")
            null
        }
    }

    override fun cacheEncryptionKeys(chatId: String, connectionId: String, userIds: List<String>) {
        val keys = MessageCrypto.deriveKeysForConnection(connectionId, userIds)
        chatCryptoCache[chatId] = ResolvedChatCrypto.Pairwise(keys)
    }

    override fun cacheGroupMasterKey(chatId: String, masterKey: ByteArray) {
        chatCryptoCache[chatId] = ResolvedChatCrypto.GroupMaster(masterKey.copyOf())
    }

    private fun decryptMessage(message: Message, crypto: ResolvedChatCrypto?): Message {
        if (message.messageType == "call_log") return message
        if (crypto == null) {
            if (MessageCrypto.isAnyE2eeWireContent(message.content)) {
                return message.copy(content = "New message")
            }
            return message
        }
        return when (crypto) {
            is ResolvedChatCrypto.GroupMaster -> {
                if (!MessageCrypto.isGroupMessageEncrypted(message.content)) {
                    if (MessageCrypto.isEncrypted(message.content)) message.copy(content = "New message")
                    else message
                } else {
                    message.copy(
                        content = MessageCrypto.decryptGroupMessageContent(message.content, crypto.masterKey),
                    )
                }
            }
            is ResolvedChatCrypto.Pairwise -> {
                if (!MessageCrypto.isEncrypted(message.content)) message
                else message.copy(content = MessageCrypto.decryptContent(message.content, crypto.keys))
            }
        }
    }

    private suspend fun rememberChatConnectionRouting(chatId: String, connectionId: String) {
        if (chatId.isBlank() || connectionId.isBlank()) return
        chatConnectionRouteMutex.withLock {
            chatIdToConnectionId[chatId] = connectionId
            chatIdToGroupId.remove(chatId)
        }
    }

    private suspend fun rememberChatGroupRouting(chatId: String, groupId: String) {
        if (chatId.isBlank() || groupId.isBlank()) return
        chatConnectionRouteMutex.withLock {
            chatIdToGroupId[chatId] = groupId
            chatIdToConnectionId.remove(chatId)
        }
    }

    /** Returns a connection id **or** a group id for Clicks list routing ([bumpConnectionInChatList]). */
    private suspend fun resolveListKeyForChat(chatId: String): String? {
        chatConnectionRouteMutex.withLock {
            chatIdToConnectionId[chatId]?.let { return it }
            chatIdToGroupId[chatId]?.let { return it }
        }
        val row = supabase.from("chats")
            .select(columns = Columns.list("connection_id", "group_id")) {
                filter { eq("id", chatId) }
                limit(1)
            }
            .decodeList<ChatRoutingRow>()
            .firstOrNull() ?: return null
        when {
            row.groupId != null -> rememberChatGroupRouting(chatId, row.groupId)
            !row.connectionId.isNullOrBlank() -> rememberChatConnectionRouting(chatId, row.connectionId)
        }
        return row.connectionId ?: row.groupId
    }

    @Serializable
    private data class ChatRoutingRow(
        @SerialName("connection_id") val connectionId: String? = null,
        @SerialName("group_id") val groupId: String? = null,
    )

    private suspend fun fetchUsersByIdsSafe(userIds: List<String>): List<User> {
        if (userIds.isEmpty()) return emptyList()

        return supabaseRepository.fetchUsersByIds(userIds)
    }

    @Serializable
    private data class ChatRow(
        val id: String,
        @SerialName("connection_id")
        val connectionId: String? = null,
        @SerialName("group_id")
        val groupId: String? = null,
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
        val isRead: Boolean = false,
        @SerialName("message_type")
        val messageType: String = "text",
        val metadata: JsonElement? = null,
    ) {
        fun toMessage(): Message = Message(
            id = id,
            user_id = userId,
            content = content,
            timeCreated = timeCreated,
            timeEdited = timeEdited,
            isRead = isRead,
            messageType = messageType,
            metadata = metadata,
        )
    }

    /**
     * Newest message per chat. A single global messages query ordered by time is row-capped by
     * PostgREST; the first row per chat in that window is not always the true latest, while
     * [Connection.last_message_at] (maintained by a DB trigger) still advances — causing sort/preview mismatch.
     */
    private suspend fun fetchLatestMessageRowPerChat(chatIds: List<String>): Map<String, MessageRow> {
        if (chatIds.isEmpty()) return emptyMap()
        val distinctIds = chatIds.distinct()
        val limitParallel = Semaphore(12)
        suspend fun queryLatestRow(chatId: String): MessageRow? {
            return supabase.from("messages")
                .select {
                    filter { eq("chat_id", chatId) }
                    order("time_created", Order.DESCENDING)
                    limit(1)
                }
                .decodeList<MessageRow>()
                .firstOrNull()
        }
        return coroutineScope {
            distinctIds.map { chatId ->
                async {
                    limitParallel.withPermit {
                        val row = try {
                            queryLatestRow(chatId)
                        } catch (_: Exception) {
                            delay(80)
                            try {
                                queryLatestRow(chatId)
                            } catch (_: Exception) {
                                null
                            }
                        }
                        row?.let { chatId to it }
                    }
                }
            }.awaitAll().filterNotNull().associate { it }
        }
    }

    /** Fills gaps when per-chat queries fail partially; still row-capped but better than nothing. */
    private suspend fun fetchLatestMessageRowsBulkFallback(chatIds: List<String>): Map<String, MessageRow> {
        if (chatIds.isEmpty()) return emptyMap()
        return try {
            val messages = supabase.from("messages")
                .select {
                    filter { isIn("chat_id", chatIds) }
                    order("time_created", Order.DESCENDING)
                    limit(25_000)
                }
                .decodeList<MessageRow>()
            buildMap {
                for (row in messages) {
                    if (!containsKey(row.chatId)) put(row.chatId, row)
                }
            }
        } catch (e: Exception) {
            println("ChatRepository: bulk last-message fallback failed: ${e.redactedRestMessage()}")
            emptyMap()
        }
    }

    private suspend fun buildChatsWithDetailsForConnections(
        userId: String,
        connections: List<Connection>,
    ): List<ChatWithDetails> {
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

        val chatByConnectionId = chats
            .filter { !it.connectionId.isNullOrBlank() }
            .associateBy { it.connectionId!! }

        chats.forEach { chatRow ->
            when {
                !chatRow.connectionId.isNullOrBlank() ->
                    rememberChatConnectionRouting(chatRow.id, chatRow.connectionId!!)
                !chatRow.groupId.isNullOrBlank() ->
                    rememberChatGroupRouting(chatRow.id, chatRow.groupId!!)
            }
        }

        val chatIds = chats.map { it.id }
        val perChatLatest = runCatching { fetchLatestMessageRowPerChat(chatIds) }
            .getOrElse {
                println("ChatRepository: per-chat latest messages failed: ${it.redactedRestMessage()}")
                emptyMap()
            }
        val bulkLatest = if (perChatLatest.size < chatIds.size) {
            fetchLatestMessageRowsBulkFallback(chatIds)
        } else {
            emptyMap()
        }
        val latestByChatId = bulkLatest.toMutableMap().apply { putAll(perChatLatest) }

        val unreadRows = if (chatIds.isNotEmpty()) {
            supabase.from("messages")
                .select {
                    filter {
                        isIn("chat_id", chatIds)
                        eq("is_read", false)
                        neq("user_id", userId)
                    }
                    limit(10_000)
                }
                .decodeList<MessageRow>()
        } else {
            emptyList()
        }
        val unreadByChatId = unreadRows.groupingBy { it.chatId }.eachCount()

        return connections.mapNotNull { connection ->
            val chatRow = chatByConnectionId[connection.id]
            val otherUserId = connection.user_ids.firstOrNull { it != userId } ?: return@mapNotNull null
            val otherUser = usersById[otherUserId] ?: User(
                id = otherUserId,
                name = "Connection",
                email = null,
                image = null,
                createdAt = 0L
            )

            val rawLastMessage = chatRow?.let { latestByChatId[it.id]?.toMessage() }
            val pairwise = if (chatRow != null) {
                val k = MessageCrypto.deriveKeysForConnection(connection.id, connection.user_ids)
                chatCryptoCache[chatRow.id] = ResolvedChatCrypto.Pairwise(k)
                ResolvedChatCrypto.Pairwise(k)
            } else null
            val lastMessage = rawLastMessage?.let { decryptMessage(it, pairwise) }
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
    }

    private suspend fun fetchGroupChatsWithDetails(userId: String): List<ChatWithDetails> {
        return try {
            val myGroupIds = supabase.from("group_members")
                .select(columns = Columns.list("group_id")) {
                    filter { eq("user_id", userId) }
                    limit(500)
                }
                .decodeList<GroupMemberGroupIdRow>()
                .map { it.groupId }
                .distinct()
            if (myGroupIds.isEmpty()) return emptyList()

            val groupChats = supabase.from("chats")
                .select {
                    filter { isIn("group_id", myGroupIds) }
                }
                .decodeList<ChatRow>()

            groupChats.forEach { r ->
                r.groupId?.let { rememberChatGroupRouting(r.id, it) }
            }

            val chatIds = groupChats.map { it.id }
            val latestByChatId = runCatching { fetchLatestMessageRowPerChat(chatIds) }
                .getOrElse { emptyMap() }

            val unreadRows = if (chatIds.isNotEmpty()) {
                supabase.from("messages")
                    .select {
                        filter {
                            isIn("chat_id", chatIds)
                            eq("is_read", false)
                            neq("user_id", userId)
                        }
                        limit(10_000)
                    }
                    .decodeList<MessageRow>()
            } else {
                emptyList()
            }
            val unreadByChatId = unreadRows.groupingBy { it.chatId }.eachCount()

            groupChats.mapNotNull { chatRow ->
                val gid = chatRow.groupId ?: return@mapNotNull null
                val group = supabase.from("groups")
                    .select(columns = Columns.list("id", "name", "created_by", "key_anchor_user_id")) {
                        filter { eq("id", gid) }
                        limit(1)
                    }
                    .decodeList<GroupRow>()
                    .firstOrNull() ?: return@mapNotNull null

                val memberRows = supabase.from("group_members")
                    .select(columns = Columns.list("user_id")) {
                        filter { eq("group_id", gid) }
                        limit(100)
                    }
                    .decodeList<GroupMemberUidRow>()
                val memberIds = memberRows.map { it.userId }.distinct()
                if (memberIds.isEmpty()) return@mapNotNull null

                val title = group.name.ifBlank { "Clique" }
                val anchor = group.keyAnchorUserId
                    ?: memberIds.filter { it != group.createdBy }.minOrNull()
                    ?: memberIds.firstOrNull()
                    ?: return@mapNotNull null
                val displayPeer = memberIds.firstOrNull { it != userId } ?: userId
                val usersById = fetchUsersByIdsSafe(memberIds).associateBy { it.id }
                val otherUser = usersById[displayPeer] ?: User(
                    id = gid,
                    name = title,
                    email = null,
                    image = null,
                    createdAt = 0L,
                )
                val groupMemberUsers = memberIds
                    .filter { it != userId }
                    .mapNotNull { uid -> usersById[uid] }
                    .sortedWith(
                        compareByDescending<User> {
                            maxOf(it.lastPolled ?: 0L, it.last_paired ?: 0L)
                        }.thenBy { it.name ?: "" }
                            .thenBy { it.id },
                    )

                val clique = GroupCliqueDetails(
                    groupId = gid,
                    name = title,
                    createdByUserId = group.createdBy,
                    keyAnchorUserId = anchor,
                    memberUserIds = memberIds,
                )

                val crypto = resolveChatCrypto(chatRow.id, userId)
                val rawLast = latestByChatId[chatRow.id]?.toMessage()
                val lastMessage = rawLast?.let { decryptMessage(it, crypto) }
                val synthetic = syntheticConnectionForGroupClique(
                    groupId = gid,
                    memberUserIds = memberIds,
                    lastMessageAt = lastMessage?.timeCreated ?: chatRow.updatedAt,
                )

                ChatWithDetails(
                    chat = Chat(
                        id = chatRow.id,
                        connectionId = null,
                        groupId = gid,
                        messages = emptyList(),
                    ),
                    connection = synthetic,
                    otherUser = otherUser,
                    lastMessage = lastMessage,
                    unreadCount = unreadByChatId[chatRow.id] ?: 0,
                    groupClique = clique,
                    groupMemberUsers = groupMemberUsers,
                )
            }.sortedByDescending { d ->
                d.lastMessage?.timeCreated
                    ?: d.connection.last_message_at
                    ?: d.connection.created
            }
        } catch (e: Exception) {
            println("ChatRepository: group chats fetch failed: ${e.redactedRestMessage()}")
            emptyList()
        }
    }

    /**
     * Short-lived cache so that back-to-back calls to [fetchUserChatsWithDetails] and
     * [fetchArchivedUserChatsWithDetails] (e.g. from [ChatViewModel.loadChats]) share a
     * single set of connection + junction queries instead of doubling network round-trips.
     */
    private var cachedJunctionData: Triple<List<Connection>, Set<String>, Set<String>>? = null
    private var cachedJunctionUserId: String? = null
    private var cachedJunctionTimestamp: Long = 0L
    private val junctionCacheTtlMs = 5_000L // 5 seconds

    // Fetch all chats for a user with details via API
    override suspend fun fetchUserChatsWithDetails(userId: String): List<ChatWithDetails> {
        return try {
            val (connections, archivedIds, hiddenIds) = getOrFetchJunctionData(userId)
            val activeRows = connections.filter { it.isActiveForUser(archivedIds, hiddenIds) }

            coroutineScope {
                val direct = async { buildChatsWithDetailsForConnections(userId, activeRows) }
                val groups = async { fetchGroupChatsWithDetails(userId) }
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
    }

    override suspend fun fetchArchivedUserChatsWithDetails(userId: String): List<ChatWithDetails> {
        return try {
            val (connections, archivedIds, hiddenIds) = getOrFetchJunctionData(userId)
            val archivedRows = connections.filter {
                it.isArchivedChannelForUser(archivedIds, hiddenIds)
            }

            buildChatsWithDetailsForConnections(userId, archivedRows)
        } catch (e: Exception) {
            println("Error fetching archived user chats: ${e.redactedRestMessage()}")
            emptyList()
        }
    }

    /**
     * Returns cached junction data if still valid for [userId], otherwise fetches
     * connections, archived IDs, and hidden IDs in parallel and caches the result.
     */
    private suspend fun getOrFetchJunctionData(
        userId: String,
    ): Triple<List<Connection>, Set<String>, Set<String>> {
        val now = Clock.System.now().toEpochMilliseconds()
        val cached = cachedJunctionData
        if (cached != null && cachedJunctionUserId == userId && now - cachedJunctionTimestamp < junctionCacheTtlMs) {
            return cached
        }
        val result = fetchConnectionsWithJunctionIds(userId)
        cachedJunctionData = result
        cachedJunctionUserId = userId
        cachedJunctionTimestamp = now
        return result
    }

    /**
     * Fetches connections, archived IDs, and hidden IDs for [userId] in parallel.
     */
    private suspend fun fetchConnectionsWithJunctionIds(
        userId: String,
    ): Triple<List<Connection>, Set<String>, Set<String>> {
        val snapshot = supabaseRepository.fetchUserConnectionsSnapshot(userId)
        return Triple(snapshot.connections, snapshot.archivedConnectionIds, snapshot.hiddenConnectionIds)
    }

    override suspend fun fetchMessagesForChat(chatId: String, viewerUserId: String?): List<Message> {
        return try {
            val crypto = resolveChatCrypto(chatId, viewerUserId)
            supabase.from("messages")
                .select {
                    filter {
                        eq("chat_id", chatId)
                    }
                    order("time_created", Order.ASCENDING)
                }
                .decodeList<Message>()
                .map { decryptMessage(it, crypto) }
        } catch (e: Exception) {
            println("Error fetching messages: ${e.redactedRestMessage()}")
            emptyList()
        }
    }

    override suspend fun sendMessage(
        chatId: String,
        userId: String,
        content: String,
        messageType: String,
        metadata: JsonElement?,
    ): Message? {
        return try {
            val crypto = resolveChatCrypto(chatId, userId)
            val wireContent = when {
                messageType == "call_log" -> content
                crypto is ResolvedChatCrypto.GroupMaster ->
                    MessageCrypto.encryptGroupMessageContent(content, crypto.masterKey)
                crypto is ResolvedChatCrypto.Pairwise ->
                    MessageCrypto.encryptContent(content, crypto.keys)
                else -> content
            }

            val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            val payload = buildJsonObject {
                put("chat_id", chatId)
                put("user_id", userId)
                put("content", wireContent)
                put("time_created", now)
                put("message_type", messageType)
                if (metadata != null) put("metadata", metadata)
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
                            eq("message_type", messageType)
                        }
                        order("time_created", Order.DESCENDING)
                        limit(1)
                    }
                    .decodeList<Message>()
                    .firstOrNull()
                    ?: throw it
            }

            val decrypted = decryptMessage(inserted, crypto)

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

            if (messageType != "call_log") {
                runCatching {
                    chatPushNotifier.notifyNewMessage(
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
                existing.connectionId?.let { rememberChatConnectionRouting(existing.id, it) }
                return Chat(
                    id = existing.id,
                    connectionId = existing.connectionId,
                    groupId = existing.groupId,
                    messages = emptyList(),
                )
            }

            val inserted = supabase.from("chats")
                .insert(ChatInsert(connectionId = connectionId)) {
                    select()
                }
                .decodeSingle<ChatRow>()

            inserted.connectionId?.let { rememberChatConnectionRouting(inserted.id, it) }
            Chat(
                id = inserted.id,
                connectionId = inserted.connectionId,
                groupId = inserted.groupId,
                messages = emptyList(),
            )
        } catch (e: Exception) {
            println("Error ensuring chat for connection $connectionId: ${e.redactedRestMessage()}")
            null
        }
    }

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
            println("Error marking messages as read: ${e.redactedRestMessage()}")
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
     * threads) never read the shared [chatCryptoCache] directly — avoiding
     * thread-visibility issues in Kotlin/Native release builds.
     */
    override suspend fun subscribeToMessages(
        chatId: String,
        viewerUserId: String,
    ): Pair<ChatMessageSubscription, Flow<MessageChangeEvent>> {
        val preloaded = resolveChatCrypto(chatId, viewerUserId)

        val channel = supabase.channel("messages:$chatId")
        var resolvedCrypto = preloaded

        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
        }.mapNotNull { action ->
            val crypto = resolvedCrypto
                ?: resolveChatCrypto(chatId, viewerUserId).also { resolvedCrypto = it }
            when (action) {
                is PostgresAction.Insert -> {
                    val row = action.decodeRecord<MessageRow>()
                    if (row.chatId == chatId) MessageChangeEvent.Insert(decryptMessage(row.toMessage(), crypto)) else null
                }
                is PostgresAction.Update -> {
                    val row = action.decodeRecord<MessageRow>()
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
        }

        return SupabaseMessageSubscription(channel) to changeFlow
    }

    override suspend fun subscribeToMessageInserts(): Pair<ChatMessageSubscription, Flow<MessageListInsertEvent>> {
        val channel = supabase.channel("clicks:msg-list:${Clock.System.now().toEpochMilliseconds()}")
        val flow = channelFlow {
            channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "messages"
            }.collect { action ->
                val row = when (action) {
                    is PostgresAction.Insert -> runCatching { action.decodeRecord<MessageRow>() }.getOrNull()
                    is PostgresAction.Update -> runCatching { action.decodeRecord<MessageRow>() }.getOrNull()
                    else -> null
                } ?: return@collect
                val listKey = resolveListKeyForChat(row.chatId) ?: return@collect
                val cached = chatCryptoCache[row.chatId]
                val crypto = cached
                    ?: getEncryptionKeysForConnection(listKey)?.let { ResolvedChatCrypto.Pairwise(it) }
                val rawMessage = row.toMessage()
                val message = when {
                    crypto != null -> decryptMessage(rawMessage, crypto)
                    MessageCrypto.isAnyE2eeWireContent(rawMessage.content) ->
                        rawMessage.copy(content = "New message")
                    else -> rawMessage
                }
                send(MessageListInsertEvent(connectionId = listKey, message = message))
            }
        }
        return SupabaseMessageSubscription(channel) to flow
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

            Chat(id = row.id, connectionId = row.connectionId, groupId = row.groupId, messages = emptyList())
        } catch (e: Exception) {
            println("Error fetching chat: ${e.redactedRestMessage()}")
            null
        }
    }

    // Fetch chat with details by chat ID via API
    override suspend fun fetchChatWithDetails(chatId: String, currentUserId: String): ChatWithDetails? {
        return try {
            val fromList = fetchUserChatsWithDetails(currentUserId)
                .firstOrNull { it.connection.id == chatId || it.chat.id == chatId }
            if (fromList != null) {
                if (!fromList.chat.id.isNullOrBlank() || fromList.groupClique != null) {
                    return fromList
                }
                val ensured = ensureChatForConnection(fromList.connection.id) ?: return fromList
                return fromList.copy(
                    chat = fromList.chat.copy(
                        id = ensured.id,
                        connectionId = fromList.connection.id,
                    ),
                )
            }
            loadChatWithDetailsByRawId(chatId, currentUserId)
        } catch (e: Exception) {
            println("Error fetching chat with details: ${e.redactedRestMessage()}")
            null
        }
    }

    private suspend fun loadChatWithDetailsByRawId(chatId: String, currentUserId: String): ChatWithDetails? {
        val row = supabase.from("chats")
            .select {
                filter { eq("id", chatId) }
                limit(1)
            }
            .decodeList<ChatRow>()
            .firstOrNull() ?: return null
        return when {
            row.groupId != null -> {
                rememberChatGroupRouting(row.id, row.groupId)
                fetchGroupChatsWithDetails(currentUserId).firstOrNull { it.chat.id == row.id }
            }
            !row.connectionId.isNullOrBlank() -> {
                rememberChatConnectionRouting(row.id, row.connectionId)
                val conn = supabase.from("connections")
                    .select(columns = connectionsSelectWithEncounters) {
                        filter { eq("id", row.connectionId) }
                        limit(1)
                    }
                    .decodeList<Connection>()
                    .map { it.withEncountersSortedNewestFirst() }
                    .firstOrNull() ?: return null
                buildChatsWithDetailsForConnections(currentUserId, listOf(conn))
                    .firstOrNull { it.chat.id == row.id || it.connection.id == row.connectionId }
            }
            else -> null
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

            val userIds = when {
                chat.groupId != null -> {
                    supabase.from("group_members")
                        .select(columns = Columns.list("user_id")) {
                            filter { eq("group_id", chat.groupId) }
                            limit(100)
                        }
                        .decodeList<GroupMemberUidRow>()
                        .map { it.userId }
                }
                !chat.connectionId.isNullOrBlank() -> {
                    val connection = supabase.from("connections")
                        .select(columns = connectionsSelectWithEncounters) {
                            filter {
                                eq("id", chat.connectionId)
                            }
                            limit(1)
                        }
                        .decodeList<Connection>()
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

    // Get user by ID - helper method for getting user details
    override suspend fun getUserById(userId: String): User? {
        return try {
            fetchUsersByIdsSafe(listOf(userId)).firstOrNull()
        } catch (e: Exception) {
            println("Error fetching user: ${e.redactedRestMessage()}")
            null
        }
    }

    // Update a message via API
    override suspend fun updateMessage(chatId: String, messageId: String, userId: String, content: String): Message? {
        return try {
            val authToken = tokenStorage.getJwt() ?: return null
            val result = apiClient.updateMessage(chatId, messageId, userId, content, authToken)
            result.getOrElse {
                println("Error updating message: ${it.redactedRestMessage()}")
                null
            }
        } catch (e: Exception) {
            println("Error updating message: ${e.redactedRestMessage()}")
            null
        }
    }

    // Delete a message via API
    override suspend fun deleteMessage(chatId: String, messageId: String, userId: String): Boolean {
        return try {
            val authToken = tokenStorage.getJwt() ?: return false
            val result = apiClient.deleteMessage(chatId, messageId, userId, authToken)
            result.getOrElse {
                println("Error deleting message: ${it.redactedRestMessage()}")
                false
            }
        } catch (e: Exception) {
            println("Error deleting message: ${e.redactedRestMessage()}")
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
            println("Error fetching reactions: ${e.redactedRestMessage()}")
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
            println("Error adding reaction: ${e.redactedRestMessage()}")
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
            println("Error removing reaction: ${e.redactedRestMessage()}")
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
            session.channel.broadcast(
                event = "typing",
                message = buildJsonObject { put("userId", userId) },
            )
        } catch (e: Exception) {
            println("ChatRepository: typing broadcast failed: ${e.redactedRestMessage()}")
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
                presence {
                    key = currentUserId
                }
            }

            val typingFlow = MutableSharedFlow<TypingStatus>(
                extraBufferCapacity = 32,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
            val peerOnline = MutableStateFlow(false)
            /** Presence keys are configured as each client's user id; diff joins/leaves are authoritative. */
            val presenceKeysOnline = mutableSetOf<String>()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val broadcastFlow = channel.broadcastFlow<TypingBroadcastPayload>(event = "typing")
            val presenceFlow = channel.presenceChangeFlow()

            val presenceJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    presenceFlow.collect { action ->
                        action.leaves.keys.forEach { key -> presenceKeysOnline.remove(key) }
                        action.joins.keys.forEach { key -> presenceKeysOnline.add(key) }
                        action.joins.values.forEach { p ->
                            userIdFromPresence(p)?.let { presenceKeysOnline.add(it) }
                        }
                        action.leaves.values.forEach { p ->
                            userIdFromPresence(p)?.let { presenceKeysOnline.remove(it) }
                        }
                        peerOnline.value = peerUserId in presenceKeysOnline
                    }
                } catch (_: Exception) {
                }
            }

            try {
                channel.subscribe(blockUntilSubscribed = true)
                channel.track(buildJsonObject { put("userId", currentUserId) })
            } catch (e: Exception) {
                println("ChatRepository: join chat ephemeral failed: ${e.redactedRestMessage()}")
                presenceJob.cancel()
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

            val presenceRefreshJob = scope.launch {
                while (isActive) {
                    delay(PRESENCE_TRACK_REFRESH_MS)
                    runCatching {
                        channel.track(buildJsonObject { put("userId", currentUserId) })
                    }
                }
            }

            ephemeralSessions[chatId] = ChatEphemeralSession(
                channel = channel,
                peerUserId = peerUserId,
                typingFlow = typingFlow,
                peerOnline = peerOnline,
                scope = scope,
                jobs = listOf(broadcastJob, presenceJob, presenceRefreshJob),
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
        } catch (e: Exception) { println("Error updating status: ${e.redactedRestMessage()}"); false }
    }

    override suspend fun forwardMessage(messageId: String, targetChatId: String, userId: String): Message? {
        return try {
            val authToken = tokenStorage.getJwt() ?: return null
            apiClient.forwardMessage(messageId, targetChatId, userId, authToken).getOrElse { null }
        } catch (e: Exception) { println("Error forwarding message: ${e.redactedRestMessage()}"); null }
    }

    override suspend fun searchMessages(chatId: String, query: String): List<Message> {
        return try {
            val allMessages = fetchMessagesForChat(chatId)
            allMessages.filter { it.content.contains(query, ignoreCase = true) }
        } catch (e: Exception) {
            println("Error searching messages: ${e.redactedRestMessage()}")
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
            println("Error resolving chat id for connection $connectionId: ${e.redactedRestMessage()}")
            null
        }
    }

    override suspend fun resolveChatIdForGroupId(groupId: String): String? {
        return try {
            supabase.from("chats")
                .select(columns = Columns.list("id")) {
                    filter { eq("group_id", groupId) }
                    limit(1)
                }
                .decodeList<ChatIdOnly>()
                .firstOrNull()
                ?.id
        } catch (e: Exception) {
            println("Error resolving chat id for group $groupId: ${e.redactedRestMessage()}")
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

    override suspend fun createVerifiedClique(
        memberUserIds: List<String>,
        encryptedKeysByUserId: Map<String, String>,
        initialGroupName: String,
    ): Result<String> = runCatching {
        val ids = memberUserIds.distinct().sorted()
        require(ids.size >= 2) { "Clique needs at least two members" }
        val body = buildJsonObject {
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

    override suspend fun leaveClique(groupId: String): Result<Unit> = runCatching {
        require(groupId.isNotBlank())
        val body = buildJsonObject { put("target_group_id", JsonPrimitive(groupId)) }
        supabase.postgrest.rpc("leave_clique", body)
    }

    override suspend fun deleteClique(groupId: String): Result<Unit> = runCatching {
        require(groupId.isNotBlank())
        val body = buildJsonObject { put("target_group_id", JsonPrimitive(groupId)) }
        supabase.postgrest.rpc("delete_clique", body)
    }

    override suspend fun renameClique(groupId: String, newName: String): Result<Unit> = runCatching {
        require(groupId.isNotBlank())
        val body = buildJsonObject {
            put("target_group_id", JsonPrimitive(groupId))
            put("new_name", JsonPrimitive(newName))
        }
        supabase.postgrest.rpc("rename_clique", body)
    }

    override fun clearChatListLocalCaches() {
        cachedJunctionData = null
        cachedJunctionUserId = null
        cachedJunctionTimestamp = 0L
    }

    override suspend fun verifiedCliqueEdgesExist(memberUserIds: List<String>): Boolean =
        runCatching {
            val ids = memberUserIds.distinct().sorted()
            if (ids.size < 2) return@runCatching false
            val body = buildJsonObject {
                put("p_member_ids", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
            }
            val rpcResult = supabase.postgrest.rpc("verified_clique_edges_exist", body)
            parseRpcBoolean(rpcResult.data)
        }.getOrElse { e ->
            println("ChatRepository: verifiedCliqueEdgesExist failed: ${e.redactedRestMessage()}")
            false
        }

    private fun parseRpcBoolean(body: String): Boolean {
        val t = body.trim().trim('"')
        if (t.equals("true", ignoreCase = true)) return true
        if (t.equals("false", ignoreCase = true)) return false
        val el = runCatching { Json.parseToJsonElement(t) }.getOrNull() ?: return false
        return when (el) {
            is JsonPrimitive -> el.content.equals("true", ignoreCase = true)
            else -> false
        }
    }

    private fun decodeUuidScalarFromRpc(body: String): String {
        val t = body.trim()
        if (t.length in 32..40 && t.count { it == '-' } == 4) return t
        val el = Json.parseToJsonElement(t)
        return when (el) {
            is JsonPrimitive -> el.content.trim().trim('"')
            is JsonArray -> el.first().jsonPrimitive.content.trim().trim('"')
            else -> error("Unexpected RPC payload: $t")
        }
    }

    override suspend fun uploadChatMedia(bytes: ByteArray, objectPath: String, contentType: String): String? {
        if (bytes.isEmpty()) return null
        return try {
            supabase.storage.from(CHAT_MEDIA_BUCKET).upload(objectPath, bytes) {
                upsert = true
            }
            supabase.storage.from(CHAT_MEDIA_BUCKET).publicUrl(objectPath)
        } catch (e: Exception) {
            println("ChatRepository: uploadChatMedia failed: ${e.redactedRestMessage()}")
            null
        }
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
private const val PRESENCE_TRACK_REFRESH_MS = 25_000L
private const val GLOBAL_PRESENCE_CHANNEL = "room:presence"
