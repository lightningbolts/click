package compose.project.click.click.data.realtime // pragma: allowlist secret

import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.withTimeoutOrNull

const val REALTIME_SUBSCRIBE_TIMEOUT_MS = 8_000L

/**
 * Subscribe with a hard deadline. Expired Realtime JWTs otherwise stall
 * `blockUntilSubscribed = true` indefinitely (hub chat pulsing logo).
 */
internal suspend fun RealtimeChannel.subscribeWithTimeout(timeoutMs: Long = REALTIME_SUBSCRIBE_TIMEOUT_MS): Boolean =
    withTimeoutOrNull(timeoutMs) {
        subscribe(blockUntilSubscribed = true)
        true
    } ?: run {
        println("Realtime: channel subscribe timed out after ${timeoutMs}ms")
        false
    }
