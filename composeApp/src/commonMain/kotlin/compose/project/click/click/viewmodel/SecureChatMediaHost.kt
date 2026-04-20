package compose.project.click.click.viewmodel

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
    fun ensureSecureChatImageLoaded(scopeId: String, viewerUserId: String, message: Message)
    fun ensureSecureChatAudioLoaded(scopeId: String, viewerUserId: String, message: Message)
}
