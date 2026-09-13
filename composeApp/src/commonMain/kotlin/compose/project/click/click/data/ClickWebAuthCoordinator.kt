package compose.project.click.click.data // pragma: allowlist secret

import compose.project.click.click.data.auth.EnsureFreshAccessToken // pragma: allowlist secret
import compose.project.click.click.data.repository.AuthRepository // pragma: allowlist secret

/**
 * Shared click-web bearer readiness for Home bookmarks, map engagement, and beacon drops.
 * Restores/refreshes the Supabase session before BFF calls on cold start.
 */
object ClickWebAuthCoordinator {
    suspend fun ensureReady(authRepository: AuthRepository = AuthRepository()): Boolean {
        // Do not force-refresh: Home / map poll this on every composition. Forced GoTrue
        // hits trip "Request rate limit reached", after which every JWT-backed call fails.
        val token = EnsureFreshAccessToken.get(authRepository = authRepository)
        return !token.isNullOrBlank()
    }
}
