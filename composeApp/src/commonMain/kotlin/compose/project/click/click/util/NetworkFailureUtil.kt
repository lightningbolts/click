package compose.project.click.click.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

/**
 * True when a failure is likely transient connectivity (offline / unreachable host).
 * Used by offline-first refresh paths to preserve local SSOT instead of clearing UI state.
 */
fun Throwable.isOfflineNetworkFailure(): Boolean {
    if (this is TimeoutCancellationException) return true
    if (this is CancellationException) return false
    val name = this::class.simpleName.orEmpty()
    if (name.contains("HttpRequestTimeout", ignoreCase = true)) return true
    if (name.contains("ConnectTimeout", ignoreCase = true)) return true
    if (name.contains("UnresolvedAddress", ignoreCase = true)) return true
    if (name.contains("IOException", ignoreCase = true)) return true
    val message = redactedRestMessage().lowercase()
    return message.contains("timeout") ||
        message.contains("timed out") ||
        message.contains("network") ||
        message.contains("socket") ||
        message.contains("unable to resolve") ||
        message.contains("failed to connect") ||
        message.contains("unreachable") ||
        message.contains("offline") ||
        message.contains("connection reset") ||
        message.contains("connection refused") ||
        message.contains("no address associated") ||
        (message.contains("host") && message.contains("unreachable"))
}

/**
 * True when a refresh/session failure means credentials are dead (not a transient network blip).
 * Callers should clear auth tokens and force re-login rather than staying "logged in" with a
 * poisoned JWT that makes PostgREST/RLS return empty forever.
 *
 * Do **not** treat AuthRestException / AuthApiException by class name: GoTrue wraps refreshable
 * "JWT expired" in those types. Only dead refresh credentials force re-login.
 */
fun Throwable.isHardAuthFailure(): Boolean {
    if (isOfflineNetworkFailure()) return false
    val message = redactedRestMessage().lowercase()
    // Soft "JWT expired" / access-token expiry is refreshable — never treat as hard failure.
    if (message.contains("jwt expired") ||
        message.contains("access token expired") ||
        (message.contains("jwt") && message.contains("expired"))
    ) {
        return false
    }
    return message.contains("invalid refresh") ||
        message.contains("refresh token not found") ||
        message.contains("refresh_token_not_found") ||
        message.contains("session not found") ||
        message.contains("session_not_found") ||
        message.contains("user from sub claim in jwt does not exist") ||
        message.contains("invalid login credentials") ||
        (message.contains("refresh") && message.contains("invalid"))
}
