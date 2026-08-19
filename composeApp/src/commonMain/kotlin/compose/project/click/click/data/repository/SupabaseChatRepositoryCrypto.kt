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

internal suspend fun SupabaseChatRepository.ensureFreshJwtForChat(): String? =
    compose.project.click.click.data.auth.EnsureFreshAccessToken.get(
        tokenStorage = tokenStorage,
        authRepository = authRepository,
    )


internal suspend fun SupabaseChatRepository.refreshedJwtAfterAuthFailure(): String? {
    // Do not import TokenStorage over a live SDK session — that can rotate to a stale refresh token.
    if (supabase.auth.currentSessionOrNull() == null) {
        runCatching { SupabaseConfig.importStoredSessionWithoutRefresh(tokenStorage) }
    }
    val refreshResult = authRepository.refreshSession()
    refreshResult.onFailure { err ->
        println("ChatRepository: token refresh failed: ${err.redactedRestMessage()}")
        if (err.isHardAuthFailure()) {
            // Do not cascade restoreSession + second refresh — that amplifies rate limits
            // and can re-import a poisoned refresh token.
            return null
        }
    }
    return supabase.auth.currentSessionOrNull()?.accessToken?.trim()?.takeIf { it.isNotEmpty() }
        ?: tokenStorage.getJwt()?.trim()?.takeIf { jwt ->
            jwt.isNotEmpty() && run {
                val exp = compose.project.click.click.data.auth.EnsureFreshAccessToken.jwtExpEpochMs(jwt)
                exp == null || exp > Clock.System.now().toEpochMilliseconds()
            }
        }
}

internal fun Throwable.isAuthFailure(): Boolean {
    val msg = redactedRestMessage().lowercase()
    return msg.contains("401") ||
        msg.contains("unauthorized") ||
        msg.contains("invalid jwt") ||
        msg.contains("jwt expired") ||
        msg.contains("token has expired") ||
        msg.contains("invalidjwttoken")
}

internal suspend fun SupabaseChatRepository.unwrapGroupMasterKeyFromDb(
    groupId: String,
    viewerUserId: String,
): ByteArray? {
    return try {
        val group = supabase.from("groups")
            .select(columns = Columns.list("id", "name", "created_by", "key_anchor_user_id", "avatar_url")) {
                filter { eq("id", groupId) }
                limit(1)
            }
            .decodeList<SupabaseChatRepository.GroupRow>()
            .firstOrNull() ?: return null

        val memberRow = supabase.from("group_members")
            .select(columns = Columns.list("encrypted_group_key")) {
                filter {
                    eq("group_id", groupId)
                    eq("user_id", viewerUserId)
                }
                limit(1)
            }
            .decodeList<SupabaseChatRepository.GroupMemberKeyRow>()
            .firstOrNull() ?: return null

        val wrapPeer = when {
            viewerUserId == group.createdBy -> group.keyAnchorUserId
            else -> group.createdBy
        } ?: return null

        val connId = findConnectionIdBetween(viewerUserId, wrapPeer) ?: return null
        withContext(Dispatchers.Default) {
            val keys = MessageCrypto.deriveKeysForConnection(
                connId,
                listOf(viewerUserId, wrapPeer).sorted(),
            )
            val plain = MessageCrypto.decryptContent(memberRow.encryptedGroupKey, keys)
            // HMAC failure returns the e2e: wire string unchanged — do not Base64-decode it
            // (that throws "Invalid symbol ':' at index 3").
            MessageCrypto.tryDecodeGroupMasterKeyBase64(plain)
        }
    } catch (e: Exception) {
        println("ChatRepository: unwrap group key failed: ${e.redactedRestMessage()}")
        null
    }
}

internal suspend fun SupabaseChatRepository.resolveChatCrypto(chatId: String, viewerUserId: String?): ChatSessionCaches.ResolvedChatCrypto? {
    ChatSessionCaches.getCrypto(chatId)?.let { return it }
    return try {
        val row = supabase.from("chats")
            .select(columns = Columns.list("id", "connection_id", "group_id")) {
                filter { eq("id", chatId) }
                limit(1)
            }
            .decodeList<SupabaseChatRepository.ChatRoutingRow>()
            .firstOrNull() ?: return null

        when {
            row.groupId != null -> {
                rememberChatGroupRouting(chatId, row.groupId)
                val uid = viewerUserId ?: return null
                val master = unwrapGroupMasterKeyFromDb(row.groupId, uid) ?: return null
                val resolved = ChatSessionCaches.ResolvedChatCrypto.GroupMaster(master)
                ChatSessionCaches.putGroupCrypto(chatId, master)
                resolved
            }
            !row.connectionId.isNullOrBlank() -> {
                rememberChatConnectionRouting(chatId, row.connectionId)
                val userIds = AppDataManager.connections.value
                    .firstOrNull { it.id == row.connectionId }
                    ?.user_ids
                    ?: supabase.from("connections")
                        .select(columns = Columns.list("id", "user_ids")) {
                            filter { eq("id", row.connectionId) }
                            limit(1)
                        }
                        .decodeList<SupabaseChatRepository.ConnectionUserIdsRow>()
                        .firstOrNull()
                        ?.user_ids
                    ?: return null
                val keys = MessageCrypto.deriveKeysForConnection(row.connectionId, userIds)
                val resolved = ChatSessionCaches.ResolvedChatCrypto.Pairwise(keys)
                ChatSessionCaches.putPairwiseCrypto(chatId, keys)
                resolved
            }
            else -> null
        }
    } catch (e: Exception) {
        println("ChatRepository: resolveChatCrypto failed: ${e.redactedRestMessage()}")
        null
    }
}

internal suspend fun SupabaseChatRepository.getEncryptionKeysForConnection(connectionId: String): MessageCrypto.DerivedKeys? {
    if (connectionId.isBlank()) return null
    AppDataManager.connections.value
        .firstOrNull { it.id == connectionId }
        ?.user_ids
        ?.takeIf { it.size >= 2 }
        ?.let { return MessageCrypto.deriveKeysForConnection(connectionId, it) }
    return try {
        val connection = supabase.from("connections")
            .select(columns = Columns.list("id", "user_ids")) {
                filter { eq("id", connectionId) }
                limit(1)
            }
            .decodeList<SupabaseChatRepository.ConnectionUserIdsRow>()
            .firstOrNull() ?: return null
        MessageCrypto.deriveKeysForConnection(connection.id, connection.user_ids)
    } catch (e: Exception) {
        println("ChatRepository: Failed to derive connection keys: ${e.redactedRestMessage()}")
        null
    }
}

internal fun SupabaseChatRepository.decryptMessageOnCurrentThread(message: Message, crypto: ChatSessionCaches.ResolvedChatCrypto?): Message {
    val decrypted =
        if (message.messageType == "call_log" || message.isBeaconChatMessage()) {
            message
        } else if (crypto == null) {
            if (MessageCrypto.isAnyE2eeWireContent(message.content)) {
                message.copy(content = "New message")
            } else {
                message
            }
        } else {
            when (crypto) {
                is ChatSessionCaches.ResolvedChatCrypto.GroupMaster -> {
                    if (!MessageCrypto.isGroupMessageEncrypted(message.content)) {
                        if (MessageCrypto.isEncrypted(message.content)) message.copy(content = "New message")
                        else message
                    } else {
                        message.copy(
                            content = MessageCrypto.decryptGroupMessageContent(message.content, crypto.masterKey),
                        )
                    }
                }
                is ChatSessionCaches.ResolvedChatCrypto.Pairwise -> {
                    if (!MessageCrypto.isEncrypted(message.content)) message
                    else message.copy(content = MessageCrypto.decryptContent(message.content, crypto.keys))
                }
            }
        }
    return decrypted.withCoercedBeaconType().withDbDerivedDeliveryState()
}

internal suspend fun SupabaseChatRepository.decryptMessage(message: Message, crypto: ChatSessionCaches.ResolvedChatCrypto?): Message =
    withContext(Dispatchers.Default) {
        decryptMessageOnCurrentThread(message, crypto)
    }
