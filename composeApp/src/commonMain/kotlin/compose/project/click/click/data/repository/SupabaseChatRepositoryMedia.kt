@file:Suppress("ktlint:standard:no-wildcard-imports")

package compose.project.click.click.data.repository

import compose.project.click.click.chat.attachments.AttachmentCrypto
import compose.project.click.click.chat.attachments.ChatAttachmentValidator
import compose.project.click.click.crypto.MessageCrypto
import compose.project.click.click.data.models.*
import compose.project.click.click.util.chatMediaVaultLocalPath
import compose.project.click.click.util.compressOutgoingChatImageForUpload
import compose.project.click.click.util.imageVaultFileExtension
import compose.project.click.click.util.readChatMediaVaultBytes
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import compose.project.click.click.util.vaultCacheExtension
import compose.project.click.click.util.writeChatMediaVaultFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun SupabaseChatRepository.enrichMediaEncryptionMetadata(
    messageType: String,
    metadata: JsonElement?,
): JsonElement? {
    val mt = messageType.lowercase()
    if (mt != ChatMessageType.IMAGE && mt != ChatMessageType.AUDIO) return metadata
    return when (metadata) {
        is JsonObject ->
            buildJsonObject {
                for ((k, v) in metadata.entries) put(k, v)
                put("is_encrypted_media", JsonPrimitive(true))
            }
        null -> buildJsonObject { put("is_encrypted_media", JsonPrimitive(true)) }
        else -> metadata
    }
}

internal suspend fun SupabaseChatRepository.uploadEncryptedMediaBlob(
    chatId: String,
    senderUserId: String,
    plainBytes: ByteArray,
    mimeType: String,
    fileName: String,
): ChatRepository.EncryptedAttachmentUpload? =
    try {
        // Route through the existing `/api/chat/media` path so wire compatibility with legacy
        // image/audio upload sites stays bit-for-bit identical.
        val objectPath = "$senderUserId/$chatId/$fileName"
        val publicUrl = uploadChatMedia(plainBytes, objectPath, mimeType)
        if (publicUrl.isNullOrBlank()) {
            null
        } else {
            ChatRepository.EncryptedAttachmentUpload(
                path = publicUrl,
                fileMasterKeyBase64 = "",
                sha256Base64 = AttachmentCrypto.sha256Base64(plainBytes),
                sizeBytes = plainBytes.size.toLong(),
                mimeType = mimeType.ifBlank { "application/octet-stream" },
                fileName = fileName,
            )
        }
    } catch (e: Exception) {
        println("ChatRepository: uploadEncryptedBlob(media) failed: ${e.redactedRestMessage()}")
        null
    }

internal suspend fun SupabaseChatRepository.uploadEncryptedAttachmentBlob(
    chatId: String,
    senderUserId: String,
    plainBytes: ByteArray,
    mimeType: String,
    fileName: String,
): ChatRepository.EncryptedAttachmentUpload? {
    // Client-side gate — server enforces identical rules via the Storage bucket policy, but
    // failing fast here avoids a round-trip and surfaces a friendly error.
    val validation =
        ChatAttachmentValidator.validate(
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = plainBytes.size.toLong(),
        )
    if (validation is ChatAttachmentValidator.Result.Invalid) {
        println("ChatRepository: attachment validation failed reason=${validation.reason}")
        return null
    }

    return try {
        val fileMasterKey = AttachmentCrypto.generateFileMasterKey()
        val cipher = AttachmentCrypto.encryptFileBytes(plainBytes, fileMasterKey)
        val sha = AttachmentCrypto.sha256Base64(plainBytes)
        val jwt = ensureFreshJwtForChat() ?: return null
        val uploaded =
            apiClient
                .uploadAttachment(
                    fileBytes = cipher,
                    chatId = chatId,
                    mimeType = mimeType.ifBlank { "application/octet-stream" },
                    fileName = fileName,
                    authToken = jwt,
                ).recoverCatching { firstErr ->
                    val retriedJwt =
                        refreshedJwtAfterAuthFailure()
                            ?: throw firstErr
                    apiClient
                        .uploadAttachment(
                            fileBytes = cipher,
                            chatId = chatId,
                            mimeType = mimeType.ifBlank { "application/octet-stream" },
                            fileName = fileName,
                            authToken = retriedJwt,
                        ).getOrThrow()
                }.getOrElse { err ->
                    println("ChatRepository: attachment upload failed: ${err.redactedRestMessage()}")
                    return null
                }
        ChatRepository.EncryptedAttachmentUpload(
            path = uploaded.path,
            fileMasterKeyBase64 = AttachmentCrypto.encodeFileMasterKeyBase64(fileMasterKey),
            sha256Base64 = sha,
            sizeBytes = plainBytes.size.toLong(),
            mimeType = mimeType.ifBlank { "application/octet-stream" },
            fileName = fileName,
        )
    } catch (e: Exception) {
        println("ChatRepository: uploadEncryptedBlob(attachments) failed: ${e.redactedRestMessage()}")
        null
    }
}

internal suspend fun SupabaseChatRepository.vaultEncryptedMediaMessage(
    chatId: String,
    viewerUserId: String,
    message: Message,
): Message {
    val type = message.messageType.lowercase()
    return when (type) {
        ChatMessageType.FILE -> vaultFileAttachmentMessage(chatId, message)
        ChatMessageType.IMAGE, ChatMessageType.AUDIO ->
            vaultRemoteEncryptedMediaMessage(
                chatId = chatId,
                viewerUserId = viewerUserId,
                message = message,
                type = type,
            )
        else -> message
    }
}

internal suspend fun SupabaseChatRepository.vaultRemoteEncryptedMediaMessage(
    chatId: String,
    viewerUserId: String,
    message: Message,
    type: String,
): Message {
    if (!message.isEncryptedMedia() || message.hasLocalMediaUri()) return message
    val remoteUrl = message.mediaUrlOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return message
    val extension =
        when (type) {
            ChatMessageType.AUDIO -> message.audioCacheFileExtension()
            else -> imageVaultFileExtension(message.originalMimeTypeOrNull(), remoteUrl)
        }
    if (readChatMediaVaultBytes(message.id, extension)?.isNotEmpty() == true) {
        val localUri = chatMediaVaultLocalUri(message.id, extension) ?: return message
        return message.withLocalMediaUri(localUri)
    }
    val plain =
        downloadAndDecryptChatMedia(chatId, viewerUserId, remoteUrl)
            ?.takeIf { it.isNotEmpty() }
            ?: return message
    val localUri = writeChatMediaVaultFile(message.id, plain, extension) ?: return message
    return message.withLocalMediaUri(localUri)
}

internal suspend fun SupabaseChatRepository.vaultFileAttachmentMessage(
    chatId: String,
    message: Message,
): Message {
    val envelope = AttachmentCrypto.resolveEnvelope(message.content, message.metadata) ?: return message
    val extension = envelope.vaultCacheExtension()
    if (readChatMediaVaultBytes(message.id, extension)?.isNotEmpty() == true) {
        return message
    }
    val plain =
        downloadAttachmentPlaintext(
            path = envelope.path,
            fileMasterKeyBase64 = envelope.key,
            expectedSha256Base64 = envelope.sha256,
        )?.takeIf { it.isNotEmpty() } ?: return message
    writeChatMediaVaultFile(message.id, plain, extension)
    return message
}

internal fun SupabaseChatRepository.chatMediaVaultLocalUri(
    messageId: String,
    extension: String,
): String? =
    chatMediaVaultLocalPath(messageId, extension)?.let {
        "file://$it"
    }

internal suspend fun SupabaseChatRepository.downloadAndDecryptChatMediaImpl(
    chatId: String,
    viewerUserId: String,
    mediaUrl: String,
): ByteArray? {
    return try {
        ensureFreshJwtForChat()
        val crypto = resolveChatCrypto(chatId, viewerUserId) ?: return null
        val raw =
            apiClient.downloadUrlBytes(mediaUrl).getOrElse { err ->
                println("ChatRepository: media download failed: ${err.redactedRestMessage()}")
                // One auth-refresh retry — stale JWT after offline→online often looks like a download miss.
                refreshedJwtAfterAuthFailure()
                apiClient.downloadUrlBytes(mediaUrl).getOrElse { retryErr ->
                    println("ChatRepository: media download retry failed: ${retryErr.redactedRestMessage()}")
                    return null
                }
            }
        val normalized = normalizeEncryptedMediaPayload(raw)
        if (normalized !== raw) {
            println("ChatRepository: decoded base64-wrapped encrypted media payload for chat=$chatId")
        }
        runCatching {
            withContext(Dispatchers.Default) {
                when (crypto) {
                    is ChatSessionCaches.ResolvedChatCrypto.GroupMaster -> MessageCrypto.decryptMediaBytes(normalized, crypto.masterKey)
                    is ChatSessionCaches.ResolvedChatCrypto.Pairwise -> MessageCrypto.decryptMediaBytes(normalized, crypto.keys)
                }
            }
        }.onFailure { err ->
            println("ChatRepository: media decrypt failed for chat=$chatId: ${err.redactedRestMessage()}")
        }.getOrNull()
    } catch (e: Exception) {
        println("ChatRepository: downloadAndDecryptChatMedia failed: ${e.redactedRestMessage()}")
        null
    }
}

internal suspend fun SupabaseChatRepository.uploadChatMediaImpl(
    bytes: ByteArray,
    objectPath: String,
    contentType: String,
): String? {
    if (bytes.isEmpty()) return null
    return try {
        val parts = objectPath.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        val chatId = parts.getOrNull(1) ?: return null
        val senderUserId = parts.getOrNull(0) ?: return null
        val crypto = resolveChatCrypto(chatId, senderUserId) ?: return null
        val ct = contentType.ifBlank { "application/octet-stream" }
        val (plainBytes, uploadMime) =
            if (ct.lowercase().startsWith("image/")) {
                val compressed = compressOutgoingChatImageForUpload(bytes, ct)
                if (compressed.size >= 2 &&
                    compressed[0] == 0xFF.toByte() &&
                    compressed[1] == 0xD8.toByte()
                ) {
                    compressed to "image/jpeg"
                } else {
                    compressed to ct
                }
            } else {
                bytes to ct
            }
        val cipher =
            when (crypto) {
                is ChatSessionCaches.ResolvedChatCrypto.GroupMaster -> MessageCrypto.encryptMediaBytes(plainBytes, crypto.masterKey)
                is ChatSessionCaches.ResolvedChatCrypto.Pairwise -> MessageCrypto.encryptMediaBytes(plainBytes, crypto.keys)
            }
        val jwt = ensureFreshJwtForChat() ?: return null
        apiClient
            .uploadMedia(
                fileBytes = cipher,
                chatId = chatId,
                mimeType = uploadMime,
                authToken = jwt,
            ).getOrElse { firstErr ->
                val retriedJwt = refreshedJwtAfterAuthFailure() ?: return null
                apiClient
                    .uploadMedia(
                        fileBytes = cipher,
                        chatId = chatId,
                        mimeType = uploadMime,
                        authToken = retriedJwt,
                    ).getOrElse {
                        println("ChatRepository: uploadChatMedia failed: ${firstErr.redactedRestMessage()}")
                        return null
                    }
            }
    } catch (e: Exception) {
        println("ChatRepository: uploadChatMedia failed: ${e.redactedRestMessage()}")
        null
    }
}
