@file:Suppress(
    "ktlint:standard:backing-property-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.chat.attachments.AttachmentCrypto // pragma: allowlist secret
import compose.project.click.click.chat.attachments.ChatAttachmentValidator // pragma: allowlist secret
import compose.project.click.click.collaboration.computeClickDropRevealTtlIso
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.CHAT_ATTACHMENTS_BUCKET // pragma: allowlist secret
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.MessageDeliveryState // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.replySnippetForMessage // pragma: allowlist secret
import compose.project.click.click.data.repository.ChatRepository // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAttachmentDownloadOutcome // pragma: allowlist secret
import compose.project.click.click.ui.chat.saveDecryptedAttachmentToDownloads // pragma: allowlist secret
import compose.project.click.click.util.readChatMediaVaultBytesForMessage // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import compose.project.click.click.util.vaultCacheExtension // pragma: allowlist secret
import compose.project.click.click.util.writeChatMediaVaultFile // pragma: allowlist secret
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * Encrypts and uploads a Click Drop frame tagged with the active [encounterId].
 * Reveal TTL is always 24 hours after send (not the session's fixed collaboration TTL).
 */
internal fun ChatViewModel.sendDisposableRollPhotoImpl(
    bytes: ByteArray,
    encounterId: String,
    collaborationTtlIso: String,
    mimeType: String = "image/jpeg",
) {
    if (bytes.isEmpty() || encounterId.isBlank()) return
    val connectionId = currentConnectionId ?: return
    val userId = _currentUserId.value ?: return
    val revealTtlIso = computeClickDropRevealTtlIso()
    _messageSendError.value = null
    viewModelScope.launch {
        outboundChatMessageMutex.withLock {
            _isMessageSubmitInProgress.value = true
            try {
                val apiChatId =
                    resolveOrCreateApiChatId(connectionId) ?: run {
                        _messageSendError.value = "Failed to send — unable to start chat"
                        return@withLock
                    }
                val tempId = "temp-roll-${Clock.System.now().toEpochMilliseconds()}"
                val localMs = Clock.System.now().toEpochMilliseconds()
                val optimistic =
                    Message(
                        id = tempId,
                        user_id = userId,
                        content = " ",
                        timeCreated = localMs,
                        messageType = ChatMessageType.IMAGE,
                        metadata =
                            buildJsonObject {
                                put("is_encrypted_media", true)
                                put("original_mime_type", mimeType)
                                put("disposable_roll", true)
                                put("encounter_id", encounterId)
                                put("collaboration_ttl", revealTtlIso)
                            },
                        localSentAt = localMs,
                        deliveryState = MessageDeliveryState.PENDING,
                    )
                val currentUser =
                    resolveMessageUser(userId, apiChatId)
                        ?: AppDataManager.currentUser.value?.takeIf { it.id == userId }
                        ?: User(id = userId, name = "You", createdAt = 0L)
                appendOutgoingOptimistic(optimistic, currentUser)
                secureImageBytesCache.put(tempId, bytes)
                _secureChatMediaLoadState.update {
                    it + (
                        tempId to
                            SecureChatMediaLoadState(
                                loading = false,
                                imageBytes = bytes,
                                uploadProgress = 0.5f,
                            )
                    )
                }
                val ext = extensionForChatMedia(mimeType, isImage = true)
                val unique = "${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(1_000_000_000)}"
                val path = "$userId/$apiChatId/$unique.$ext"
                val uploadBytes = bytes
                val url =
                    chatRepository.uploadChatMedia(uploadBytes, path, mimeType) ?: run {
                        markOptimisticSendFailed(tempId)
                        secureImageBytesCache.remove(tempId)
                        _secureChatMediaLoadState.update { m -> m - tempId }
                        _messageSendError.value = "Failed to upload Click Drop photo"
                        return@withLock
                    }
                _secureChatMediaLoadState.update { m ->
                    val cur = m[tempId]
                    val heldBytes = cur?.imageBytes ?: uploadBytes
                    m + (
                        tempId to
                            SecureChatMediaLoadState(
                                loading = false,
                                imageBytes = heldBytes,
                                uploadProgress = 1f,
                            )
                    )
                }
                val meta =
                    buildJsonObject {
                        put("media_url", url)
                        put("original_mime_type", mimeType)
                        put("is_encrypted_media", true)
                        put("disposable_roll", true)
                        put("encounter_id", encounterId)
                        put("collaboration_ttl", revealTtlIso)
                    }
                val message =
                    chatRepository.sendMessage(
                        chatId = apiChatId,
                        userId = userId,
                        content = " ",
                        messageType = ChatMessageType.IMAGE,
                        metadata = meta,
                        clientLocalSentAtMs = localMs,
                    )
                if (message != null) {
                    applyInsertedMessage(message, currentUser, userId, optimisticTempId = tempId)
                    activateConnectionIfPending(connectionId)
                } else {
                    markOptimisticSendFailed(tempId)
                    _messageSendError.value = "Failed to send Click Drop photo"
                }
            } catch (e: Exception) {
                _messageSendError.value = "Failed to send Click Drop — ${e.redactedRestMessage().ifBlank { "error" }}"
            } finally {
                _isMessageSubmitInProgress.value = false
            }
        }
    }
}

internal fun ChatViewModel.sendChatAudioImpl(
    bytes: ByteArray,
    mimeType: String,
    durationSeconds: Int?,
) {
    if (bytes.isEmpty()) return
    val connectionId = currentConnectionId ?: return
    val userId = _currentUserId.value ?: return
    val caption = _messageInput.value.trim()
    _messageSendError.value = null
    viewModelScope.launch {
        outboundChatMessageMutex.withLock {
            _isMessageSubmitInProgress.value = true
            var tempId: String? = null
            try {
                val apiChatId =
                    resolveOrCreateApiChatId(connectionId) ?: run {
                        _messageSendError.value = "Failed to send — unable to start chat"
                        return@withLock
                    }
                val localMs = Clock.System.now().toEpochMilliseconds()
                tempId = "temp-audio-$localMs-${Random.nextLong()}"
                val replyTarget = _replyingTo.value
                val optimisticMeta =
                    buildJsonObject {
                        put("original_mime_type", mimeType)
                        put("is_encrypted_media", true)
                        if (durationSeconds != null) put("duration_seconds", durationSeconds)
                        if (replyTarget != null) {
                            put("reply_to_id", replyTarget.message.id)
                            put("reply_to_content", replySnippetForMessage(replyTarget.message))
                        }
                    }
                val currentUser =
                    resolveMessageUser(userId, apiChatId)
                        ?: AppDataManager.currentUser.value?.takeIf { it.id == userId }
                        ?: User(id = userId, name = "You", createdAt = 0L)
                appendOutgoingOptimistic(
                    Message(
                        id = tempId!!,
                        user_id = userId,
                        content = if (caption.isEmpty()) " " else caption,
                        timeCreated = localMs,
                        messageType = ChatMessageType.AUDIO,
                        metadata = optimisticMeta,
                        localSentAt = localMs,
                        deliveryState = MessageDeliveryState.PENDING,
                    ),
                    currentUser,
                )
                val ext = extensionForChatMedia(mimeType, isImage = false)
                val unique = "${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(1_000_000_000)}"
                val path = "$userId/$apiChatId/$unique.$ext"
                val url =
                    chatRepository.uploadChatMedia(bytes, path, mimeType) ?: run {
                        markOptimisticSendFailed(tempId!!)
                        _messageSendError.value = "Failed to upload audio"
                        return@withLock
                    }
                val meta =
                    if (replyTarget != null) {
                        buildJsonObject {
                            put("media_url", url)
                            put("original_mime_type", mimeType)
                            put("is_encrypted_media", true)
                            if (durationSeconds != null) put("duration_seconds", durationSeconds)
                            put("reply_to_id", replyTarget.message.id)
                            put("reply_to_content", replySnippetForMessage(replyTarget.message))
                        }
                    } else {
                        buildJsonObject {
                            put("media_url", url)
                            put("original_mime_type", mimeType)
                            put("is_encrypted_media", true)
                            if (durationSeconds != null) put("duration_seconds", durationSeconds)
                        }
                    }
                val message =
                    chatRepository.sendMessage(
                        chatId = apiChatId,
                        userId = userId,
                        content = if (caption.isEmpty()) " " else caption,
                        messageType = ChatMessageType.AUDIO,
                        metadata = meta,
                        clientLocalSentAtMs = localMs,
                    )
                if (message != null) {
                    _messageInput.value = ""
                    updateMessageInput("")
                    _replyingTo.value = null
                    applyInsertedMessage(message, currentUser, userId, optimisticTempId = tempId)
                    activateConnectionIfPending(connectionId)
                } else {
                    markOptimisticSendFailed(tempId!!)
                    _messageSendError.value = "Failed to send voice message"
                }
            } catch (e: Exception) {
                tempId?.let { markOptimisticSendFailed(it) }
                _messageSendError.value = "Failed to send audio — ${e.redactedRestMessage().ifBlank { "error" }}"
            } finally {
                _isMessageSubmitInProgress.value = false
            }
        }
    }
}

/**
 * Send an encrypted arbitrary attachment (C4). Generates a fresh per-file master key inside
 * [ChatRepository.uploadEncryptedBlob], uploads the ciphertext to the `chat-attachments`
 * bucket, then sends a `message_type = file` message whose body is the `ccx:v1:` envelope —
 * so the per-file key travels entirely inside the existing E2EE wire format.
 */
internal fun ChatViewModel.sendChatFileImpl(
    bytes: ByteArray,
    mimeType: String,
    fileName: String,
) {
    if (bytes.isEmpty()) return
    val connectionId = currentConnectionId ?: return
    val userId = _currentUserId.value ?: return
    val trimmedName = fileName.trim().ifEmpty { "attachment" }

    val validation =
        ChatAttachmentValidator.validate(
            fileName = trimmedName,
            mimeType = mimeType,
            sizeBytes = bytes.size.toLong(),
        )
    if (validation is ChatAttachmentValidator.Result.Invalid) {
        _messageSendError.value = validation.message
        return
    }

    _messageSendError.value = null
    viewModelScope.launch {
        outboundChatMessageMutex.withLock {
            _isMessageSubmitInProgress.value = true
            var tempId: String? = null
            try {
                val apiChatId =
                    resolveOrCreateApiChatId(connectionId) ?: run {
                        _messageSendError.value = "Failed to send — unable to start chat"
                        return@withLock
                    }
                val localMs = Clock.System.now().toEpochMilliseconds()
                tempId = "temp-file-$localMs-${Random.nextLong()}"
                val replyTarget = _replyingTo.value
                val currentUser =
                    resolveMessageUser(userId, apiChatId)
                        ?: AppDataManager.currentUser.value?.takeIf { it.id == userId }
                        ?: User(id = userId, name = "You", createdAt = 0L)
                appendOutgoingOptimistic(
                    Message(
                        id = tempId!!,
                        user_id = userId,
                        content = trimmedName,
                        timeCreated = localMs,
                        messageType = ChatMessageType.FILE,
                        metadata =
                            buildJsonObject {
                                put("attachment_name", trimmedName)
                                put("attachment_mime", mimeType)
                                put("attachment_size", bytes.size.toLong())
                                if (replyTarget != null) {
                                    put("reply_to_id", replyTarget.message.id)
                                    put("reply_to_content", replySnippetForMessage(replyTarget.message))
                                }
                            },
                        localSentAt = localMs,
                        deliveryState = MessageDeliveryState.PENDING,
                    ),
                    currentUser,
                )
                val uploaded =
                    chatRepository.uploadEncryptedBlob(
                        bucketName = CHAT_ATTACHMENTS_BUCKET,
                        chatId = apiChatId,
                        senderUserId = userId,
                        plainBytes = bytes,
                        mimeType = mimeType,
                        fileName = trimmedName,
                    ) ?: run {
                        markOptimisticSendFailed(tempId!!)
                        _messageSendError.value = "Failed to upload attachment"
                        return@withLock
                    }
                val envelope =
                    AttachmentCrypto.Envelope(
                        v = 1,
                        type = "file",
                        name = uploaded.fileName,
                        mime = uploaded.mimeType,
                        size = uploaded.sizeBytes,
                        path = uploaded.path,
                        key = uploaded.fileMasterKeyBase64,
                        sha256 = uploaded.sha256Base64,
                    )
                val envelopeBody = AttachmentCrypto.encodeEnvelope(envelope)

                val meta =
                    buildJsonObject {
                        put("attachment_path", uploaded.path)
                        put("attachment_name", uploaded.fileName)
                        put("attachment_mime", uploaded.mimeType)
                        put("attachment_size", uploaded.sizeBytes)
                        if (replyTarget != null) {
                            put("reply_to_id", replyTarget.message.id)
                            put("reply_to_content", replySnippetForMessage(replyTarget.message))
                        }
                    }

                val message =
                    chatRepository.sendMessage(
                        chatId = apiChatId,
                        userId = userId,
                        content = envelopeBody,
                        messageType = ChatMessageType.FILE,
                        metadata = meta,
                        clientLocalSentAtMs = localMs,
                    )
                if (message != null) {
                    _replyingTo.value = null
                    applyInsertedMessage(message, currentUser, userId, optimisticTempId = tempId)
                    activateConnectionIfPending(connectionId)
                } else {
                    markOptimisticSendFailed(tempId!!)
                    _messageSendError.value = "Failed to send attachment"
                }
            } catch (e: Exception) {
                tempId?.let { markOptimisticSendFailed(it) }
                _messageSendError.value = "Failed to send attachment — ${e.redactedRestMessage().ifBlank { "error" }}"
                println("Error sending attachment: ${e.redactedRestMessage()}")
            } finally {
                _isMessageSubmitInProgress.value = false
            }
        }
    }
}

/**
 * Download + decrypt a chat attachment (Phase 2 — C6). Mints a fresh signed URL, pulls the
 * ciphertext, decrypts with the per-file master key from the envelope, re-verifies SHA-256
 * on the plaintext, then writes the bytes to the platform Downloads surface. All failures
 * are surfaced as a user-visible [ChatAttachmentDownloadOutcome.Failure].
 */
internal suspend fun ChatViewModel.downloadChatAttachmentImpl(
    messageId: String,
    envelope: AttachmentCrypto.Envelope,
): ChatAttachmentDownloadOutcome {
    if (envelope.path.isBlank() || envelope.key.isBlank() || envelope.sha256.isBlank()) {
        return ChatAttachmentDownloadOutcome.Failure("Attachment envelope is invalid.")
    }
    val extension = envelope.vaultCacheExtension()
    val vaultedBytes =
        messageId
            .takeIf { it.isNotBlank() }
            ?.let { readChatMediaVaultBytesForMessage(it, preferredExtension = extension) }
            ?.takeIf { it.isNotEmpty() }
    val plaintext =
        vaultedBytes ?: chatRepository.downloadAttachmentPlaintext(
            path = envelope.path,
            fileMasterKeyBase64 = envelope.key,
            expectedSha256Base64 = envelope.sha256,
        ) ?: return ChatAttachmentDownloadOutcome.Failure(
            "Download failed — integrity check did not pass.",
        )
    if (vaultedBytes == null && messageId.isNotBlank()) {
        writeChatMediaVaultFile(messageId, plaintext, extension)
    }
    val savedPath =
        saveDecryptedAttachmentToDownloads(
            bytes = plaintext,
            fileName = envelope.name.ifBlank { "attachment" },
            mimeType = envelope.mime.ifBlank { "application/octet-stream" },
        ) ?: return ChatAttachmentDownloadOutcome.Failure(
            "Couldn't write the file to Downloads.",
        )
    return ChatAttachmentDownloadOutcome.Success(savedPath)
}
