package compose.project.click.click.deeplink

import compose.project.click.click.qr.clickLinkExpQueryValue
import compose.project.click.click.qr.clickLinkTokenQueryValue
import compose.project.click.click.qr.clickLinkVenueQueryValue
import compose.project.click.click.qr.parseQrPayload
import compose.project.click.click.qr.toTokenQrPayloadFromClickLinkOrNull
import compose.project.click.click.qr.toUserIdFromClickUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A connection handshake requested by an external link (system camera, Universal Link, `click://`).
 * Token-bearing QR links (`/c/{uuid}?token=…&exp=…[&venue_id=…]`) keep the single-use token and venue
 * so the handshake redeems server-side exactly like the in-app scanner (iOS `ConnectionInvocation`).
 */
data class ConnectionInvocation(
    val userId: String,
    val token: String? = null,
    val exp: Long? = null,
    val venueId: String? = null,
)

/**
 * Routes Universal Links and custom URL schemes for `/c/{userId}` into the connection handshake flow.
 * Platform entry points (Android MainActivity) call [handleIncomingUrl]; [App] observes
 * [pendingConnection] for both cold and warm starts.
 */
object ConnectionDeepLinkRouter {
    private val _pendingConnection = MutableStateFlow<ConnectionInvocation?>(null)
    val pendingConnection: StateFlow<ConnectionInvocation?> = _pendingConnection.asStateFlow()

    /**
     * Parse a connection user id from a Universal Link or deep link URL.
     * Supports `https://joinclick.co/c/{uuid}`, legacy `/connect/{uuid}`, and `click://` variants.
     */
    fun parseConnectionUserId(url: String): String? = parseConnectionInvocation(url)?.userId

    /**
     * Parse the full invocation (user id plus optional token / expiry / venue) from [url].
     * Token payloads are parsed first; plain links fall back to user id only.
     */
    fun parseConnectionInvocation(url: String): ConnectionInvocation? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        trimmed.toTokenQrPayloadFromClickLinkOrNull()?.let { payload ->
            return ConnectionInvocation(
                userId = payload.userId,
                token = payload.token,
                exp = payload.exp,
                venueId = payload.venueId?.takeIf { it.isNotBlank() },
            )
        }
        val userId =
            parseQrPayload(trimmed)
                ?: trimmed.toUserIdFromClickUrl()?.takeIf { it.isNotBlank() }
                ?: return null
        // Token without an expiry (iOS accepts it too): still hand the token to server redemption.
        return ConnectionInvocation(
            userId = userId,
            token = trimmed.clickLinkTokenQueryValue(),
            exp = trimmed.clickLinkExpQueryValue(),
            venueId = trimmed.clickLinkVenueQueryValue(),
        )
    }

    /**
     * Queue a connection handshake for [url]. Returns true when the URL was recognized.
     */
    fun handleIncomingUrl(url: String): Boolean {
        val invocation = parseConnectionInvocation(url) ?: return false
        _pendingConnection.value = invocation
        return true
    }

    fun consume(): ConnectionInvocation? {
        val value = _pendingConnection.value
        _pendingConnection.value = null
        return value
    }
}
