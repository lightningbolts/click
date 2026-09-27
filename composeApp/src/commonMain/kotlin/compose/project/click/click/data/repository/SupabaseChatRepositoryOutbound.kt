package compose.project.click.click.data.repository

import compose.project.click.click.crypto.MessageCrypto
import compose.project.click.click.crypto.MessageCryptoV2
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One outbound message encrypted for its chat: E2EE wire [content] plus [metadata] carrying the v2
 * crypto fields (`crypto_version`, `epoch`, `sender_device_id`, `client_message_id`).
 *
 * Shared by every path that writes a message body to click-web (send now; Send Later, plans and
 * forwarding reuse it) so they all encrypt identically — mirrors iOS `ChatRepository.textPost`.
 * Metadata passed in must never contain plaintext derived from E2EE content (e.g. reply excerpts).
 */
internal class OutboundWire(
    /** Legacy (v1 pairwise / group master) keys, also used to decrypt the server's echo. */
    val legacyCrypto: ChatSessionCaches.ResolvedChatCrypto?,
    private val chatId: String,
    private val plaintext: String,
    private val baseMetadata: JsonElement?,
    private val mediaUpload: E2eeV2MediaUploadRecord?,
    private var v2Session: E2eeV2ChatSession?,
    private var clientMessageId: String?,
    private val resolveFreshV2Session: suspend () -> E2eeV2ChatSession?,
) {
    var content: String = encrypt()
        private set
    var metadata: JsonElement? = buildMetadata()
        private set

    /**
     * click-web rejected the write with "E2EE v2 required" (the chat upgraded or rotated epochs):
     * re-resolve the session and re-encrypt, keeping the same client message id.
     */
    suspend fun refreshAfterV2Required() {
        val session = resolveFreshV2Session() ?: throw E2eeV2RequiredException()
        mediaUpload?.requireMatches(chatId, session)
        v2Session = session
        clientMessageId = clientMessageId ?: MessageCryptoV2.generateClientMessageId()
        content = encrypt()
        metadata = buildMetadata()
    }

    private fun encrypt(): String {
        val session = v2Session
        val legacy = legacyCrypto
        return when {
            session != null ->
                MessageCryptoV2.encryptMessage(
                    metadata =
                        MessageCryptoV2.MessageMetadata(
                            chatId = chatId,
                            epoch = session.epoch,
                            senderDeviceId = session.senderDeviceId,
                            clientMessageId = clientMessageId ?: error("v2 client message id is missing"),
                        ),
                    epochKey = session.epochKey,
                    plaintext = plaintext,
                )
            legacy is ChatSessionCaches.ResolvedChatCrypto.GroupMaster ->
                MessageCrypto.encryptGroupMessageContent(plaintext, legacy.masterKey)
            legacy is ChatSessionCaches.ResolvedChatCrypto.Pairwise ->
                MessageCrypto.encryptContent(plaintext, legacy.keys)
            else -> plaintext
        }
    }

    private fun buildMetadata(): JsonElement? =
        v2Session?.let { session ->
            buildJsonObject {
                (baseMetadata as? JsonObject)?.forEach { (key, value) -> put(key, value) }
                put("crypto_version", MessageCryptoV2.CRYPTO_VERSION)
                put("epoch", session.epoch)
                put("sender_device_id", session.senderDeviceId)
                put("client_message_id", clientMessageId ?: error("v2 client message id is missing"))
            }
        } ?: baseMetadata
}

private fun E2eeV2MediaUploadRecord.requireMatches(
    chatId: String,
    session: E2eeV2ChatSession?,
) {
    if (session == null ||
        metadata.chatId != chatId ||
        metadata.epoch != session.epoch ||
        metadata.senderDeviceId != session.senderDeviceId
    ) {
        throw E2eeV2RequiredException()
    }
}

private val MEDIA_REFERENCE_KEYS =
    listOf("media_url", "mediaUrl", "attachment_path", "attachmentPath", "path", "storage_path", "object_path")

/**
 * Resolves the chat's crypto and encrypts [content]. Media sends must reuse the v2 session their
 * attachment was uploaded under; a mismatch throws [E2eeV2RequiredException] so the caller retries.
 */
internal suspend fun SupabaseChatRepository.prepareOutboundWire(
    chatId: String,
    userId: String,
    content: String,
    messageType: String,
    metadata: JsonElement?,
): OutboundWire {
    val legacyCrypto = resolveChatCrypto(chatId, userId)
    val v2Session = resolveE2eeV2ChatCrypto(chatId, userId, allowLifecycle = true)
    val mediaReference =
        (metadata as? JsonObject)?.let { root ->
            MEDIA_REFERENCE_KEYS
                .asSequence()
                .mapNotNull { root[it]?.toString()?.trim('"')?.takeIf(String::isNotBlank) }
                .firstOrNull()
        }
    val mediaUpload = e2eeV2MediaRecordForReference(mediaReference)
    mediaUpload?.requireMatches(chatId, v2Session)
    val clientMessageId =
        v2Session?.let {
            mediaUpload?.metadata?.clientMessageId ?: MessageCryptoV2.generateClientMessageId()
        }
    return OutboundWire(
        legacyCrypto = legacyCrypto,
        chatId = chatId,
        plaintext = content,
        baseMetadata = enrichMediaEncryptionMetadata(messageType, metadata),
        mediaUpload = mediaUpload,
        v2Session = v2Session,
        clientMessageId = clientMessageId,
        resolveFreshV2Session = {
            resolveE2eeV2ChatCrypto(chatId, userId, forceRefresh = true, allowLifecycle = true)
        },
    )
}
