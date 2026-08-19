@file:Suppress(
    "ktlint:standard:backing-property-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.data.models.audioCacheFileExtension // pragma: allowlist secret
import compose.project.click.click.data.models.hasLocalMediaUri // pragma: allowlist secret
import compose.project.click.click.data.models.isEncryptedMedia // pragma: allowlist secret
import compose.project.click.click.data.models.mediaUrlOrNull // pragma: allowlist secret
import compose.project.click.click.ui.chat.deleteSecureChatAudioTempFile // pragma: allowlist secret
import compose.project.click.click.ui.chat.secureChatImageBitmapCache // pragma: allowlist secret
import compose.project.click.click.ui.chat.writeSecureChatAudioTempFile // pragma: allowlist secret
import compose.project.click.click.util.chatMediaDispatcher // pragma: allowlist secret
import compose.project.click.click.util.chatMediaVaultExtensionForMessage // pragma: allowlist secret
import compose.project.click.click.util.fileUriToLocalPath // pragma: allowlist secret
import compose.project.click.click.util.isChatMediaVaultLocalPath // pragma: allowlist secret
import compose.project.click.click.util.readChatMediaVaultBytesForMessage // pragma: allowlist secret
import compose.project.click.click.util.readChatMediaVaultLocalPathForMessage // pragma: allowlist secret
import compose.project.click.click.util.writeChatMediaVaultFile // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.put

internal fun ChatViewModel.clearSecureChatMediaCache(purgePersistentCache: Boolean = false) {
    _secureChatMediaLoadState.value = emptyMap()
    if (purgePersistentCache) {
        secureAudioPathCache.valuesSnapshot().forEach { path ->
            deleteSecureChatAudioTempFile(path)
        }
        secureAudioPathCache.clear()
        secureImageBytesCache.clear()
    }
}

internal fun ChatViewModel.hydrateSecureMediaStateFromByteCache(messages: List<MessageWithUser>) {
    if (messages.isEmpty()) return
    _secureChatMediaLoadState.update { map ->
        var out = map
        for (mwu in messages) {
            val id = mwu.message.id
            if (out[id]?.imageBytes != null) continue
            val bytes = secureImageBytesCache.get(id)?.takeIf { it.isNotEmpty() } ?: continue
            out = out + (id to SecureChatMediaLoadState(loading = false, imageBytes = bytes))
        }
        out
    }
}

internal suspend fun ChatViewModel.hydrateSecureMediaFromDiskVault(messages: List<MessageWithUser>) {
    if (messages.isEmpty()) return
    val vaultMessages =
        messages.filter {
            val type = it.message.messageType.lowercase()
            type == ChatMessageType.IMAGE || type == ChatMessageType.AUDIO
        }
    val visibleBatch = vaultMessages.takeLast(SECURE_CHAT_DISK_HYDRATE_VISIBLE_BATCH)
    val deferredBatch = vaultMessages.dropLast(SECURE_CHAT_DISK_HYDRATE_VISIBLE_BATCH)
    applyDiskVaultHydration(visibleBatch)
    if (deferredBatch.isNotEmpty()) {
        viewModelScope.launch(Dispatchers.Default) {
            applyDiskVaultHydration(deferredBatch)
        }
    }
}

internal suspend fun ChatViewModel.applyDiskVaultHydration(batch: List<MessageWithUser>) {
    if (batch.isEmpty()) return
    val updates =
        withContext(Dispatchers.Default) {
            val out = LinkedHashMap<String, SecureChatMediaLoadState>()
            for (mwu in batch) {
                val msg = mwu.message
                val id = msg.id
                val extension = chatMediaVaultExtensionForMessage(msg)
                when (msg.messageType.lowercase()) {
                    ChatMessageType.IMAGE -> {
                        if (_secureChatMediaLoadState.value[id]?.imageBytes != null) continue
                        val memCached = secureImageBytesCache.get(id)?.takeIf { it.isNotEmpty() }
                        if (memCached != null) {
                            out[id] = SecureChatMediaLoadState(loading = false, imageBytes = memCached)
                            continue
                        }
                        if (secureChatImageBitmapCache.get(id) != null) continue
                        val vaultBytes =
                            readChatMediaVaultBytesForMessage(
                                messageId = id,
                                mediaUrl = msg.mediaUrlOrNull(),
                                preferredExtension = extension,
                            ) ?: continue
                        secureImageBytesCache.put(id, vaultBytes)
                        out[id] = SecureChatMediaLoadState(loading = false, imageBytes = vaultBytes)
                    }
                    ChatMessageType.AUDIO -> {
                        if (_secureChatMediaLoadState.value[id]?.audioLocalPath != null) continue
                        val audioPath = resolveVaultedAudioLocalPath(msg, extension) ?: continue
                        secureAudioPathCache.put(id, audioPath)
                        out[id] = SecureChatMediaLoadState(loading = false, audioLocalPath = audioPath)
                    }
                }
            }
            out
        }
    if (updates.isNotEmpty()) {
        _secureChatMediaLoadState.update { it + updates }
    }
}

internal fun ChatViewModel.resolveVaultedAudioLocalPath(
    message: Message,
    extension: String?,
): String? {
    if (message.hasLocalMediaUri()) {
        return fileUriToLocalPath(message.mediaUrlOrNull().orEmpty()).takeIf { it.isNotBlank() }
    }
    return readChatMediaVaultLocalPathForMessage(
        messageId = message.id,
        preferredExtension = extension,
        mediaUrl = message.mediaUrlOrNull(),
    )
}

internal suspend fun ChatViewModel.warmSecureMediaForTimeline(messages: List<MessageWithUser>) {
    hydrateSecureMediaStateFromByteCache(messages)
    hydrateSecureMediaFromDiskVault(messages)
}

internal fun ChatViewModel.cacheAndPublishSecureAudio(
    message: Message,
    bytes: ByteArray,
) {
    val path = cacheSecureAudioOnDisk(message.id, bytes, message.audioCacheFileExtension())
    if (path.isNullOrBlank()) {
        println("ChatViewModel: secure audio cache write failed for message=${message.id}")
        _secureChatMediaLoadState.update {
            it + (message.id to SecureChatMediaLoadState(loading = false, error = "Could not cache audio"))
        }
    } else {
        val evictedPath = secureAudioPathCache.put(message.id, path)
        if (!evictedPath.isNullOrBlank() && evictedPath != path && !isChatMediaVaultLocalPath(evictedPath)) {
            deleteSecureChatAudioTempFile(evictedPath)
        }
        _secureChatMediaLoadState.update {
            it + (message.id to SecureChatMediaLoadState(loading = false, audioLocalPath = path))
        }
    }
}

internal fun ChatViewModel.cacheSecureAudioOnDisk(
    messageId: String,
    bytes: ByteArray,
    extension: String,
): String? {
    val vaultUri = writeChatMediaVaultFile(messageId, bytes, extension)
    if (!vaultUri.isNullOrBlank()) {
        return fileUriToLocalPath(vaultUri)
    }
    return writeSecureChatAudioTempFile(messageId, bytes, extension)
}

internal suspend fun ChatViewModel.fetchDecryptedChatMediaBytesImpl(message: Message): ByteArray? {
    secureImageBytesCache.get(message.id)?.takeIf { it.isNotEmpty() }?.let { return it }
    readChatMediaVaultBytesForMessage(
        messageId = message.id,
        mediaUrl = message.mediaUrlOrNull(),
        preferredExtension = chatMediaVaultExtensionForMessage(message),
    )?.takeIf { it.isNotEmpty() }
        ?.also { secureImageBytesCache.put(message.id, it) }
        ?.let { return it }
    val s = _chatMessagesState.value as? ChatMessagesState.Success ?: return null
    val cid = s.chatDetails.chat.id ?: return null
    val uid = _currentUserId.value ?: return null
    val url = message.mediaUrlOrNull() ?: return null
    if (!message.isEncryptedMedia()) return null
    val bytes =
        withContext(chatMediaDispatcher) {
            secureImageNetworkLoads.withPermit {
                chatRepository.downloadAndDecryptChatMedia(cid, uid, url)
            }
        }
    if (bytes != null && bytes.isNotEmpty()) {
        secureImageBytesCache.put(message.id, bytes)
        chatMediaVaultExtensionForMessage(message)?.let { ext ->
            writeChatMediaVaultFile(message.id, bytes, ext)
        }
    }
    return bytes
}

internal fun extensionForChatMedia(
    mime: String,
    isImage: Boolean,
): String {
    val m = mime.lowercase()
    return when {
        m.contains("png") -> "png"
        m.contains("webp") -> "webp"
        m.contains("jpeg") || m.contains("jpg") -> "jpg"
        m.contains("mpeg") || m.contains("mp3") -> "mp3"
        m.contains("mp4") || m.contains("m4a") || m.contains("aac") -> "m4a"
        m.contains("ogg") -> "ogg"
        m.contains("wav") -> "wav"
        isImage -> "jpg"
        else -> "bin"
    }
}
