@file:Suppress("ktlint:standard:no-wildcard-imports")

package compose.project.click.click.data.repository

import compose.project.click.click.crypto.MessageCrypto
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.models.*
import compose.project.click.click.util.isHardAuthFailure
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

internal suspend fun SupabaseChatRepository.ensureFreshJwtForChat(): String? =
    compose.project.click.click.data.auth.EnsureFreshAccessToken.get( // pragma: allowlist secret
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
    val sdk = supabase.auth.currentSessionOrNull()?.accessToken?.trim()?.takeIf { it.isNotEmpty() }
    if (compose.project.click.click.data.auth.EnsureFreshAccessToken.isAccessTokenFresh(sdk)) { // pragma: allowlist secret
        return sdk
    }
    return compose.project.click.click.data.auth.EnsureFreshAccessToken.get( // pragma: allowlist secret
        tokenStorage = tokenStorage,
        authRepository = authRepository,
        forceRefresh = false,
    )
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
        val group =
            supabase
                .from("groups")
                .select(columns = Columns.list("id", "name", "created_by", "key_anchor_user_id", "avatar_url")) {
                    filter { eq("id", groupId) }
                    limit(1)
                }.decodeList<SupabaseChatRepository.GroupRow>()
                .firstOrNull() ?: return null

        val memberRow =
            supabase
                .from("group_members")
                .select(columns = Columns.list("encrypted_group_key")) {
                    filter {
                        eq("group_id", groupId)
                        eq("user_id", viewerUserId)
                    }
                    limit(1)
                }.decodeList<SupabaseChatRepository.GroupMemberKeyRow>()
                .firstOrNull() ?: return null

        val wrapPeer =
            when {
                viewerUserId == group.createdBy -> group.keyAnchorUserId
                else -> group.createdBy
            } ?: return null

        val connId = findConnectionIdBetween(viewerUserId, wrapPeer) ?: return null
        withContext(Dispatchers.Default) {
            val keys =
                MessageCrypto.deriveKeysForConnection(
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

internal suspend fun SupabaseChatRepository.resolveChatCrypto(
    chatId: String,
    viewerUserId: String?,
): ChatSessionCaches.ResolvedChatCrypto? {
    ChatSessionCaches.getCrypto(chatId)?.let { return it }
    return try {
        val row =
            supabase
                .from("chats")
                .select(columns = Columns.list("id", "connection_id", "group_id")) {
                    filter { eq("id", chatId) }
                    limit(1)
                }.decodeList<SupabaseChatRepository.ChatRoutingRow>()
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
                val userIds =
                    AppDataManager.connections.value
                        .firstOrNull { it.id == row.connectionId }
                        ?.user_ids
                        ?: supabase
                            .from("connections")
                            .select(columns = Columns.list("id", "user_ids")) {
                                filter { eq("id", row.connectionId) }
                                limit(1)
                            }.decodeList<SupabaseChatRepository.ConnectionUserIdsRow>()
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
        val connection =
            supabase
                .from("connections")
                .select(columns = Columns.list("id", "user_ids")) {
                    filter { eq("id", connectionId) }
                    limit(1)
                }.decodeList<SupabaseChatRepository.ConnectionUserIdsRow>()
                .firstOrNull() ?: return null
        MessageCrypto.deriveKeysForConnection(connection.id, connection.user_ids)
    } catch (e: Exception) {
        println("ChatRepository: Failed to derive connection keys: ${e.redactedRestMessage()}")
        null
    }
}

internal fun SupabaseChatRepository.decryptMessageOnCurrentThread(
    message: Message,
    crypto: ChatSessionCaches.ResolvedChatCrypto?,
): Message {
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
                        if (MessageCrypto.isEncrypted(message.content)) {
                            message.copy(content = "New message")
                        } else {
                            message
                        }
                    } else {
                        message.copy(
                            content = MessageCrypto.decryptGroupMessageContent(message.content, crypto.masterKey),
                        )
                    }
                }
                is ChatSessionCaches.ResolvedChatCrypto.Pairwise -> {
                    if (!MessageCrypto.isEncrypted(message.content)) {
                        message
                    } else {
                        message.copy(content = MessageCrypto.decryptContent(message.content, crypto.keys))
                    }
                }
            }
        }
    return decrypted.withCoercedBeaconType().withDbDerivedDeliveryState()
}

internal suspend fun SupabaseChatRepository.decryptMessage(
    message: Message,
    crypto: ChatSessionCaches.ResolvedChatCrypto?,
): Message =
    withContext(Dispatchers.Default) {
        decryptMessageOnCurrentThread(message, crypto)
    }
