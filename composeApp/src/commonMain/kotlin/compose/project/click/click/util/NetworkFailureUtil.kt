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
