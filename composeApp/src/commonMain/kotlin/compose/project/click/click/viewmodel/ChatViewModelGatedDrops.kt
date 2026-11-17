package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.flags.FeatureFlag
import compose.project.click.click.data.flags.FeatureFlags
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.isGatedDrop
import compose.project.click.click.data.models.mediaUrlOrNull // pragma: allowlist secret
import compose.project.click.click.data.models.originalMimeTypeOrNull // pragma: allowlist secret
import compose.project.click.click.data.repository.GatedDropOriginalResult
import compose.project.click.click.data.repository.GatedDropOriginals
import compose.project.click.click.data.repository.gatedDropOriginalVaultKey
import compose.project.click.click.ui.chat.secureChatImageBitmapCache // pragma: allowlist secret
import compose.project.click.click.util.chatMediaDispatcher // pragma: allowlist secret
import compose.project.click.click.util.chatMediaVaultExtensionForMessage // pragma: allowlist secret
import compose.project.click.click.util.imageVaultFileExtension // pragma: allowlist secret
import compose.project.click.click.util.readChatMediaVaultBytes
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import compose.project.click.click.util.writeChatMediaVaultFile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit

/**
 * Develops a gated Click Drop (sent by a client with `drops_develop` on) once it is due, and swaps
 * the pixelated preview for the original in this chat's media state. Until then the bubble stays
 * pixelated and save/share stay off, so the preview is never treated as the photo. The original is
 * kept only under [gatedDropOriginalVaultKey] (memory and vault), never under the message id.
 */
internal fun ChatViewModel.ensureGatedDropOriginalLoaded(
    scopeId: String,
    viewerUserId: String,
    message: Message,
    signedUrl: String? = null,
) {
    val originalKey = gatedDropOriginalVaultKey(viewerUserId, message.id)
    // "Loaded" alone isn't enough: the original may have been evicted since. Reload it then.
    if (GatedDropOriginals.isLoaded(message.id) && secureImageBytesCache.get(originalKey)?.isNotEmpty() == true) return
    if (!tryStartGatedDropLoad(message.id)) return
    GatedDropOriginals.clearFailed(message.id)
    viewModelScope.launch(chatMediaDispatcher) {
        var developed = false
        try {
            val extension =
                chatMediaVaultExtensionForMessage(message)
                    ?: imageVaultFileExtension(message.originalMimeTypeOrNull(), message.mediaUrlOrNull().orEmpty())
            val result =
                secureImageNetworkLoads.withPermit {
                    chatRepository.loadGatedDropOriginal(scopeId, viewerUserId, message, extension, signedUrl)
                }
            if (result is GatedDropOriginalResult.Developed) {
                developed = true
                secureImageBytesCache.put(originalKey, result.bytes)
                // Drop the preview bitmap so the bubble decodes the original.
                secureChatImageBitmapCache.remove(message.id)
                _secureChatMediaLoadState.update {
                    it + (message.id to SecureChatMediaLoadState(loading = false, imageBytes = result.bytes))
                }
                GatedDropOriginals.markLoaded(message.id)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            println("ChatViewModel: gated drop develop failed for message=${message.id}: ${e.redactedRestMessage()}")
        } finally {
            if (!developed) {
                // Unavailable, pending, offline or rate limited: the bubble offers "Tap to retry".
                GatedDropOriginals.forget(message.id)
                GatedDropOriginals.markFailed(message.id)
            }
            finishGatedDropLoad(message.id)
        }
    }
}

/** Atomically claims [messageId] for an original load; false when one is in flight already. */
internal fun ChatViewModel.tryStartGatedDropLoad(messageId: String): Boolean {
    var claimed = false
    gatedDropLoadsInFlight.update { inFlight ->
        claimed = messageId !in inFlight
        if (claimed) inFlight + messageId else inFlight
    }
    return claimed
}

/** Safe from any thread (the load finishes on [chatMediaDispatcher]). */
internal fun ChatViewModel.finishGatedDropLoad(messageId: String) {
    gatedDropLoadsInFlight.update { it - messageId }
}

/**
 * True when this chat holds (or is about to show) a gated drop's original, so a preview load that
 * lands late must not replace it.
 */
internal fun ChatViewModel.holdsGatedDropOriginal(
    viewerUserId: String?,
    message: Message,
): Boolean {
    if (!message.isGatedDrop()) return false
    if (GatedDropOriginals.isLoaded(message.id)) return true
    val viewer = viewerUserId?.takeIf { it.isNotBlank() } ?: return false
    return secureImageBytesCache.get(gatedDropOriginalVaultKey(viewer, message.id))?.isNotEmpty() == true
}

/**
 * A gated drop's original for a drop marked loaded: from memory, else this viewer's vault file.
 * Null when it isn't loaded, or its original is gone (the caller treats it as still awaiting).
 * Reads the disk: call off the main thread.
 */
internal fun ChatViewModel.loadedGatedDropOriginalBytes(
    message: Message,
    viewerUserId: String?,
): ByteArray? {
    if (!GatedDropOriginals.isLoaded(message.id)) return null
    val viewer = viewerUserId?.takeIf { it.isNotBlank() } ?: return null
    val key = gatedDropOriginalVaultKey(viewer, message.id)
    secureImageBytesCache.get(key)?.takeIf { it.isNotEmpty() }?.let { return it }
    return readChatMediaVaultBytes(key, chatMediaVaultExtensionForMessage(message))
        ?.takeIf { it.isNotEmpty() }
        ?.also { secureImageBytesCache.put(key, it) }
}

/**
 * [ChatViewModel.ensureSecureChatImageLoaded] for a gated drop: shows its original when this viewer
 * holds it in memory. Otherwise a developed drop (re)loads its original, keeping a "loaded" mark
 * until that settles; a drop not developed (per the server) drops a stale mark. True when shown.
 */
internal fun ChatViewModel.showOrLoadGatedDropOriginal(
    scopeId: String,
    viewerUserId: String,
    message: Message,
): Boolean {
    val original = secureImageBytesCache.get(gatedDropOriginalVaultKey(viewerUserId, message.id))?.takeIf { it.isNotEmpty() }
    if (original != null) {
        // Only an original load puts bytes under this key, and it runs once the server says developed.
        if (_secureChatMediaLoadState.value[message.id]?.imageBytes !== original) {
            _secureChatMediaLoadState.update {
                it + (message.id to SecureChatMediaLoadState(loading = false, imageBytes = original))
            }
        }
        GatedDropOriginals.markLoaded(message.id)
        return true
    }
    if (clickDropHost.isDeveloped(message.id)) {
        // Developed by this viewer: load the original. The preview keeps the bubble filled meanwhile.
        ensureGatedDropOriginalLoaded(scopeId, viewerUserId, message)
    } else if (message.id !in gatedDropLoadsInFlight.value) {
        GatedDropOriginals.forget(message.id)
    }
    return false
}

/**
 * True when this sender's Click Drop goes out gated: `drops_develop` is on and it's a photo. Mirrors
 * iOS `ChatComposerDraft.gatesOriginal`; with the flag off, drops send as before (original as media).
 */
internal fun gatesClickDropOriginal(
    mimeType: String,
    flagEnabled: Boolean = FeatureFlags.isEnabled(FeatureFlag.DROPS_DEVELOP),
): Boolean = flagEnabled && mimeType.trim().lowercase().startsWith("image/")

/** Keeps the sender's own original under the developed-original vault key (as iOS does after send). */
internal fun storeOwnGatedDropOriginal(
    message: Message,
    original: ByteArray,
) {
    val extension =
        chatMediaVaultExtensionForMessage(message)
            ?: imageVaultFileExtension(message.originalMimeTypeOrNull(), message.mediaUrlOrNull().orEmpty())
    writeChatMediaVaultFile(gatedDropOriginalVaultKey(message.user_id, message.id), original, extension)
}

/** [ChatDropDevelopService] over the chat repository (`/api/drops/develop`, `/api/drops/views`). */
internal class RepositoryChatDropDevelopService(
    private val repository: () -> compose.project.click.click.data.repository.ChatRepository,
) : ChatDropDevelopService {
    override suspend fun develop(ids: List<String>) = repository().developChatDrops(ids)

    override suspend fun developedAt(ids: List<String>) = repository().loadChatDropDevelopedAt(ids)
}

/**
 * [ChatViewModel]'s [ClickDropDevelopHost]. A developed drop's original loads with the signed URL
 * that came with the develop, so a "Develop all" costs one develop call and a download per photo.
 */
internal class ChatViewModelClickDropHost(
    private val viewModel: ChatViewModel,
) : ClickDropDevelopHost {
    /** Where to load each drop's original once developed (main-thread only). */
    private val contexts = mutableMapOf<String, Triple<String, String, Message>>()

    val controller =
        ChatDropDevelopController(
            scope = viewModel.viewModelScope,
            service = RepositoryChatDropDevelopService { viewModel.chatRepository },
            onDeveloped = { id, url ->
                contexts.remove(id)?.let { (scopeId, viewer, message) ->
                    // A legacy drop's media is the original already: the bubble unlocks from the book.
                    if (message.isGatedDrop()) viewModel.ensureGatedDropOriginalLoaded(scopeId, viewer, message, url)
                }
            },
            onError = { viewModel._chatNotice.value = it },
        )

    override val clickDropBook get() = controller.book

    fun isDeveloped(messageId: String): Boolean = controller.book.value.isDeveloped(messageId)

    override fun checkClickDrops(messages: List<Message>) {
        controller.check(messages.mapNotNull { it.clickDropRevealRefOrNull() })
    }

    override fun developClickDrops(
        scopeId: String,
        viewerUserId: String,
        messages: List<Message>,
    ) {
        val refs =
            messages.mapNotNull { message ->
                message.clickDropRevealRefOrNull()?.also { contexts[it.id] = Triple(scopeId, viewerUserId, message) }
            }
        controller.develop(refs)
    }

    override fun developClickDropIfJustRevealed(
        scopeId: String,
        viewerUserId: String,
        message: Message,
    ) {
        val ref = message.clickDropRevealRefOrNull() ?: return
        contexts[ref.id] = Triple(scopeId, viewerUserId, message)
        controller.developIfJustRevealed(ref)
    }

    override fun didShowClickDropDevelop(messageId: String) = controller.shown(messageId)

    fun clear() {
        contexts.clear()
        controller.clear()
    }
}
