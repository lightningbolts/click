package compose.project.click.click.data

import compose.project.click.click.data.repository.AuthRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/**
 * Shared click-web bearer readiness for Home bookmarks, map engagement, and beacon drops.
 * Restores/refreshes the Supabase session before BFF calls on cold start.
 */
object ClickWebAuthCoordinator {
    suspend fun ensureReady(authRepository: AuthRepository = AuthRepository()): Boolean {
        val existingToken = SupabaseConfig.client.auth.currentSessionOrNull()?.accessToken?.trim()
        if (!existingToken.isNullOrEmpty()) return true
        if (SupabaseConfig.client.auth.currentSessionOrNull()?.accessToken.isNullOrBlank()) {
            authRepository.restoreSession()
        }
        authRepository.refreshSession()
        return awaitSession(timeoutMs = 20_000L)
    }

    private suspend fun awaitSession(timeoutMs: Long): Boolean {
        return try {
            withTimeout(timeoutMs) {
                while (true) {
                    val token = SupabaseConfig.client.auth.currentSessionOrNull()?.accessToken?.trim()
                    if (!token.isNullOrEmpty()) return@withTimeout true
                    delay(100)
                }
                @Suppress("UNREACHABLE_CODE")
                false
            }
        } catch (_: TimeoutCancellationException) {
            false
        }
    }
}
