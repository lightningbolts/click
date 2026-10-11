package compose.project.click.click.data.repository

import compose.project.click.click.crypto.MessageCrypto
import compose.project.click.click.crypto.MessageCryptoV2
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.isGatedDrop
import compose.project.click.click.util.compressOutgoingChatImageForUpload
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/*
 * Gated Click Drops (spec §2, sheet 07 G0-5 / G2-1). The message's media is only the pixelated
 * preview; the E2EE original goes through `/api/chat/media` with `drop_original: true` and is
 * named once in `metadata.drop_original_path`. The server swaps that for `drop_gated: true` and
 * signs the original only through `/api/drops/develop` after reveal.
 */

/** Where a gated drop's original went, and (v2 chats) how to decrypt it. */
data class ClickDropOriginalUpload(
    val path: String,
    /** Null in v1 chats: the original uses the chat's legacy media keys. */
    val v2: MessageCryptoV2.MediaMetadata?,
)

/** `metadata.drop_original` = `{ epoch, sender_device_id, client_message_id, media_ciphertext_sha256 }`. */
internal fun Message.dropOriginalV2MetadataOrNull(chatId: String): MessageCryptoV2.MediaMetadata? = dropOriginalV2MetadataOrNull(metadata, chatId)

internal fun dropOriginalV2MetadataOrNull(
    metadata: JsonElement?,
    chatId: String,
): MessageCryptoV2.MediaMetadata? {
    val fields = (metadata as? JsonObject)?.get("drop_original") as? JsonObject ?: return null
    fun str(key: String) = (fields[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    return MessageCryptoV2.MediaMetadata(
        chatId = chatId,
        epoch = (fields["epoch"] as? JsonPrimitive)?.intOrNull ?: return null,
        senderDeviceId = str("sender_device_id") ?: return null,
        clientMessageId = str("client_message_id") ?: return null,
        mediaCiphertextSha256 = str("media_ciphertext_sha256") ?: return null,
    )
}

/**
 * The fields a gated drop adds to its message metadata (iOS `ChatRepository.sendMedia(gated)`):
 * `drop_original_path`, plus `drop_original` in v2 chats. Legacy keys stay as they are.
 */
fun gatedClickDropMetadata(
    base: JsonObject,
    upload: ClickDropOriginalUpload,
): JsonObject =
    buildJsonObject {
        base.forEach { (key, value) -> put(key, value) }
        put("drop_original_path", upload.path)
        upload.v2?.let { v2 ->
            put(
                "drop_original",
                buildJsonObject {
                    put("epoch", v2.epoch)
                    put("sender_device_id", v2.senderDeviceId)
                    put("client_message_id", v2.clientMessageId)
                    put("media_ciphertext_sha256", v2.mediaCiphertextSha256)
                },
            )
        }
    }

/**
 * Encrypts and uploads a gated drop's original. In v2 chats it uses its own client message ID
 * `"<previewClientMessageId>.original"`, so its AAD and replay identity differ from the preview's.
 */
internal suspend fun SupabaseChatRepository.uploadClickDropOriginalImpl(
    plainBytes: ByteArray,
    chatId: String,
    senderUserId: String,
    mimeType: String,
    previewClientMessageId: String?,
): ClickDropOriginalUpload? {
    if (plainBytes.isEmpty()) return null
    return try {
        val plain = compressOutgoingChatImageForUpload(plainBytes, mimeType)
        val uploadMime = if (plain.size >= 2 && plain[0] == 0xFF.toByte() && plain[1] == 0xD8.toByte()) "image/jpeg" else mimeType
        val v2Crypto = resolveE2eeV2ChatCrypto(chatId, senderUserId, allowLifecycle = true)
        val originalId = "${previewClientMessageId ?: MessageCryptoV2.generateClientMessageId()}.original"
        val v2 =
            v2Crypto?.let { session ->
                MessageCryptoV2.encryptMedia(
                    metadata =
                        MessageCryptoV2.MediaMetadata(
                            chatId = chatId,
                            epoch = session.epoch,
                            senderDeviceId = session.senderDeviceId,
                            clientMessageId = originalId,
                            mediaCiphertextSha256 = "",
                        ),
                    epochKey = session.epochKey,
                    plaintext = plain,
                    replayGuard = session.replayGuard,
                )
            }
        val cipher =
            v2?.uploadedBytes ?: when (val crypto = resolveChatCrypto(chatId, senderUserId)) {
                is ChatSessionCaches.ResolvedChatCrypto.GroupMaster -> MessageCrypto.encryptMediaBytes(plain, crypto.masterKey)
                is ChatSessionCaches.ResolvedChatCrypto.Pairwise -> MessageCrypto.encryptMediaBytes(plain, crypto.keys)
                null -> return null
            }
        val v2Metadata =
            v2?.let {
                MessageCryptoV2.MediaMetadata(
                    chatId = chatId,
                    epoch = v2Crypto!!.epoch,
                    senderDeviceId = v2Crypto.senderDeviceId,
                    clientMessageId = originalId,
                    mediaCiphertextSha256 = it.mediaCiphertextSha256,
                )
            }
        val request =
            v2?.let {
                compose.project.click.click.data.api.E2eeV2MediaUploadRequest(
                    envelope = it.authorizationEnvelope,
                    mediaCiphertextSha256 = it.mediaCiphertextSha256,
                    epoch = v2Metadata!!.epoch,
                    senderDeviceId = v2Metadata.senderDeviceId,
                    clientMessageId = originalId,
                )
            }
        val jwt = ensureFreshJwtForChat() ?: return null
        val uploaded =
            apiClient
                .uploadMediaWithPath(cipher, chatId, uploadMime, jwt, request, dropOriginal = true)
                .getOrElse {
                    val retry = refreshedJwtAfterAuthFailure() ?: return null
                    apiClient.uploadMediaWithPath(cipher, chatId, uploadMime, retry, request, dropOriginal = true).getOrElse { err ->
                        println("ChatRepository: drop original upload failed: ${err.redactedRestMessage()}")
                        return null
                    }
                }
        ClickDropOriginalUpload(path = uploaded.path ?: return null, v2 = v2Metadata)
    } catch (e: Exception) {
        println("ChatRepository: drop original upload failed: ${e.redactedRestMessage()}")
        null
    }
}

/**
 * Downloads a developed gated drop's original from the develop-issued [signedUrl] and decrypts it
 * with `metadata.drop_original` (v2) or the chat's legacy media keys (v1). Never returns the preview.
 */
internal suspend fun SupabaseChatRepository.loadClickDropOriginalImpl(
    chatId: String,
    viewerUserId: String,
    message: Message,
    signedUrl: String,
): ByteArray? {
    if (!message.isGatedDrop() || signedUrl.isBlank()) return null
    return try {
        val raw =
            apiClient.downloadUrlBytes(signedUrl).getOrElse { err ->
                println("ChatRepository: drop original download failed: ${err.redactedRestMessage()}")
                return null
            }
        val v2 = message.dropOriginalV2MetadataOrNull(chatId)
        if (v2 != null) {
            val session = resolveE2eeV2ChatCrypto(chatId, viewerUserId) ?: return null
            val epochKey = session.epochKeys[v2.epoch] ?: return null
            return withContext(Dispatchers.Default) { MessageCryptoV2.decryptMedia(v2, epochKey, raw) }
        }
        val crypto = resolveChatCrypto(chatId, viewerUserId) ?: return null
        val normalized = normalizeEncryptedMediaPayload(raw)
        withContext(Dispatchers.Default) {
            when (crypto) {
                is ChatSessionCaches.ResolvedChatCrypto.GroupMaster -> MessageCrypto.decryptMediaBytes(normalized, crypto.masterKey)
                is ChatSessionCaches.ResolvedChatCrypto.Pairwise -> MessageCrypto.decryptMediaBytes(normalized, crypto.keys)
            }
        }
    } catch (e: Exception) {
        println("ChatRepository: drop original decrypt failed: ${e.redactedRestMessage()}")
        null
    }
}
