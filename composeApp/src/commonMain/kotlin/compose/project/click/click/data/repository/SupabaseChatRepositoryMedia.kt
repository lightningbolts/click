@file:Suppress("ktlint:standard:no-wildcard-imports")

package compose.project.click.click.data.repository

import compose.project.click.click.chat.attachments.AttachmentCrypto
import compose.project.click.click.chat.attachments.ChatAttachmentValidator
import compose.project.click.click.crypto.MessageCrypto
import compose.project.click.click.crypto.MessageCryptoV2
import compose.project.click.click.data.api.E2eeV2MediaUploadRequest
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal data class E2eeV2MediaUploadRecord(
    val metadata: MessageCryptoV2.MediaMetadata,
    val authorizationEnvelope: String,
    val storagePath: String? = null,
)

/** Bridges the existing URL/path-only media API to the following sendMessage call. */
private object E2eeV2MediaUploadCache {
    private const val MAX_ENTRIES = 256
    private val records = linkedMapOf<String, E2eeV2MediaUploadRecord>()

    fun put(reference: String, record: E2eeV2MediaUploadRecord) {
        val key = reference.trim()
        if (key.isEmpty()) return
        records[key] = record
        while (records.size > MAX_ENTRIES) records.remove(records.keys.first())
    }

    fun get(reference: String?): E2eeV2MediaUploadRecord? = reference?.trim()?.let(records::get)
}

internal fun SupabaseChatRepository.e2eeV2MediaRecordForReference(reference: String?): E2eeV2MediaUploadRecord? =
    E2eeV2MediaUploadCache.get(reference)

private fun JsonObject.stringField(vararg names: String): String? =
    names.asSequence()
        .mapNotNull { name -> this[name]?.jsonPrimitive?.contentOrNull }
        .firstOrNull { it.isNotBlank() }

private fun JsonObject.intField(vararg names: String): Int? =
    names.asSequence()
        .mapNotNull { name -> this[name]?.jsonPrimitive?.intOrNull }
        .firstOrNull()

internal fun Message.e2eeV2MediaMetadataOrNull(chatIdFallback: String? = null): MessageCryptoV2.MediaMetadata? {
    val root = metadata as? JsonObject ?: return null
    val digest = root.stringField("media_ciphertext_sha256", "mediaCiphertextSha256") ?: return null
    val epoch = root.intField("media_epoch", "mediaEpoch", "epoch") ?: return null
    val senderDeviceId = root.stringField("media_sender_device_id", "mediaSenderDeviceId", "sender_device_id", "senderDeviceId") ?: return null
    val clientMessageId = root.stringField("media_client_message_id", "mediaClientMessageId", "client_message_id", "clientMessageId") ?: return null
    return MessageCryptoV2.MediaMetadata(
        chatId = root.stringField("media_chat_id", "mediaChatId") ?: chatIdFallback ?: return null,
        epoch = epoch,
        senderDeviceId = senderDeviceId,
        clientMessageId = clientMessageId,
        mediaCiphertextSha256 = digest,
    )
}

internal fun Message.e2eeV2MediaStoragePathOrNull(): String? {
    val root = metadata as? JsonObject ?: return null
    return root.stringField("media_path", "mediaPath")
}

private fun E2eeV2MediaUploadRecord.toRequest(): E2eeV2MediaUploadRequest =
    E2eeV2MediaUploadRequest(
        envelope = authorizationEnvelope,
        mediaCiphertextSha256 = metadata.mediaCiphertextSha256,
        epoch = metadata.epoch,
        senderDeviceId = metadata.senderDeviceId,
        clientMessageId = metadata.clientMessageId,
    )

internal fun SupabaseChatRepository.enrichMediaEncryptionMetadata(
    messageType: String,
    metadata: JsonElement?,
): JsonElement? {
    val mt = messageType.lowercase()
    val root = metadata as? JsonObject
    val reference =
        root?.stringField(
            "media_url",
            "mediaUrl",
            "attachment_path",
            "attachmentPath",
            "path",
            "storage_path",
            "object_path",
        )
    val v2 = e2eeV2MediaRecordForReference(reference)
    if (mt != ChatMessageType.IMAGE && mt != ChatMessageType.AUDIO && mt != ChatMessageType.FILE && v2 == null) return metadata
    if (root == null && mt == ChatMessageType.FILE && v2 == null) return metadata
    return buildJsonObject {
        root?.forEach { (key, value) -> put(key, value) }
        if (mt == ChatMessageType.IMAGE || mt == ChatMessageType.AUDIO) {
            put("is_encrypted_media", JsonPrimitive(true))
        }
        v2?.let { record ->
            put("crypto_version", MessageCryptoV2.CRYPTO_VERSION)
            put("media_chat_id", record.metadata.chatId)
            put("media_epoch", record.metadata.epoch)
            put("media_sender_device_id", record.metadata.senderDeviceId)
            put("media_client_message_id", record.metadata.clientMessageId)
            put("media_ciphertext_sha256", record.metadata.mediaCiphertextSha256)
            put("media_authorization_envelope", record.authorizationEnvelope)
            record.storagePath?.takeIf { it.isNotBlank() }?.let { put("media_path", it) }
        }
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
                sha256Base64 = e2eeV2MediaRecordForReference(publicUrl)?.metadata?.mediaCiphertextSha256
                    ?: AttachmentCrypto.sha256Base64(plainBytes),
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
        val v2Crypto = resolveE2eeV2ChatCrypto(chatId, senderUserId, allowLifecycle = true)
        val mediaClientMessageId = v2Crypto?.let { MessageCryptoV2.generateClientMessageId() }
        val v2 =
            v2Crypto?.let { session ->
                MessageCryptoV2.encryptMedia(
                    metadata = MessageCryptoV2.MediaMetadata(
                        chatId = chatId,
                        epoch = session.epoch,
                        senderDeviceId = session.senderDeviceId,
                        clientMessageId = mediaClientMessageId!!,
                        mediaCiphertextSha256 = "",
                    ),
                    epochKey = session.epochKey,
                    plaintext = plainBytes,
                    replayGuard = session.replayGuard,
                )
            }
        val fileMasterKey = v2?.let { null } ?: AttachmentCrypto.generateFileMasterKey()
        val cipher = v2?.uploadedBytes ?: AttachmentCrypto.encryptFileBytes(plainBytes, fileMasterKey!!)
        val sha = v2?.mediaCiphertextSha256 ?: AttachmentCrypto.sha256Base64(plainBytes)
        val v2Record =
            v2?.let { encrypted ->
                E2eeV2MediaUploadRecord(
                    metadata = MessageCryptoV2.MediaMetadata(
                        chatId = chatId,
                        epoch = v2Crypto!!.epoch,
                        senderDeviceId = v2Crypto.senderDeviceId,
                        clientMessageId = mediaClientMessageId!!,
                        mediaCiphertextSha256 = encrypted.mediaCiphertextSha256,
                    ),
                    authorizationEnvelope = encrypted.authorizationEnvelope,
                )
            }
        val jwt = ensureFreshJwtForChat() ?: return null
        val uploaded =
            apiClient
                .uploadAttachment(
                    fileBytes = cipher,
                    chatId = chatId,
                    mimeType = mimeType.ifBlank { "application/octet-stream" },
                    fileName = fileName,
                    authToken = jwt,
                    v2 = v2Record?.toRequest(),
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
                            v2 = v2Record?.toRequest(),
                        ).getOrThrow()
                }.getOrElse { err ->
                    println("ChatRepository: attachment upload failed: ${err.redactedRestMessage()}")
                    return null
                }
        ChatRepository.EncryptedAttachmentUpload(
            path = uploaded.path,
            fileMasterKeyBase64 = fileMasterKey?.let(AttachmentCrypto::encodeFileMasterKeyBase64).orEmpty(),
            sha256Base64 = sha,
            sizeBytes = plainBytes.size.toLong(),
            mimeType = mimeType.ifBlank { "application/octet-stream" },
            fileName = fileName,
        ).also {
            v2Record?.let { record ->
                val withPath = record.copy(storagePath = uploaded.path)
                E2eeV2MediaUploadCache.put(uploaded.path, withPath)
                uploaded.initialSignedUrl?.let { url -> E2eeV2MediaUploadCache.put(url, withPath) }
            }
        }
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
        ChatMessageType.FILE -> vaultFileAttachmentMessage(chatId, viewerUserId, message)
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
    val v2Metadata = message.e2eeV2MediaMetadataOrNull(chatId)
    val v2StoragePath = message.e2eeV2MediaStoragePathOrNull()
    val remoteUrl = message.mediaUrlOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    if (remoteUrl == null && (v2Metadata == null || v2StoragePath.isNullOrBlank())) return message
    val extension =
        when (type) {
            ChatMessageType.AUDIO -> message.audioCacheFileExtension()
            else -> imageVaultFileExtension(message.originalMimeTypeOrNull(), remoteUrl.orEmpty())
        }
    if (readChatMediaVaultBytes(message.id, extension)?.isNotEmpty() == true) {
        val localUri = chatMediaVaultLocalUri(message.id, extension) ?: return message
        return message.withLocalMediaUri(localUri)
    }
    val plain =
        downloadAndDecryptChatMediaImpl(
            chatId = chatId,
            viewerUserId = viewerUserId,
            mediaUrl = remoteUrl.orEmpty(),
            v2Metadata = v2Metadata,
            v2StoragePath = v2StoragePath,
        )
            ?.takeIf { it.isNotEmpty() }
            ?: return message
    val localUri = writeChatMediaVaultFile(message.id, plain, extension) ?: return message
    return message.withLocalMediaUri(localUri)
}

internal suspend fun SupabaseChatRepository.vaultFileAttachmentMessage(
    chatId: String,
    viewerUserId: String,
    message: Message,
): Message {
    val envelope = AttachmentCrypto.resolveEnvelope(message.content, message.metadata) ?: return message
    val extension = envelope.vaultCacheExtension()
    if (readChatMediaVaultBytes(message.id, extension)?.isNotEmpty() == true) {
        return message
    }
    val plain =
        message.e2eeV2MediaMetadataOrNull(chatId)?.let { mediaMetadata ->
            downloadAndDecryptV2Attachment(
                chatId = chatId,
                viewerUserId = viewerUserId,
                path = message.e2eeV2MediaStoragePathOrNull() ?: envelope.path,
                metadata = mediaMetadata,
            )
        } ?: downloadAttachmentPlaintext(
            path = envelope.path,
            fileMasterKeyBase64 = envelope.key,
            expectedSha256Base64 = envelope.sha256,
        )
    val nonEmptyPlain = plain?.takeIf { it.isNotEmpty() } ?: return message
    writeChatMediaVaultFile(message.id, nonEmptyPlain, extension)
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
    v2Metadata: MessageCryptoV2.MediaMetadata? = null,
    v2StoragePath: String? = null,
): ByteArray? {
    return try {
        ensureFreshJwtForChat()
        if (v2Metadata != null) {
            val session = resolveE2eeV2ChatCrypto(chatId, viewerUserId) ?: return null
            check(v2Metadata.chatId == chatId) { "E2EE v2 media chat mismatch" }
            val epochKey = session.epochKeys[v2Metadata.epoch]
                ?: return null
            val downloadUrl =
                v2StoragePath?.takeIf { it.isNotBlank() }?.let { path ->
                    val jwt = ensureFreshJwtForChat() ?: return null
                    apiClient.signAttachmentUrl(path, jwt).recoverCatching { firstErr ->
                        val retry = refreshedJwtAfterAuthFailure() ?: throw firstErr
                        apiClient.signAttachmentUrl(path, retry).getOrThrow()
                    }.getOrElse { err ->
                        println("ChatRepository: v2 media sign failed: ${err.redactedRestMessage()}")
                        return null
                    }
                } ?: mediaUrl
            val raw = downloadMediaBytesWithAuthRetry(downloadUrl) ?: return null
            return withContext(Dispatchers.Default) {
                MessageCryptoV2.decryptMedia(v2Metadata, epochKey, raw)
            }
        }
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

private suspend fun SupabaseChatRepository.downloadMediaBytesWithAuthRetry(mediaUrl: String): ByteArray? {
    return apiClient.downloadUrlBytes(mediaUrl).getOrElse { err ->
        println("ChatRepository: media download failed: ${err.redactedRestMessage()}")
        refreshedJwtAfterAuthFailure()
        apiClient.downloadUrlBytes(mediaUrl).getOrElse { retryErr ->
            println("ChatRepository: media download retry failed: ${retryErr.redactedRestMessage()}")
            return null
        }
    }
}

internal suspend fun SupabaseChatRepository.downloadAndDecryptV2Attachment(
    chatId: String,
    viewerUserId: String,
    path: String,
    metadata: MessageCryptoV2.MediaMetadata,
): ByteArray? {
    return try {
        val session = resolveE2eeV2ChatCrypto(chatId, viewerUserId) ?: return null
        check(metadata.chatId == chatId) { "E2EE v2 attachment chat mismatch" }
        val epochKey = session.epochKeys[metadata.epoch] ?: return null
        val jwt = ensureFreshJwtForChat() ?: return null
        val signedUrl =
            apiClient.signAttachmentUrl(path, jwt).recoverCatching { firstErr ->
                val retry = refreshedJwtAfterAuthFailure() ?: throw firstErr
                apiClient.signAttachmentUrl(path, retry).getOrThrow()
            }.getOrElse { err ->
                println("ChatRepository: v2 attachment sign failed: ${err.redactedRestMessage()}")
                return null
            }
        val bytes = apiClient.downloadAttachmentBytes(signedUrl).getOrElse { err ->
            println("ChatRepository: v2 attachment download failed: ${err.redactedRestMessage()}")
            return null
        }
        withContext(Dispatchers.Default) {
            MessageCryptoV2.decryptMedia(metadata, epochKey, bytes)
        }
    } catch (e: Exception) {
        println("ChatRepository: v2 attachment decrypt failed: ${e.redactedRestMessage()}")
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
        val v2Crypto = resolveE2eeV2ChatCrypto(chatId, senderUserId, allowLifecycle = true)
        val mediaClientMessageId = v2Crypto?.let { MessageCryptoV2.generateClientMessageId() }
        val v2 =
            v2Crypto?.let { session ->
                MessageCryptoV2.encryptMedia(
                    metadata = MessageCryptoV2.MediaMetadata(
                        chatId = chatId,
                        epoch = session.epoch,
                        senderDeviceId = session.senderDeviceId,
                        clientMessageId = mediaClientMessageId!!,
                        mediaCiphertextSha256 = "",
                    ),
                    epochKey = session.epochKey,
                    plaintext = plainBytes,
                    replayGuard = session.replayGuard,
                )
            }
        val crypto = if (v2 == null) resolveChatCrypto(chatId, senderUserId) else null
        val cipher =
            v2?.uploadedBytes ?: when (crypto) {
                is ChatSessionCaches.ResolvedChatCrypto.GroupMaster -> MessageCrypto.encryptMediaBytes(plainBytes, crypto.masterKey)
                is ChatSessionCaches.ResolvedChatCrypto.Pairwise -> MessageCrypto.encryptMediaBytes(plainBytes, crypto.keys)
                null -> return null
            }
        val v2Record =
            v2?.let { encrypted ->
                E2eeV2MediaUploadRecord(
                    metadata = MessageCryptoV2.MediaMetadata(
                        chatId = chatId,
                        epoch = v2Crypto!!.epoch,
                        senderDeviceId = v2Crypto.senderDeviceId,
                        clientMessageId = mediaClientMessageId!!,
                        mediaCiphertextSha256 = encrypted.mediaCiphertextSha256,
                    ),
                    authorizationEnvelope = encrypted.authorizationEnvelope,
                )
            }
        val jwt = ensureFreshJwtForChat() ?: return null
        val uploaded = apiClient
            .uploadMediaWithPath(
                fileBytes = cipher,
                chatId = chatId,
                mimeType = uploadMime,
                authToken = jwt,
                v2 = v2Record?.toRequest(),
            ).getOrElse { firstErr ->
                val retriedJwt = refreshedJwtAfterAuthFailure() ?: return null
                apiClient
                    .uploadMediaWithPath(
                        fileBytes = cipher,
                        chatId = chatId,
                        mimeType = uploadMime,
                        authToken = retriedJwt,
                        v2 = v2Record?.toRequest(),
                    ).getOrElse {
                        println("ChatRepository: uploadChatMedia failed: ${firstErr.redactedRestMessage()}")
                        return null
                    }
            }
        if (v2Record != null && uploaded.path.isNullOrBlank()) {
            println("ChatRepository: v2 media upload response missing storage path")
            return null
        }
        val uploadedUrl = uploaded.url
        v2Record?.let { record ->
            val withPath = record.copy(storagePath = uploaded.path)
            E2eeV2MediaUploadCache.put(uploadedUrl, withPath)
            uploaded.path?.let { path -> E2eeV2MediaUploadCache.put(path, withPath) }
        }
        uploadedUrl
    } catch (e: Exception) {
        println("ChatRepository: uploadChatMedia failed: ${e.redactedRestMessage()}")
        null
    }
}
