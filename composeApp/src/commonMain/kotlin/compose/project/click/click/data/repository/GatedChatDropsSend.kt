package compose.project.click.click.data.repository

import compose.project.click.click.crypto.MessageCrypto
import compose.project.click.click.crypto.MessageCryptoV2
import compose.project.click.click.data.api.E2eeV2MediaUploadRequest
import compose.project.click.click.util.compressOutgoingChatImageForUpload
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * Gated Click Drops, send side (flag `drops_develop`; click-web `lib/server/drops/chatDrops.ts`,
 * `app/api/chat/media/route.ts`; iOS `ChatRepository.sendMedia` with `draft.gatesOriginal`). The
 * message's media is only the pixelated preview; the E2EE original goes through `/api/chat/media`
 * with `drop_original: true` and is named once in `metadata.drop_original_path`. The server swaps
 * that for `drop_gated: true` and signs the original only through `/api/drops/develop` after reveal.
 */

/** Where a gated drop's original went, and (v2 chats) how to decrypt it. */
data class ClickDropOriginalUpload(
    val path: String,
    /** Null in v1 chats: the original uses the chat's legacy media keys. */
    val v2: MessageCryptoV2.MediaMetadata?,
)

/** The original's v2 client message ID: its own, so its AAD and replay identity differ from the preview's. */
internal fun clickDropOriginalClientMessageId(previewClientMessageId: String): String = "$previewClientMessageId.original"

/**
 * The fields a gated drop adds to its message metadata: `drop_original_path`, plus `drop_original`
 * (`{epoch, sender_device_id, client_message_id, media_ciphertext_sha256}`) in v2 chats.
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
 * Encrypts and uploads a gated drop's original the same way its preview went up: in v2 chats under
 * the preview's epoch with client message ID `"<preview id>.original"`, otherwise with the chat's
 * legacy media keys.
 */
internal suspend fun SupabaseChatRepository.uploadClickDropOriginalImpl(
    plainBytes: ByteArray,
    chatId: String,
    senderUserId: String,
    mimeType: String,
    previewMediaReference: String,
): ClickDropOriginalUpload? {
    if (plainBytes.isEmpty()) return null
    return try {
        val plain = compressOutgoingChatImageForUpload(plainBytes, mimeType)
        val isJpeg = plain.size >= 2 && plain[0] == 0xFF.toByte() && plain[1] == 0xD8.toByte()
        val uploadMime = if (isJpeg) "image/jpeg" else mimeType
        val preview = e2eeV2MediaRecordForReference(previewMediaReference)
        val v2Metadata: MessageCryptoV2.MediaMetadata?
        val request: E2eeV2MediaUploadRequest?
        val cipher: ByteArray
        if (preview != null) {
            val session = resolveE2eeV2ChatCrypto(chatId, senderUserId) ?: return null
            val epoch = preview.metadata.epoch
            val epochKey = session.epochKeys[epoch] ?: return null
            val originalId = clickDropOriginalClientMessageId(preview.metadata.clientMessageId)
            val encrypted =
                MessageCryptoV2.encryptMedia(
                    metadata =
                        MessageCryptoV2.MediaMetadata(
                            chatId = chatId,
                            epoch = epoch,
                            senderDeviceId = preview.metadata.senderDeviceId,
                            clientMessageId = originalId,
                            mediaCiphertextSha256 = "",
                        ),
                    epochKey = epochKey,
                    plaintext = plain,
                    replayGuard = session.replayGuard,
                )
            cipher = encrypted.uploadedBytes
            v2Metadata =
                MessageCryptoV2.MediaMetadata(
                    chatId = chatId,
                    epoch = epoch,
                    senderDeviceId = preview.metadata.senderDeviceId,
                    clientMessageId = originalId,
                    mediaCiphertextSha256 = encrypted.mediaCiphertextSha256,
                )
            request =
                E2eeV2MediaUploadRequest(
                    envelope = encrypted.authorizationEnvelope,
                    mediaCiphertextSha256 = encrypted.mediaCiphertextSha256,
                    epoch = epoch,
                    senderDeviceId = preview.metadata.senderDeviceId,
                    clientMessageId = originalId,
                )
        } else {
            cipher =
                when (val crypto = resolveChatCrypto(chatId, senderUserId)) {
                    is ChatSessionCaches.ResolvedChatCrypto.GroupMaster -> MessageCrypto.encryptMediaBytes(plain, crypto.masterKey)
                    is ChatSessionCaches.ResolvedChatCrypto.Pairwise -> MessageCrypto.encryptMediaBytes(plain, crypto.keys)
                    null -> return null
                }
            v2Metadata = null
            request = null
        }
        val jwt = ensureFreshJwtForChat() ?: return null
        val uploaded =
            apiClient
                .uploadMediaWithPath(cipher, chatId, uploadMime, jwt, request, dropOriginal = true)
                .getOrElse { first ->
                    val retry = refreshedJwtAfterAuthFailure()
                    if (retry == null) {
                        println("ChatRepository: drop original upload failed: ${first.redactedRestMessage()}")
                        return null
                    }
                    apiClient.uploadMediaWithPath(cipher, chatId, uploadMime, retry, request, dropOriginal = true).getOrElse { err ->
                        println("ChatRepository: drop original upload retry failed: ${err.redactedRestMessage()}")
                        return null
                    }
                }
        ClickDropOriginalUpload(path = uploaded.path ?: return null, v2 = v2Metadata)
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        println("ChatRepository: drop original upload failed: ${e.redactedRestMessage()}")
        null
    }
}
