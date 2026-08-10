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
 * Prefer live GoTrue session on resume; only import TokenStorage when SDK has no session.
 * Then refresh → restart auto-refresh → reconnect Realtime → rebuild RealtimeCoordinator channels.
 */
object SupabaseForegroundRecovery {
    suspend fun recoverAfterBackground(
        client: SupabaseClient,
        tokenStorage: TokenStorage = createTokenStorage(),
        authRepository: AuthRepository = AuthRepository(tokenStorage),
    ): Boolean {
        runCatching { client.realtime.disconnect() }
        // Only hydrate TokenStorage into GoTrue when the SDK has no session. Blind re-import
        // overwrites a good SettingsSessionManager refresh token and breaks chat until sign-out.
        if (client.auth.currentSessionOrNull() == null) {
            runCatching { SupabaseConfig.importStoredSessionWithoutRefresh(tokenStorage) }
        }
        val refreshOk = authRepository.refreshSession()
            .onSuccess {
                runCatching { client.auth.startAutoRefreshForCurrentSession() }
            }
            .onFailure { e ->
                println(
                    "SupabaseForegroundRecovery: refresh failed: ${e.redactedRestMessage()}",
                )
            }
            .isSuccess
        if (!refreshOk) {
            // Do not reconnect Realtime with a dead JWT — callers pause sync / force re-login.
            return false
        }
        runCatching { client.realtime.connect() }
        // Tear down stale RealtimeCoordinator channels so the next ensureStarted() re-subscribes
        // with the refreshed JWT (jobs may still look "active" with a dead socket auth).
        runCatching { RealtimeCoordinator.stopAndAwait() }
        return true
    }
}
