package compose.project.click.click.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.realtime

/**
 * Drops the Realtime WebSocket and re-authenticates after iOS/Android resume so Ktor does not sit
 * on stale TLS/TCP state until the app-level startup timeout fires.
 */
object SupabaseForegroundRecovery {
    suspend fun recoverAfterBackground(client: SupabaseClient) {
        runCatching { client.realtime.disconnect() }
        runCatching { client.auth.refreshCurrentSession() }
        runCatching { client.realtime.connect() }
    }
}
