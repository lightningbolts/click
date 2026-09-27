package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.data.models.FORWARD_MAX_TARGETS // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.canForward // pragma: allowlist secret
import compose.project.click.click.data.models.forwardableCaption // pragma: allowlist secret
import compose.project.click.click.data.models.originalMimeTypeOrNull // pragma: allowlist secret
import compose.project.click.click.util.isPersistedApiChatId // pragma: allowlist secret
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/*
 * Forwarding (iOS `ConversationModel.forward`): each target gets a fresh message encrypted for that
 * chat (text through the normal send path, photos re-uploaded from the decrypted local copy),
 * marked `metadata.forwarded` so every reader sees "Forwarded". No server-side copy of plaintext.
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
        val isImage = message.messageType.lowercase() == ChatMessageType.IMAGE
        val imageBytes = if (isImage) fetchDecryptedChatMediaBytes(message) else null
        if (isImage && imageBytes == null) {
            _chatNotice.value = "Couldn't forward that photo"
            return@launch
        }
        var sent = 0
        for (target in chosen) {
            val chatId = forwardTargetChatId(target) ?: continue
            val connectionId = target.connection.id.takeIf { target.groupClique == null }
            val localMs = Clock.System.now().toEpochMilliseconds()
            val result =
                runCatching {
                    if (imageBytes != null) {
                        val mime = message.originalMimeTypeOrNull() ?: "image/jpeg"
                        val ext = extensionForChatMedia(mime, isImage = true)
                        val path = "$userId/$chatId/$localMs-${Random.nextInt(1_000_000_000)}.$ext"
                        val url = chatRepository.uploadChatMedia(imageBytes, path, mime) ?: return@runCatching null
                        chatRepository.sendMessage(
                            chatId = chatId,
                            userId = userId,
                            content = message.forwardableCaption().ifEmpty { " " },
                            messageType = ChatMessageType.IMAGE,
                            metadata =
                                buildJsonObject {
                                    put("media_url", url)
                                    put("original_mime_type", mime)
                                    put("is_encrypted_media", true)
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
