package compose.project.click.click.data.repository

import compose.project.click.click.chat.attachments.AttachmentCrypto
import compose.project.click.click.chat.attachments.ChatAttachmentValidator
import compose.project.click.click.crypto.MessageCrypto
import compose.project.click.click.auth.LocalSessionCache
import compose.project.click.click.data.CHAT_ATTACHMENTS_BUCKET
import compose.project.click.click.data.CHAT_MEDIA_BUCKET
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.api.ChatApiClient
import compose.project.click.click.data.models.*
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.notifications.ChatPushNotifier
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Presence
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.track
import compose.project.click.click.data.AppDataManager
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import compose.project.click.click.util.compressOutgoingChatImageForUpload
import compose.project.click.click.util.chatMediaDispatcher
import compose.project.click.click.util.isHardAuthFailure
import compose.project.click.click.util.isOfflineNetworkFailure
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import compose.project.click.click.util.chatMediaVaultLocalPath
import compose.project.click.click.util.imageVaultFileExtension
import compose.project.click.click.util.readChatMediaVaultBytes
import compose.project.click.click.util.vaultCacheExtension
import compose.project.click.click.util.writeChatMediaVaultFile

internal suspend fun SupabaseChatRepository.fetchUsersByIdsSafe(userIds: List<String>): List<User> {
    if (userIds.isEmpty()) return emptyList()

    return supabaseRepository.fetchUsersByIds(userIds)
}

/**
 * Batch inbox previews via single RPC (replaces per-chat latest-message queries).
 */
internal suspend fun SupabaseChatRepository.fetchInboxPreviewsFromRpc(): Map<String, SupabaseChatRepository.MessageRow> {
    return try {
        @Serializable
        data class InboxPreviewRow(
            @SerialName("chat_id") val chatId: String,
            @SerialName("connection_id") val connectionId: String? = null,
            @SerialName("last_message_id") val lastMessageId: String? = null,
            @SerialName("last_message_user_id") val lastMessageUserId: String? = null,
            @SerialName("last_message_content") val lastMessageContent: String? = null,
            @SerialName("last_message_time_created") val lastMessageTimeCreated: Long? = null,
            @SerialName("last_message_type") val lastMessageType: String? = null,
            @SerialName("last_message_metadata") val lastMessageMetadata: JsonObject? = null,
            @SerialName("last_message_is_read") val lastMessageIsRead: Boolean = false,
            @SerialName("unread_count") val unreadCount: Long = 0L,
        )
        val rows = supabase.postgrest.rpc("get_inbox_previews").decodeList<InboxPreviewRow>()
        buildMap {
            for (row in rows) {
                val msgId = row.lastMessageId ?: continue
                val created = row.lastMessageTimeCreated ?: continue
                put(
                    row.chatId,
                    SupabaseChatRepository.MessageRow(
                        id = msgId,
                        chatId = row.chatId,
                        userId = row.lastMessageUserId.orEmpty(),
                        content = row.lastMessageContent.orEmpty(),
                        timeCreated = created,
                        timeEdited = null,
                        isRead = row.lastMessageIsRead,
                        messageType = row.lastMessageType ?: "text",
                        metadata = row.lastMessageMetadata,
                    ),
                )
            }
        }
    } catch (e: Exception) {
        println("ChatRepository: get_inbox_previews RPC failed: ${e.redactedRestMessage()}")
        emptyMap()
    }
}

/**
 * Newest message per chat. Prefers [fetchInboxPreviewsFromRpc]; falls back to per-chat queries.
 */
internal suspend fun SupabaseChatRepository.fetchLatestMessageRowPerChat(chatIds: List<String>): Map<String, SupabaseChatRepository.MessageRow> {
    if (chatIds.isEmpty()) return emptyMap()
    val fromRpc = fetchInboxPreviewsFromRpc()
    if (fromRpc.isNotEmpty()) {
        return chatIds.distinct().mapNotNull { id -> fromRpc[id]?.let { id to it } }.toMap()
    }
    val distinctIds = chatIds.distinct()
    val limitParallel = Semaphore(12)
    suspend fun queryLatestRow(chatId: String): SupabaseChatRepository.MessageRow? {
        return supabase.from("messages")
            .select {
                filter { eq("chat_id", chatId) }
                order("time_created", Order.DESCENDING)
                limit(1)
            }
            .decodeList<SupabaseChatRepository.MessageRow>()
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
internal suspend fun SupabaseChatRepository.fetchLatestMessageRowsBulkFallback(chatIds: List<String>): Map<String, SupabaseChatRepository.MessageRow> {
    if (chatIds.isEmpty()) return emptyMap()
    return try {
        val messages = supabase.from("messages")
            .select {
                filter { isIn("chat_id", chatIds) }
                order("time_created", Order.DESCENDING)
                limit(25_000)
            }
            .decodeList<SupabaseChatRepository.MessageRow>()
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

internal suspend fun SupabaseChatRepository.buildChatsWithDetailsForConnections(
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
                .decodeList<SupabaseChatRepository.ChatRow>()
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
            .decodeList<SupabaseChatRepository.MessageRow>()
    } else {
        emptyList()
    }
    val unreadByChatId = unreadRows.groupingBy { it.chatId }.eachCount()

    // Batch pairwise-key derivations so the crypto cache is written to once
    // under a single withLock instead of per-iteration inside mapNotNull
    // (which is a non-suspending lambda). Keeps NASA-P10 bounded-loop OK.
    val derivedPairwise = withContext(Dispatchers.Default) {
        val result = HashMap<String, ChatSessionCaches.ResolvedChatCrypto.Pairwise>(connections.size)
        for (connection in connections) {
            val chatRow = chatByConnectionId[connection.id] ?: continue
            val keys = MessageCrypto.deriveKeysForConnection(connection.id, connection.user_ids)
            result[chatRow.id] = ChatSessionCaches.ResolvedChatCrypto.Pairwise(keys)
        }
        result
    }
    if (derivedPairwise.isNotEmpty()) {
        for ((chatId, crypto) in derivedPairwise) {
            ChatSessionCaches.putPairwiseCrypto(chatId, crypto.keys)
        }
    }

    return withContext(Dispatchers.Default) {
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

            val rawLastMessage = chatRow?.let { latestByChatId[it.id]?.toMessage() }
            val pairwise = chatRow?.let { derivedPairwise[it.id] }
            val lastMessage = rawLastMessage?.let { decryptMessageOnCurrentThread(it, pairwise) }
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
}

internal fun SupabaseChatRepository.appDataJunctionSnapshot(userId: String): Triple<List<Connection>, Set<String>, Set<String>>? {
    if (compose.project.click.click.data.AppDataManager.currentUser.value?.id != userId) return null
    if (!compose.project.click.click.data.AppDataManager.isDataLoaded.value) return null
    val connections = compose.project.click.click.data.AppDataManager.connections.value
    if (connections.isEmpty() &&
        compose.project.click.click.data.AppDataManager.inboxFeedChats.value.isEmpty()
    ) {
        return null
    }
    return Triple(
        connections,
        compose.project.click.click.data.AppDataManager.archivedConnectionIds.value,
        compose.project.click.click.data.AppDataManager.hiddenConnectionIds.value,
    )
}

/**
 * Resolve viewer id for group listing. Prefer live GoTrue user; fall back through JWT `sub`,
 * the caller [userId], then [AppDataManager] — session import historically set user=null.
 */
internal suspend fun SupabaseChatRepository.resolveSignedInUserIdForGroups(userId: String, jwt: String?): String {
    supabase.auth.currentUserOrNull()?.id?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val token = jwt?.trim()?.takeIf { it.isNotEmpty() } ?: tokenStorage.getJwt()?.trim()
    LocalSessionCache.parseIdentityFromJwt(token.orEmpty())
        ?.userId?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { return it }
    userId.trim().takeIf { it.isNotEmpty() }?.let { return it }
    return AppDataManager.currentUser.value?.id?.trim().orEmpty()
}

/**
 * Returns cached junction data if still valid for [userId], otherwise fetches
 * connections, archived IDs, and hidden IDs in parallel and caches the result.
 */
internal suspend fun SupabaseChatRepository.getOrFetchJunctionData(
    userId: String,
): Triple<List<Connection>, Set<String>, Set<String>> {
    val now = Clock.System.now().toEpochMilliseconds()
    val cached = cachedJunctionData
    if (cached != null && cachedJunctionUserId == userId && now - cachedJunctionTimestamp < junctionCacheTtlMs) {
        return cached
    }
    appDataJunctionSnapshot(userId)?.let { snapshot ->
        cachedJunctionData = snapshot
        cachedJunctionUserId = userId
        cachedJunctionTimestamp = now
        return snapshot
    }
    val result = fetchConnectionsWithJunctionIds(userId)
    cachedJunctionData = result
    cachedJunctionUserId = userId
    cachedJunctionTimestamp = now
    return result
}

/**
 * Fetches connections, archived IDs, and hidden IDs for [userId] in parallel.
 * Falls back to [AppDataManager] local SSOT when the network is unavailable.
 */
internal suspend fun SupabaseChatRepository.fetchConnectionsWithJunctionIds(
    userId: String,
): Triple<List<Connection>, Set<String>, Set<String>> {
    return try {
        val snapshot = supabaseRepository.fetchUserConnectionsSnapshot(userId, runStaleSweep = false)
        Triple(snapshot.connections, snapshot.archivedConnectionIds, snapshot.hiddenConnectionIds)
    } catch (e: Exception) {
        if (!e.isOfflineNetworkFailure()) throw e
        println("SupabaseChatRepository: junction fetch offline — using local SSOT: ${e.redactedRestMessage()}")
        Triple(
            AppDataManager.connections.value,
            AppDataManager.archivedConnectionIds.value,
            AppDataManager.hiddenConnectionIds.value,
        )
    }
}

internal suspend fun SupabaseChatRepository.rememberChatConnectionRouting(chatId: String, connectionId: String) {
    ChatSessionCaches.rememberConnectionRouting(chatId, connectionId)
}

internal suspend fun SupabaseChatRepository.rememberChatGroupRouting(chatId: String, groupId: String) {
    ChatSessionCaches.rememberGroupRouting(chatId, groupId)
}

/** Returns a connection id **or** a group id for Clicks list routing ([bumpConnectionInChatList]). */
internal suspend fun SupabaseChatRepository.resolveListKeyForChat(chatId: String): String? {
    ChatSessionCaches.peekListKeyForChat(chatId)?.let { return it }
    AppDataManager.connections.value
        .firstOrNull { it.chat.id == chatId }
        ?.let { conn ->
            rememberChatConnectionRouting(chatId, conn.id)
            return conn.id
        }
    AppDataManager.inboxFeedChats.value
        .firstOrNull { it.chat.id == chatId }
        ?.let { inboxRow ->
            val groupId = inboxRow.groupClique?.groupId
            if (groupId != null) {
                rememberChatGroupRouting(chatId, groupId)
                return groupId
            }
            rememberChatConnectionRouting(chatId, inboxRow.connection.id)
            return inboxRow.connection.id
        }
    val row = supabase.from("chats")
        .select(columns = Columns.list("connection_id", "group_id")) {
            filter { eq("id", chatId) }
            limit(1)
        }
        .decodeList<SupabaseChatRepository.ChatRoutingRow>()
        .firstOrNull() ?: return null
    when {
        row.groupId != null -> rememberChatGroupRouting(chatId, row.groupId)
        !row.connectionId.isNullOrBlank() -> rememberChatConnectionRouting(chatId, row.connectionId)
    }
    return row.connectionId ?: row.groupId
}

internal fun SupabaseChatRepository.distinctPeerIdsForSearch(raw: List<String>): List<String> {
    val out = LinkedHashSet<String>(minOf(raw.size, 400))
    for (id in raw) {
        val t = id.trim()
        if (t.isNotEmpty()) out.add(t)
        if (out.size >= 400) break
    }
    return out.toList()
}

internal suspend fun SupabaseChatRepository.loadInterestTagsForPeers(peerIds: List<String>): Map<String, List<String>> {
    val rows = supabase.from("user_interests")
        .select {
            filter { isIn("user_id", peerIds) }
            limit(500)
        }
        .decodeList<SupabaseChatRepository.UserInterestRowDb>()
    if (rows.isEmpty()) return emptyMap()
    val map = HashMap<String, List<String>>(rows.size)
    for (row in rows) {
        map[row.userId] = row.tags
    }
    return map
}

internal suspend fun SupabaseChatRepository.loadActiveIntentsForPeers(peerIds: List<String>): Map<String, List<AvailabilityIntentRow>> {
    val nowIso = Clock.System.now().toString()
    val rows = supabase.from("availability_intents")
        .select {
            filter {
                isIn("user_id", peerIds)
                gte("expires_at", nowIso)
            }
            limit(500)
        }
        .decodeList<AvailabilityIntentRow>()
    if (rows.isEmpty()) return emptyMap()
    val map = HashMap<String, MutableList<AvailabilityIntentRow>>()
    for (row in rows) {
        val uid = row.userId?.trim()?.takeIf { it.isNotEmpty() } ?: continue
        val bucket = map.getOrPut(uid) { ArrayList(4) }
        bucket.add(row)
    }
    return map
}

internal fun SupabaseChatRepository.parseRpcBoolean(body: String): Boolean {
    val t = body.trim().trim('"')
    if (t.equals("true", ignoreCase = true)) return true
    if (t.equals("false", ignoreCase = true)) return false
    val el = runCatching { Json.parseToJsonElement(t) }.getOrNull() ?: return false
    return when (el) {
        is JsonPrimitive -> el.content.equals("true", ignoreCase = true)
        else -> false
    }
}

internal fun SupabaseChatRepository.decodeUuidScalarFromRpc(body: String): String {
    val t = body.trim()
    if (t.length in 32..40 && t.count { it == '-' } == 4) return t
    val el = runCatching { Json.parseToJsonElement(t) }.getOrElse {
        throw IllegalStateException("Unexpected RPC payload: $t")
    }
    return when (el) {
        is JsonPrimitive -> el.content.trim().trim('"')
        is JsonArray -> {
            val first = el.firstOrNull()
                ?: throw IllegalStateException("Unexpected empty RPC payload")
            first.jsonPrimitive.content.trim().trim('"')
        }
        else -> throw IllegalStateException("Unexpected RPC payload: $t")
    }
}

internal suspend fun SupabaseChatRepository.fetchGroupUserChatsWithDetailsImpl(userId: String): List<ChatWithDetails> {
    // Mirror click-web DashboardView: membership → chats/groups/members; crypto optional.
    // Offline session import can leave GoTrue with a JWT but user=null — never hard-require
    // currentUserOrNull(); resolve id from JWT / caller / AppDataManager instead.
    val jwt = runCatching { ensureFreshJwtForChat() }.getOrNull()
    if (supabase.auth.currentUserOrNull()?.id.isNullOrBlank()) {
        runCatching { SupabaseConfig.importStoredSessionWithoutRefresh(tokenStorage) }
        if (supabase.auth.currentUserOrNull()?.id.isNullOrBlank()) {
            runCatching { authRepository.refreshSession() }
        }
    }
    val memberUserId = resolveSignedInUserIdForGroups(userId, jwt)
    if (memberUserId.isEmpty()) {
        println("ChatRepository: group chats skipped — no signed-in user id (jwt=${!jwt.isNullOrBlank()})")
        return emptyList()
    }

    val myGroupIds = supabase.from("group_members")
        .select(columns = Columns.list("group_id")) {
            filter { eq("user_id", memberUserId) }
            limit(500)
        }
        .decodeList<SupabaseChatRepository.GroupMemberGroupIdRow>()
        .map { it.groupId }
        .distinct()
    println("ChatRepository: group membership for $memberUserId → ${myGroupIds.size} group(s)")
    if (myGroupIds.isEmpty()) return emptyList()

    val (groupChats, allGroups, allMemberRows) = coroutineScope {
        val chatsDeferred = async {
            supabase.from("chats")
                .select(columns = Columns.list("id", "group_id", "updated_at")) {
                    filter { isIn("group_id", myGroupIds) }
                }
                .decodeList<SupabaseChatRepository.GroupChatListRow>()
        }
        val groupsDeferred = async {
            // Match click-web: id/name/created_by only — extra columns must not block listing.
            supabase.from("groups")
                .select(columns = Columns.list("id", "name", "created_by")) {
                    filter { isIn("id", myGroupIds) }
                }
                .decodeList<SupabaseChatRepository.GroupRow>()
        }
        val membersDeferred = async {
            supabase.from("group_members")
                .select(columns = Columns.list("group_id", "user_id")) {
                    filter { isIn("group_id", myGroupIds) }
                    limit(5000)
                }
                .decodeList<SupabaseChatRepository.GroupMemberFullRow>()
        }
        Triple(chatsDeferred.await(), groupsDeferred.await(), membersDeferred.await())
    }

    println(
        "ChatRepository: group chats=${groupChats.size} groups=${allGroups.size} " +
            "memberRows=${allMemberRows.size}",
    )

    groupChats.forEach { r ->
        rememberChatGroupRouting(r.id, r.groupId)
    }

    val chatIds = groupChats.map { it.id }
    val (latestByChatId, unreadByChatId, allUsers) = coroutineScope {
        val latestDeferred = async {
            runCatching { fetchLatestMessageRowPerChat(chatIds) }.getOrElse { emptyMap() }
        }
        val unreadDeferred = async {
            runCatching {
                if (chatIds.isEmpty()) return@runCatching emptyMap()
                supabase.from("messages")
                    .select {
                        filter {
                            isIn("chat_id", chatIds)
                            eq("is_read", false)
                            neq("user_id", memberUserId)
                        }
                        limit(10_000)
                    }
                    .decodeList<SupabaseChatRepository.MessageRow>()
                    .groupingBy { it.chatId }.eachCount()
            }.getOrElse { emptyMap() }
        }
        val allMemberUserIds = allMemberRows.map { it.userId }.distinct()
        val usersDeferred = async {
            runCatching { fetchUsersByIdsSafe(allMemberUserIds) }.getOrElse { emptyList() }
        }
        Triple(latestDeferred.await(), unreadDeferred.await(), usersDeferred.await())
    }

    val groupsById = allGroups.associateBy { it.id }
    val membersByGroupId = allMemberRows.groupBy { it.groupId }
    val usersById = allUsers.associateBy { it.id }

    // Do NOT resolveChatCrypto here — website list path never does, and hanging key
    // unwraps were leaving Groups permanently empty while direct chats painted.
    return withContext(Dispatchers.Default) {
        groupChats.mapNotNull { chatRow ->
            val gid = chatRow.groupId
            val group = groupsById[gid] ?: return@mapNotNull null

            // Prefer full member list; fall back to viewer-only if RLS hides peers.
            val memberIds = membersByGroupId[gid]
                ?.map { it.userId }?.distinct()
                .orEmpty()
                .ifEmpty { listOf(memberUserId) }

            val title = group.name.ifBlank { "Clique" }
            val anchor = group.keyAnchorUserId
                ?: memberIds.filter { it != group.createdBy }.minOrNull()
                ?: memberIds.firstOrNull()
                ?: memberUserId
            val displayPeer = memberIds.firstOrNull { it != memberUserId } ?: memberUserId
            val otherUser = usersById[displayPeer] ?: User(
                id = gid,
                name = title,
                email = null,
                image = group.avatarUrl,
                createdAt = 0L,
            )
            val groupMemberUsers = memberIds
                .filter { it != memberUserId }
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
                avatarUrl = group.avatarUrl,
            )

            // Raw preview only — decrypt when the thread is opened.
            val lastMessage = latestByChatId[chatRow.id]?.toMessage()
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
                otherUser = otherUser.copy(
                    name = title,
                    image = otherUser.image ?: group.avatarUrl,
                ),
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
    }
}
