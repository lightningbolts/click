@file:Suppress("ktlint:standard:no-wildcard-imports")

package compose.project.click.click.data.repository

import compose.project.click.click.crypto.MessageCrypto
import compose.project.click.click.crypto.MessageCryptoV2
import compose.project.click.click.crypto.PlatformCrypto
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.auth.EnsureFreshAccessToken // pragma: allowlist secret
import compose.project.click.click.data.models.*
import compose.project.click.click.util.isHardAuthFailure
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class E2eeV2RequiredException : IllegalStateException("E2EE_V2_REQUIRED")

internal data class E2eeV2ChatSession(
    val epoch: Int,
    val epochKeys: Map<Int, ByteArray>,
    val senderDeviceId: String,
    val identity: compose.project.click.click.crypto.DeviceIdentity,
    val membershipFingerprint: String,
    val replayGuard: MessageCryptoV2.ReplayGuard = MessageCryptoV2.ReplayGuard(),
) {
    val epochKey: ByteArray
        get() = epochKeys[epoch] ?: error("Current E2EE v2 key is unavailable")
}

private object E2eeV2SessionCache {
    private val sessions = mutableMapOf<String, E2eeV2ChatSession>()

    fun get(chatId: String): E2eeV2ChatSession? = sessions[chatId]

    fun put(chatId: String, session: E2eeV2ChatSession) {
        sessions[chatId]?.epochKeys?.values?.distinct()?.forEach { it.fill(0) }
        sessions[chatId] = session
    }
}

internal suspend fun SupabaseChatRepository.ensureFreshJwtForChat(): String? =
    EnsureFreshAccessToken.get(
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
    val sdk =
        supabase.auth
            .currentSessionOrNull()
            ?.accessToken
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    if (EnsureFreshAccessToken.isAccessTokenFresh(sdk)) {
        return sdk
    }
    return EnsureFreshAccessToken.get(
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

internal fun Throwable.isE2eeV2Required(): Boolean {
    val msg = redactedRestMessage().lowercase()
    return msg.contains("e2ee_v2_required") ||
        msg.contains("e2ee v2 required") ||
        msg.contains("e2ee-v2-required")
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

/** Resolves the current server epoch and unwraps this device's v2 epoch key. */
internal suspend fun SupabaseChatRepository.resolveE2eeV2ChatCrypto(
    chatId: String,
    viewerUserId: String,
    forceRefresh: Boolean = false,
    allowLifecycle: Boolean = false,
): E2eeV2ChatSession? {
    if (!forceRefresh && !allowLifecycle) E2eeV2SessionCache.get(chatId)?.let { return it }
    val token = ensureFreshJwtForChat() ?: return null
    val identity = try {
        MessageCryptoV2.loadOrCreateDeviceIdentity()
    } catch (_: Exception) {
        throw E2eeV2RequiredException()
    }
    apiClient.registerE2eeV2Device(identity.info.deviceId, identity.info.publicKeySpkiBase64, token)
    val devices =
        apiClient.discoverE2eeV2Devices(chatId, token).getOrElse { throw E2eeV2RequiredException() }
    val registered = devices.firstOrNull { it.deviceId == identity.info.deviceId }
        ?: throw E2eeV2RequiredException()
    val logicalDeviceId = identity.info.deviceId
    var state =
        apiClient.getE2eeV2State(chatId, token, logicalDeviceId).getOrElse {
            throw E2eeV2RequiredException()
        }
    check(state.chatId == chatId && state.deviceId == logicalDeviceId) {
        "E2EE v2 epoch response identity mismatch"
    }

    val allParticipantsHaveV2Devices =
        if (allowLifecycle) {
            val participantIds = participantUserIdsForE2eeUpgrade(chatId)
            participantIds != null &&
                participantIds.isNotEmpty() &&
                participantIds.all { participantId -> devices.any { it.userId == participantId } }
        } else {
            true
        }
    if (allowLifecycle && state.currentEpoch == null) {
        if (!allParticipantsHaveV2Devices) return null
        state =
            createE2eeV2EpochWithFreshKey(
                chatId = chatId,
                identity = identity,
                devices = devices,
                epoch = 1,
                membershipFingerprint = membershipFingerprintForDevices(devices),
                authToken = token,
            )
    } else if (allowLifecycle && state.currentEpoch != null) {
        if (!allParticipantsHaveV2Devices) throw E2eeV2RequiredException()
        val fingerprint = membershipFingerprintForDevices(devices)
        if (state.membershipFingerprint != fingerprint) {
            state =
                createE2eeV2EpochWithFreshKey(
                    chatId = chatId,
                    identity = identity,
                    devices = devices,
                    epoch = state.currentEpoch + 1,
                    membershipFingerprint = fingerprint,
                    authToken = token,
                )
        }
    }

    val currentEpoch = state.currentEpoch ?: return null
    if (currentEpoch <= 0) throw E2eeV2RequiredException()
    // The epoch route returns recipient_device_id as the persisted row UUID. The request/query
    // contract and the envelope AAD use the logical identity.deviceId instead.
    val epochKeys = state.envelopes
        .filter { it.recipientDeviceId == registered.id }
        .mapNotNull { envelope ->
            val key = runCatching {
                MessageCryptoV2.unwrapEpochKey(
                    metadata = MessageCryptoV2.EpochKeyWrapMetadata(
                        chatId = chatId,
                        epoch = envelope.epoch,
                        senderDeviceId = envelope.senderDeviceId,
                        recipientDeviceId = logicalDeviceId,
                    ),
                    recipientIdentity = identity,
                    envelope = envelope.envelope,
                )
            }.getOrNull() ?: return@mapNotNull null
            envelope.epoch to key
        }.toMap()
    if (!epochKeys.containsKey(currentEpoch)) throw E2eeV2RequiredException()
    return E2eeV2ChatSession(
        epoch = currentEpoch,
        epochKeys = epochKeys,
        senderDeviceId = logicalDeviceId,
        identity = identity,
        membershipFingerprint = state.membershipFingerprint ?: membershipFingerprintForDevices(devices),
    ).also { E2eeV2SessionCache.put(chatId, it) }
}

private suspend fun SupabaseChatRepository.participantUserIdsForE2eeUpgrade(chatId: String): Set<String>? =
    try {
        val routing =
            supabase
                .from("chats")
                .select(columns = Columns.list("connection_id", "group_id")) {
                    filter { eq("id", chatId) }
                    limit(1)
                }.decodeList<SupabaseChatRepository.ChatRoutingRow>()
                .firstOrNull() ?: return null
        when {
            routing.groupId != null ->
                supabase
                    .from("group_members")
                    .select(columns = Columns.list("user_id")) {
                        filter { eq("group_id", routing.groupId) }
                    }.decodeList<SupabaseChatRepository.GroupMemberUidRow>()
                    .map { it.userId.trim() }
                    .filter(String::isNotBlank)
                    .toSet()
            !routing.connectionId.isNullOrBlank() ->
                supabase
                    .from("connections")
                    .select(columns = Columns.list("id", "user_ids")) {
                        filter { eq("id", routing.connectionId) }
                        limit(1)
                    }.decodeList<SupabaseChatRepository.ConnectionUserIdsRow>()
                    .firstOrNull()
                    ?.user_ids
                    ?.map(String::trim)
                    ?.filter(String::isNotBlank)
                    ?.toSet()
            else -> null
        }
    } catch (error: Exception) {
        println("ChatRepository: participant lookup for E2EE v2 lifecycle failed: ${error.redactedRestMessage()}")
        null
    }

private fun membershipFingerprintForDevices(devices: List<compose.project.click.click.data.api.ClickWebChatDeviceDto>): String {
    val canonical = devices
        .map { "${it.userId.orEmpty()}:${it.deviceId}" }
        .sorted()
        .joinToString("|")
    return PlatformCrypto.sha256(canonical.encodeToByteArray()).toHexString()
}

private fun ByteArray.toHexString(): String = buildString(size * 2) {
    for (value in this@toHexString) {
        val byte = value.toInt() and 0xff
        append("0123456789abcdef"[byte ushr 4])
        append("0123456789abcdef"[byte and 0x0f])
    }
}

private suspend fun SupabaseChatRepository.createE2eeV2EpochWithFreshKey(
    chatId: String,
    identity: compose.project.click.click.crypto.DeviceIdentity,
    devices: List<compose.project.click.click.data.api.ClickWebChatDeviceDto>,
    epoch: Int,
    membershipFingerprint: String,
    authToken: String,
): compose.project.click.click.data.api.ClickWebChatE2eeV2StateEnvelope {
    val epochKey = MessageCryptoV2.generateEpochKey()
    val envelopes =
        devices.map { recipient ->
            val envelope =
                MessageCryptoV2.wrapEpochKey(
                    metadata = MessageCryptoV2.EpochKeyWrapMetadata(
                        chatId = chatId,
                        epoch = epoch,
                        senderDeviceId = identity.info.deviceId,
                        recipientDeviceId = recipient.deviceId,
                    ),
                    epochKey = epochKey,
                    recipientPublicKeySpkiBase64 = recipient.identityPublicKey,
                )
            compose.project.click.click.data.api.ClickWebChatEpochWriteEnvelope(
                recipientDeviceId = recipient.deviceId,
                envelope = envelope,
            )
        }
    val write =
        apiClient.createE2eeV2Epoch(
            chatId = chatId,
            epoch = epoch,
            senderDeviceId = identity.info.deviceId,
            membershipFingerprint = membershipFingerprint,
            envelopes = envelopes,
            authToken = authToken,
        )
    if (write.isFailure) {
        val concurrent = apiClient.getE2eeV2State(chatId, authToken, identity.info.deviceId).getOrNull()
        if (concurrent?.currentEpoch == epoch && concurrent.membershipFingerprint == membershipFingerprint) {
            return concurrent
        }
        throw write.exceptionOrNull() ?: E2eeV2RequiredException()
    }
    return apiClient.getE2eeV2State(chatId, authToken, identity.info.deviceId).getOrElse {
        throw E2eeV2RequiredException()
    }
}

internal fun SupabaseChatRepository.peekE2eeV2ChatCrypto(chatId: String): E2eeV2ChatSession? = E2eeV2SessionCache.get(chatId)

internal fun shouldBypassLegacyMessageDecrypt(message: Message): Boolean =
    (message.messageType == "call_log" || message.isBeaconChatMessage()) &&
        !MessageCrypto.isV2Encrypted(message.content)

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
    v2Crypto: E2eeV2ChatSession? = null,
): Message {
    val effectiveV2Crypto =
        v2Crypto ?: if (MessageCrypto.isV2Encrypted(message.content)) {
            runCatching { MessageCryptoV2.parseE2eeV2Envelope(message.content) }
                .getOrNull()
                ?.let { (it as? MessageCryptoV2.MessageEnvelope)?.let { envelope -> peekE2eeV2ChatCrypto(envelope.chatId) } }
        } else {
            null
        }
    val decrypted =
        if (shouldBypassLegacyMessageDecrypt(message)) {
            message
        } else if (crypto == null) {
            if (MessageCrypto.isAnyE2eeWireContent(message.content)) {
                if (MessageCrypto.isV2Encrypted(message.content) && effectiveV2Crypto != null) {
                    decryptV2Message(message, effectiveV2Crypto)
                } else {
                    message.copy(content = "New message")
                }
            } else {
                message
            }
        } else {
            when (crypto) {
                is ChatSessionCaches.ResolvedChatCrypto.GroupMaster -> {
                    if (MessageCrypto.isV2Encrypted(message.content)) {
                        if (effectiveV2Crypto != null) decryptV2Message(message, effectiveV2Crypto) else message.copy(content = "New message")
                    } else if (!MessageCrypto.isGroupMessageEncrypted(message.content)) {
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
                    if (MessageCrypto.isV2Encrypted(message.content)) {
                        if (effectiveV2Crypto != null) decryptV2Message(message, effectiveV2Crypto) else message.copy(content = "New message")
                    } else if (!MessageCrypto.isEncrypted(message.content)) {
                        message
                    } else {
                        message.copy(content = MessageCrypto.decryptContent(message.content, crypto.keys))
                    }
                }
            }
        }
    return decrypted.withCoercedBeaconType().withDbDerivedDeliveryState()
}

private fun decryptV2Message(message: Message, session: E2eeV2ChatSession): Message =
    runCatching {
        val envelope = MessageCryptoV2.parseE2eeV2Envelope(message.content) as? MessageCryptoV2.MessageEnvelope
            ?: error("not a message envelope")
        val metadata = MessageCryptoV2.MessageMetadata(
            chatId = envelope.chatId,
            epoch = envelope.epoch,
            senderDeviceId = envelope.senderDeviceId,
            clientMessageId = envelope.clientMessageId,
        )
        val epochKey = session.epochKeys[envelope.epoch] ?: error("E2EE v2 epoch key is unavailable")
        message.copy(content = MessageCryptoV2.decryptMessage(metadata, epochKey, message.content, session.replayGuard))
    }.getOrElse { message.copy(content = "New message") }

internal suspend fun SupabaseChatRepository.decryptMessage(
    message: Message,
    crypto: ChatSessionCaches.ResolvedChatCrypto?,
): Message =
    withContext(Dispatchers.Default) {
        val v2 =
            if (MessageCrypto.isV2Encrypted(message.content)) {
                val envelope = runCatching { MessageCryptoV2.parseE2eeV2Envelope(message.content) }
                    .getOrNull() as? MessageCryptoV2.MessageEnvelope
                resolveE2eeV2ChatCrypto(
                    chatId = envelope?.chatId ?: return@withContext message.copy(content = "New message"),
                    viewerUserId = supabase.auth.currentUserOrNull()?.id ?: "",
                )
            } else {
                null
            }
        decryptMessageOnCurrentThread(message, crypto, v2)
    }
