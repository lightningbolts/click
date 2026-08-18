package compose.project.click.click.data // pragma: allowlist secret

import compose.project.click.click.data.realtime.rebindRealtimeSocket // pragma: allowlist secret
import compose.project.click.click.data.repository.AuthRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
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
        // Expired SDK sessions must not overwrite TokenStorage; hydrate storage when empty/expired.
        runCatching { SupabaseConfig.importStoredSessionIfSdkEmpty(tokenStorage) }
        val refreshOk =
            authRepository
                .refreshSession(forceRefresh = true)
                .onSuccess {
                    runCatching { client.auth.startAutoRefreshForCurrentSession() }
                }.onFailure { e ->
                    println(
                        "SupabaseForegroundRecovery: refresh failed: ${e.redactedRestMessage()}",
                    )
                }.isSuccess
        if (!refreshOk) {
            // Do not reconnect Realtime with a dead JWT — callers pause sync / force re-login.
            return false
        }
        runCatching { rebindRealtimeSocket() }
        return true
    }
}
