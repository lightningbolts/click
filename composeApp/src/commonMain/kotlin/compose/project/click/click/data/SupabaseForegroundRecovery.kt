package compose.project.click.click.data

import compose.project.click.click.data.realtime.RealtimeCoordinator
import compose.project.click.click.data.repository.AuthRepository
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.data.storage.createTokenStorage
import compose.project.click.click.util.redactedRestMessage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.realtime

/**
 * Drops the Realtime WebSocket and re-authenticates after iOS/Android resume so Ktor does not sit
 * on stale TLS/TCP state until the app-level startup timeout fires.
 *
 * Always imports TokenStorage → refreshes → restarts auto-refresh → reconnects Realtime, then
 * forces [RealtimeCoordinator] channel rebuild so postgres/message channels are not stuck on an
 * expired JWT from before the refresh.
 */
object SupabaseForegroundRecovery {
    suspend fun recoverAfterBackground(
        client: SupabaseClient,
        tokenStorage: TokenStorage = createTokenStorage(),
        authRepository: AuthRepository = AuthRepository(tokenStorage),
    ) {
        runCatching { client.realtime.disconnect() }
        runCatching { SupabaseConfig.importStoredSessionWithoutRefresh(tokenStorage) }
        authRepository.refreshSession()
            .onSuccess {
                runCatching { client.auth.startAutoRefreshForCurrentSession() }
            }
            .onFailure { e ->
                println(
                    "SupabaseForegroundRecovery: refresh failed: ${e.redactedRestMessage()}",
                )
            }
        runCatching { client.realtime.connect() }
        // Tear down stale RealtimeCoordinator channels so the next ensureStarted() re-subscribes
        // with the refreshed JWT (jobs may still look "active" with a dead socket auth).
        runCatching { RealtimeCoordinator.stopAndAwait() }
    }
}
