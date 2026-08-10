package compose.project.click.click.data.api

import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.repository.AuthRepository
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.data.storage.createTokenStorage
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.http.Url
import kotlinx.datetime.Clock

/**
 * Ktor bearer auth for click-web BFF routes — shared by [ApiClient] and [ChatApiClient].
 * Single-flight refresh via [AuthRepository] → [SessionRefreshCoordinator].
 */
internal fun HttpClientConfig<*>.installClickWebBearerAuth(
    tokenStorage: TokenStorage = createTokenStorage(),
) {
    val authOrigin = ApiConfig.CLICK_WEB_BASE_URL.trimEnd('/')
    val authHost = Url(authOrigin).host
    install(Auth) {
        bearer {
            loadTokens {
                val session = SupabaseConfig.client.auth.currentSessionOrNull()
                if (session != null) {
                    val access = session.accessToken
                    if (access.isNotBlank()) {
                        return@loadTokens BearerTokens(access, session.refreshToken.orEmpty())
                    }
                }
                val stored = tokenStorage.getJwt()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@loadTokens null
                val refresh = tokenStorage.getRefreshToken()?.trim().orEmpty()
                BearerTokens(stored, refresh)
            }
            refreshTokens {
                runCatching { AuthRepository(tokenStorage).refreshSession() }
                val session = SupabaseConfig.client.auth.currentSessionOrNull()
                if (session != null) {
                    val access = session.accessToken
                    if (access.isNotBlank()) {
                        return@refreshTokens BearerTokens(access, session.refreshToken.orEmpty())
                    }
                }
                val stored = tokenStorage.getJwt()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@refreshTokens null
                val refresh = tokenStorage.getRefreshToken()?.trim().orEmpty()
                BearerTokens(stored, refresh)
            }
            sendWithoutRequest { request ->
                request.url.host == authHost
            }
        }
    }
}

/**
 * Hydrates GoTrue from disk, refreshes when near expiry, and returns a bearer-ready access token.
 * Call before click-web writes when Ktor's bearer plugin may not have loaded tokens yet.
 */
internal suspend fun resolveClickWebAccessToken(
    tokenStorage: TokenStorage = createTokenStorage(),
): String? {
    if (SupabaseConfig.client.auth.currentSessionOrNull() == null) {
        runCatching { SupabaseConfig.importStoredSessionWithoutRefresh(tokenStorage) }
    }
    val authRepository = AuthRepository(tokenStorage)
    val now = Clock.System.now().toEpochMilliseconds()
    val session = SupabaseConfig.client.auth.currentSessionOrNull()
    val exp = session?.expiresAt?.toEpochMilliseconds()
    val needsRefresh = session == null || (exp != null && exp <= now + 60_000L)
    if (needsRefresh) {
        authRepository.refreshSession()
    }
    return SupabaseConfig.client.auth.currentSessionOrNull()?.accessToken
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: tokenStorage.getJwt()?.trim()?.takeIf { it.isNotEmpty() }
}

internal fun clickWebBearerHeader(rawToken: String): String {
    val t = rawToken.trim()
    return if (t.startsWith("Bearer ", ignoreCase = true)) t else "Bearer $t"
}
