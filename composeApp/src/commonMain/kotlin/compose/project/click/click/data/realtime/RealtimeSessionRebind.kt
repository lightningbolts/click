package compose.project.click.click.data.realtime // pragma: allowlist secret

import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.realtime

/**
 * Drops the Realtime WebSocket so the next [connect] authenticates with the current GoTrue JWT.
 * `connect()` is a no-op while a socket is already open — TestFlight/cold-start refreshes otherwise
 * keep talking on the expired connection (hub subscribe timeouts, unauthorized sends).
 */
internal suspend fun rebindRealtimeSocket() {
    val client = SupabaseConfig.client
    val userId =
        client.auth
            .currentUserOrNull()
            ?.id
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    runCatching { client.realtime.disconnect() }
    runCatching { client.realtime.connect() }
    runCatching { RealtimeCoordinator.stopAndAwait() }
    if (!userId.isNullOrBlank()) {
        runCatching { RealtimeCoordinator.ensureStarted(userId) }
    }
}
