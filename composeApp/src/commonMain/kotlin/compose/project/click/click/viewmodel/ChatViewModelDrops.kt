@file:Suppress("ktlint:standard:backing-property-naming")

package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.collaboration.ClickDropDevelopState
import compose.project.click.click.collaboration.ClickDropFlags
import compose.project.click.click.collaboration.clickDropPreviewJpeg
import compose.project.click.click.core.FeatureFlagGate
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.isGatedDrop
import compose.project.click.click.data.repository.ClickDropOriginalUpload
import compose.project.click.click.util.chatMediaVaultExtensionForMessage
import compose.project.click.click.util.readChatMediaVaultBytes
import compose.project.click.click.util.writeChatMediaVaultFile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/*
 * Click Drop develop flow in a chat (spec §2, sheet 07 G0-5 / G2-1 / G2-2). The state machine lives
 * in [compose.project.click.click.collaboration.ClickDropDevelopController]; this file wires it to
 * the open timeline and to gated originals, which are fetched once after develop and cached on disk
 * as `<messageId>-original`. The preview is never saved or shared as the photo.
 */

private const val DROP_ORIGINAL_MEMORY_ENTRIES = 24

/** Vault key for a gated drop's decrypted original (iOS `dropOriginalVaultKey`). */
internal fun dropOriginalVaultKey(messageId: String): String = "$messageId-original"

internal fun ChatViewModel.timelineMessages(): List<Message> =
    (_chatMessagesState.value as? ChatMessagesState.Success)?.messages?.map { it.message }.orEmpty()

/** The develop state of [message] for this viewer, or null outside the develop flow. */
fun ChatViewModel.clickDropState(message: Message): ClickDropDevelopState? = clickDrops.state(message)

/** Tap to develop one drop. */
fun ChatViewModel.developClickDrop(message: Message) {
    viewModelScope.launch { clickDrops.develop(listOf(message)) }
}

/** "Develop all (N)": every ready drop on screen. */
fun ChatViewModel.developAllClickDrops() {
    viewModelScope.launch { clickDrops.develop(clickDrops.readyDrops(timelineMessages())) }
}

/** Live develop: called on a tick while the chat is visible. */
fun ChatViewModel.developClickDropsRevealedWhileVisible() {
    viewModelScope.launch { clickDrops.developRevealedWhileVisible(timelineMessages()) }
}

/**
 * The developed original of a gated drop: from memory, the disk vault, or the develop-issued signed
 * URL (developing again for a fresh one when it's missing or expired). Null until developed.
 */
internal suspend fun ChatViewModel.loadDevelopedDropOriginal(message: Message): ByteArray? {
    if (!message.isGatedDrop()) return null
    _dropOriginalBytes.value[message.id]?.let { return it }
    val extension = chatMediaVaultExtensionForMessage(message) ?: "jpg"
    val key = dropOriginalVaultKey(message.id)
    readChatMediaVaultBytes(key, extension)?.takeIf { it.isNotEmpty() }?.let {
        publishDropOriginal(message.id, it)
        return it
    }
    if (clickDrops.state(message) != ClickDropDevelopState.Developed) return null
    val s = _chatMessagesState.value as? ChatMessagesState.Success ?: return null
    val chatId = s.chatDetails.chat.id ?: return null
    val viewer = _currentUserId.value ?: return null

    suspend fun fetch(url: String?): ByteArray? =
        url?.let {
            withContext(compose.project.click.click.util.chatMediaDispatcher) {
                secureImageNetworkLoads.withPermit { chatRepository.loadClickDropOriginal(chatId, viewer, message, it) }
            }
        }
    val bytes =
        fetch(clickDrops.signedOriginalUrl(message.id))?.takeIf { it.isNotEmpty() }
            ?: fetch(clickDrops.refreshSignedOriginalUrl(message.id))?.takeIf { it.isNotEmpty() }
            ?: return null
    writeChatMediaVaultFile(key, bytes, extension)
    publishDropOriginal(message.id, bytes)
    return bytes
}

/** Starts loading a developed gated original for its bubble (at most one load per drop). */
fun ChatViewModel.requestDevelopedDropOriginal(message: Message) {
    if (!message.isGatedDrop() || message.id in _dropOriginalBytes.value || !dropOriginalLoads.add(message.id)) return
    viewModelScope.launch {
        try {
            loadDevelopedDropOriginal(message)
        } finally {
            dropOriginalLoads.remove(message.id)
        }
    }
}

private fun ChatViewModel.publishDropOriginal(
    id: String,
    bytes: ByteArray,
) {
    _dropOriginalBytes.update { current ->
        val next = current + (id to bytes)
        if (next.size <=
            DROP_ORIGINAL_MEMORY_ENTRIES
        ) {
            next
        } else {
            next.entries.drop(next.size - DROP_ORIGINAL_MEMORY_ENTRIES).associate {
                it.key to
                    it.value
            }
        }
    }
}

/** True when the sender's chat drops go out gated (flag on and an image). */
internal fun gatesClickDropOriginal(mimeType: String): Boolean =
    FeatureFlagGate.isEnabled(ClickDropFlags.DROPS_DEVELOP) && mimeType.lowercase().startsWith("image/")

/**
 * A gated drop's two uploads: the pixelated preview as the message media and the E2EE original
 * through `drop_original: true`. Returns the preview's media URL and the original's upload.
 */
internal suspend fun ChatViewModel.uploadGatedClickDrop(
    bytes: ByteArray,
    apiChatId: String,
    userId: String,
    mimeType: String,
    previewObjectPath: String,
): Pair<String, ClickDropOriginalUpload>? {
    val preview = clickDropPreviewJpeg(bytes) ?: return null
    val previewUrl = chatRepository.uploadChatMedia(preview, previewObjectPath, "image/jpeg") ?: return null
    val original =
        chatRepository.uploadClickDropOriginal(
            plainBytes = bytes,
            chatId = apiChatId,
            senderUserId = userId,
            mimeType = mimeType,
            previewClientMessageId = chatRepository.uploadedMediaClientMessageId(previewUrl),
        ) ?: return null
    return previewUrl to original
}

/** The sender already has the original: developing their own drop needs no download. */
internal fun ChatViewModel.storeOwnDropOriginal(
    message: Message,
    original: ByteArray,
) {
    writeChatMediaVaultFile(dropOriginalVaultKey(message.id), original, chatMediaVaultExtensionForMessage(message) ?: "jpg")
}
