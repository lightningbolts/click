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
import compose.project.click.click.util.chatMediaVaultLocalPath
import compose.project.click.click.util.imageVaultFileExtension // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import compose.project.click.click.util.writeChatMediaVaultFile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit

/**
 * Develops a gated Click Drop (sent by a client with `drops_develop` on) once it is due, and swaps
 * the pixelated preview for the original in this chat's media state. Until then the bubble stays
 * pixelated and save/share stay off, so the preview is never treated as the photo.
 */
internal fun ChatViewModel.ensureGatedDropOriginalLoaded(
    scopeId: String,
    viewerUserId: String,
    message: Message,
    signedUrl: String? = null,
) {
    if (GatedDropOriginals.isLoaded(message.id)) return
    if (!gatedDropLoadsInFlight.add(message.id)) return
    viewModelScope.launch(chatMediaDispatcher) {
        try {
            val extension =
                chatMediaVaultExtensionForMessage(message)
                    ?: imageVaultFileExtension(message.originalMimeTypeOrNull(), message.mediaUrlOrNull().orEmpty())
            val result =
                secureImageNetworkLoads.withPermit {
                    chatRepository.loadGatedDropOriginal(scopeId, viewerUserId, message, extension, signedUrl)
                }
            if (result is GatedDropOriginalResult.Developed) {
                secureImageBytesCache.put(message.id, result.bytes)
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
            gatedDropLoadsInFlight.remove(message.id)
        }
    }
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
    writeChatMediaVaultFile(gatedDropOriginalVaultKey(message.id), original, extension)
}

/** [ChatDropDevelopService] over the chat repository (`/api/drops/develop`, `/api/drops/views`). */
internal class RepositoryChatDropDevelopService(
    private val repository: () -> compose.project.click.click.data.repository.ChatRepository,
) : ChatDropDevelopService {
    override suspend fun develop(ids: List<String>) = repository().developChatDrops(ids)

    override suspend fun developedAt(ids: List<String>) = repository().loadChatDropDevelopedAt(ids)
}

/** True when this device holds the drop's original already (developed here, or sent from here). */
internal fun hasLocalGatedDropOriginal(message: Message): Boolean {
    val extension =
        chatMediaVaultExtensionForMessage(message)
            ?: imageVaultFileExtension(message.originalMimeTypeOrNull(), message.mediaUrlOrNull().orEmpty())
    // A file check only: no decode on the main thread.
    return chatMediaVaultLocalPath(gatedDropOriginalVaultKey(message.id), extension) != null
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
    private val messagesById = mutableMapOf<String, Message>()

    val controller =
        ChatDropDevelopController(
            scope = viewModel.viewModelScope,
            service = RepositoryChatDropDevelopService { viewModel.chatRepository },
            hasLocalOriginal = { id -> messagesById[id]?.let(::hasLocalGatedDropOriginal) == true },
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
        val refs = messages.mapNotNull { message -> message.clickDropRevealRefOrNull()?.also { messagesById[it.id] = message } }
        controller.check(refs)
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
        messagesById.clear()
        controller.clear()
    }
}
