package compose.project.click.click.viewmodel

import compose.project.click.click.collaboration.ClickDropDevelopBook
import compose.project.click.click.data.models.Message
import kotlinx.coroutines.flow.StateFlow

data class SecureChatMediaLoadState(
    val loading: Boolean = false,
    val imageBytes: ByteArray? = null,
    val audioLocalPath: String? = null,
    val error: String? = null,
    /** 0..1 while an outgoing image upload is in flight (optimistic bubble). */
    val uploadProgress: Float? = null,
)

/**
 * Host for download/decrypt of E2EE chat media. Implemented by [ChatViewModel] (1:1 / groups)
 * and [HubChatViewModel] (hub broadcast key). [scopeId] is chat UUID or hub id respectively.
 */
interface SecureChatMediaHost {
    val secureChatMediaLoadState: StateFlow<Map<String, SecureChatMediaLoadState>>

    fun ensureSecureChatImageLoaded(
        scopeId: String,
        viewerUserId: String,
        message: Message,
    )

    fun ensureSecureChatAudioLoaded(
        scopeId: String,
        viewerUserId: String,
        message: Message,
    )

    /**
     * Server develop for gated Click Drops (tap, "Develop all", live develop). Null where drops
     * don't develop through the server (hub chats); those bubbles keep developing at reveal.
     */
    val clickDropDevelop: ClickDropDevelopHost? get() = null
}

/** What a chat's Click Drop bubbles need (iOS `ClickDropControls`). */
interface ClickDropDevelopHost {
    val clickDropBook: StateFlow<ClickDropDevelopBook>

    /** Learns which revealed drops this viewer already developed (once per drop). */
    fun checkClickDrops(messages: List<Message>)

    /** Tap to develop or "Develop all"; only ready drops go to the server. */
    fun developClickDrops(
        scopeId: String,
        viewerUserId: String,
        messages: List<Message>,
    )

    /** Live develop: called by a bubble on screen when its drop reaches zero. */
    fun developClickDropIfJustRevealed(
        scopeId: String,
        viewerUserId: String,
        message: Message,
    )

    /** The bubble finished playing the develop. */
    fun didShowClickDropDevelop(messageId: String)
}
