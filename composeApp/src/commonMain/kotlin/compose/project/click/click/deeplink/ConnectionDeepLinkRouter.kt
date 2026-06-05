package compose.project.click.click.deeplink

import compose.project.click.click.qr.toUserIdFromClickUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Routes Universal Links and custom URL schemes for `/c/{userId}` into the connection handshake flow.
 * Platform entry points (iOS AppDelegate, Android MainActivity) call [handleIncomingUrl]; [App] observes
 * [pendingConnectionUserId] for both cold and warm starts.
 */
object ConnectionDeepLinkRouter {
    private val _pendingConnectionUserId = MutableStateFlow<String?>(null)
    val pendingConnectionUserId: StateFlow<String?> = _pendingConnectionUserId.asStateFlow()

    /**
     * Parse a connection user id from a Universal Link or deep link URL.
     * Supports `https://click-us.vercel.app/c/{uuid}`, legacy `/connect/{uuid}`, and `click://` variants.
     */
    fun parseConnectionUserId(url: String): String? =
        url.trim().toUserIdFromClickUrl()?.takeIf { it.isNotBlank() }

    /**
     * Queue a connection handshake for [url]. Returns true when the URL was recognized.
     */
    fun handleIncomingUrl(url: String): Boolean {
        val userId = parseConnectionUserId(url) ?: return false
        _pendingConnectionUserId.value = userId
        return true
    }

    fun setPendingConnectionUserId(userId: String) {
        val trimmed = userId.trim()
        if (trimmed.isNotEmpty()) {
            _pendingConnectionUserId.value = trimmed
        }
    }

    fun consume(): String? {
        val value = _pendingConnectionUserId.value
        _pendingConnectionUserId.value = null
        return value
    }
}
