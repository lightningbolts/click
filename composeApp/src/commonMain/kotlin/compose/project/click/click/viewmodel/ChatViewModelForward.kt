package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.chat.attachments.AttachmentCrypto // pragma: allowlist secret
import compose.project.click.click.data.CHAT_ATTACHMENTS_BUCKET // pragma: allowlist secret
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.data.models.FORWARD_MAX_TARGETS // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.canForward // pragma: allowlist secret
import compose.project.click.click.data.models.forwardableCaption // pragma: allowlist secret
import compose.project.click.click.data.models.originalMimeTypeOrNull // pragma: allowlist secret
import compose.project.click.click.data.repository.e2eeV2MediaMetadataOrNull // pragma: allowlist secret
import compose.project.click.click.data.repository.e2eeV2MediaStoragePathOrNull // pragma: allowlist secret
import compose.project.click.click.util.isPersistedApiChatId // pragma: allowlist secret
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/*
 * Forwarding (iOS `ConversationModel.forward`): each target gets a fresh message encrypted for that
 * chat (text through the normal send path; photos, voice notes and files re-uploaded from the
 * decrypted local copy), marked `metadata.forwarded` so every reader sees "Forwarded". No
 * server-side copy of plaintext.
 */

/** Resolves (or creates) the persisted chat id for a forward target. */
private suspend fun ChatViewModel.forwardTargetChatId(target: ChatWithDetails): String? {
    target.chat.id
        ?.takeIf { isPersistedApiChatId(it) }
        ?.let { return it }
    val group = target.groupClique
    val ensured =
        if (group != null) {
            chatRepository.ensureChatForGroup(group.groupId)
        } else {
            chatRepository.ensureChatForConnection(target.connection.id)
        }
    return ensured?.id?.takeIf { isPersistedApiChatId(it) }
}

internal fun ChatViewModel.forwardMessageImpl(
    message: Message,
    targets: List<ChatWithDetails>,
) {
    val userId = _currentUserId.value ?: return
    if (!message.canForward() || targets.isEmpty()) return
    val chosen = targets.distinctBy { it.chat.id ?: it.connection.id }.take(FORWARD_MAX_TARGETS)
    viewModelScope.launch {
        val kind = message.messageType.lowercase()
        val isImage = kind == ChatMessageType.IMAGE
        val isAudio = kind == ChatMessageType.AUDIO
        val isFile = kind == ChatMessageType.FILE
        val imageBytes = if (isImage || isAudio) fetchDecryptedChatMediaBytes(message) else null
        if ((isImage || isAudio) && imageBytes == null) {
            _chatNotice.value = if (isAudio) "Couldn't forward that voice note" else "Couldn't forward that photo"
            return@launch
        }
        val fileEnvelope =
            if (isFile) AttachmentCrypto.resolvePresentation(message.content, message.metadata, isFileMessage = true)?.envelope else null
        val fileBytes =
            fileEnvelope?.let {
                decryptChatAttachmentBytes(
                    message.id,
                    it,
                    message.e2eeV2MediaMetadataOrNull(currentApiChatId),
                    message.e2eeV2MediaStoragePathOrNull(),
                ).getOrNull()
            }
        if (isFile && (fileEnvelope == null || fileBytes == null)) {
            _chatNotice.value = "Couldn't forward that file"
            return@launch
        }
        var sent = 0
        for (target in chosen) {
            val chatId = forwardTargetChatId(target) ?: continue
            val connectionId = target.connection.id.takeIf { target.groupClique == null }
            val localMs = Clock.System.now().toEpochMilliseconds()
            val result =
                runCatching {
                    if (fileEnvelope != null && fileBytes != null) {
                        forwardFile(chatId, userId, fileEnvelope, fileBytes, localMs, connectionId)
                    } else if (imageBytes != null) {
                        val mime = message.originalMimeTypeOrNull() ?: if (isAudio) "audio/mp4" else "image/jpeg"
                        val ext = extensionForChatMedia(mime, isImage = !isAudio)
                        val path = "$userId/$chatId/$localMs-${Random.nextInt(1_000_000_000)}.$ext"
                        val url = chatRepository.uploadChatMedia(imageBytes, path, mime) ?: return@runCatching null
                        chatRepository.sendMessage(
                            chatId = chatId,
                            userId = userId,
                            content = if (isAudio) " " else message.forwardableCaption().ifEmpty { " " },
                            messageType = if (isAudio) ChatMessageType.AUDIO else ChatMessageType.IMAGE,
                            metadata =
                                buildJsonObject {
                                    put("media_url", url)
                                    put("original_mime_type", mime)
                                    put("is_encrypted_media", true)
                                    message.voiceDurationSeconds()?.let { put("duration_seconds", it) }
                                    put("forwarded", true)
                                },
                            clientLocalSentAtMs = localMs,
                            connectionId = connectionId,
                        )
                    } else {
                        chatRepository.sendMessage(
                            chatId = chatId,
                            userId = userId,
                            content = message.content,
                            messageType = ChatMessageType.TEXT,
                            metadata = buildJsonObject { put("forwarded", true) },
                            clientLocalSentAtMs = localMs,
                            connectionId = connectionId,
                        )
                    }
                }.getOrNull()
            if (result != null) sent++
        }
        _chatNotice.value =
            when {
                sent == chosen.size && sent == 1 -> "Forwarded"
                sent == chosen.size -> "Forwarded to $sent chats"
                sent == 0 -> "Couldn't forward that message"
                else -> "Forwarded to $sent of ${chosen.size} chats"
            }
    }
}

private fun Message.voiceDurationSeconds(): Int? =
    ((metadata as? kotlinx.serialization.json.JsonObject)?.get("duration_seconds") as? kotlinx.serialization.json.JsonPrimitive)
        ?.content
        ?.toDoubleOrNull()
        ?.toInt()

/** Re-seals a decrypted file for [chatId] exactly like a fresh file send, marked forwarded. */
private suspend fun ChatViewModel.forwardFile(
    chatId: String,
    userId: String,
    source: AttachmentCrypto.Envelope,
    bytes: ByteArray,
    localMs: Long,
    connectionId: String?,
): Message? {
    val uploaded =
        chatRepository.uploadEncryptedBlob(
            bucketName = CHAT_ATTACHMENTS_BUCKET,
            chatId = chatId,
            senderUserId = userId,
            plainBytes = bytes,
            mimeType = source.mime.ifBlank { "application/octet-stream" },
            fileName = source.name.ifBlank { "attachment" },
        ) ?: return null
    val envelope =
        AttachmentCrypto.Envelope(
            v = if (uploaded.fileMasterKeyBase64.isBlank()) 2 else 1,
            type = "file",
            name = uploaded.fileName,
            mime = uploaded.mimeType,
            size = uploaded.sizeBytes,
            path = uploaded.path,
            key = uploaded.fileMasterKeyBase64,
            sha256 = uploaded.sha256Base64,
        )
    return chatRepository.sendMessage(
        chatId = chatId,
        userId = userId,
        content = AttachmentCrypto.encodeEnvelope(envelope),
        messageType = ChatMessageType.FILE,
        metadata =
            buildJsonObject {
                put("attachment_path", uploaded.path)
                put("attachment_name", uploaded.fileName)
                put("attachment_mime", uploaded.mimeType)
                put("attachment_size", uploaded.sizeBytes)
                put("forwarded", true)
            },
        clientLocalSentAtMs = localMs,
        connectionId = connectionId,
    )
}
