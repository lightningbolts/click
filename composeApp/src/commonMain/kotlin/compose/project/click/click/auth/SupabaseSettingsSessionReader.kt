package compose.project.click.click.auth // pragma: allowlist secret

import compose.project.click.click.data.createSupabaseAuthSettings // pragma: allowlist secret
import compose.project.click.click.data.displayNameFromMetadata // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json

/**
 * Reads the GoTrue session JSON persisted by [io.github.jan.supabase.auth.SettingsSessionManager]
 * without initializing the Supabase client (avoids blocking network refresh on cold boot).
 */
internal object SupabaseSettingsSessionReader {
    private const val SESSION_SETTINGS_KEY = "session"

    private val sessionJson =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            isLenient = true
        }

    suspend fun readIdentity(): LocalSessionIdentity? {
        val raw = createSupabaseAuthSettings().getStringOrNull(SESSION_SETTINGS_KEY)?.trim().orEmpty()
        if (raw.isEmpty()) return null

        val session = runCatching { sessionJson.decodeFromString<UserSession>(raw) }.getOrNull() ?: return null
        val accessToken = session.accessToken.trim().takeIf { it.isNotEmpty() } ?: return null
        val refreshToken = session.refreshToken.trim().takeIf { it.isNotEmpty() } ?: return null

        val fromJwt = LocalSessionCache.parseIdentityFromJwt(accessToken)
        val user = session.user
        val userId = user?.id?.takeIf { it.isNotBlank() } ?: fromJwt?.userId ?: return null
        val email = user?.email?.takeIf { it.isNotBlank() } ?: fromJwt?.email.orEmpty()
        val name = user?.displayNameFromMetadata()?.takeIf { it.isNotBlank() } ?: fromJwt?.name
        val expiresAtEpochMs =
            session.expiresAt.toEpochMilliseconds().takeIf { it > 0L }
                ?: fromJwt?.expiresAtEpochMs

        return LocalSessionIdentity(
            userId = userId,
            email = email,
            name = name,
            expiresAtEpochMs = expiresAtEpochMs,
        ).takeIf {
            LocalSessionCache.isUsableForOfflineBoot(
                expiresAtEpochMs = expiresAtEpochMs,
                hasRefreshToken = refreshToken.isNotEmpty(),
            )
        }
    }

    /** Copies SDK session tokens into [TokenStorage] when missing or the refresh token differs. */
    suspend fun syncTokensToStorageIfMissing(tokenStorage: TokenStorage) {
        val raw = createSupabaseAuthSettings().getStringOrNull(SESSION_SETTINGS_KEY)?.trim().orEmpty()
        if (raw.isEmpty()) return

        val session = runCatching { sessionJson.decodeFromString<UserSession>(raw) }.getOrNull() ?: return
        val accessToken = session.accessToken.trim().takeIf { it.isNotEmpty() } ?: return
        val refreshToken = session.refreshToken.trim().takeIf { it.isNotEmpty() } ?: return
        val storedRefresh = tokenStorage.getRefreshToken()?.trim().orEmpty()
        if (storedRefresh.isNotEmpty() && storedRefresh == refreshToken) return

        tokenStorage.saveTokens(
            jwt = accessToken,
            refreshToken = refreshToken,
            expiresAt = session.expiresAt.toEpochMilliseconds().takeIf { it > 0L },
            tokenType = session.tokenType,
        )
    }
}
