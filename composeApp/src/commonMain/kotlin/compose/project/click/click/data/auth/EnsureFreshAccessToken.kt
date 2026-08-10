package compose.project.click.click.data.auth

import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.repository.AuthRepository
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.data.storage.createTokenStorage
import compose.project.click.click.util.redactedRestMessage
import io.github.jan.supabase.auth.auth
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Shared access-token ensure for chat, presence, pending sync, Realtime, and click-web.
 *
 * Prefer live GoTrue session; never import [TokenStorage] over a live session (that overwrites
 * a good refresh token and causes "Refresh Token Not Found"). Rejects expired access JWTs —
 * callers must not send them to PostgREST / Realtime / click-web.
 */
object EnsureFreshAccessToken {
    /** Refresh when access token expires within this skew. */
    const val REFRESH_SKEW_MS = 90_000L

    suspend fun get(
        tokenStorage: TokenStorage = createTokenStorage(),
        authRepository: AuthRepository = AuthRepository(tokenStorage),
        forceRefresh: Boolean = false,
    ): String? {
        val now = Clock.System.now().toEpochMilliseconds()
        val supabase = SupabaseConfig.client

        fun usable(token: String?, expiresAtMs: Long?): String? {
            val t = token?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val exp = expiresAtMs ?: jwtExpEpochMs(t)
            if (exp != null && exp <= now) return null
            return t
        }

        val sdkSession = supabase.auth.currentSessionOrNull()
        if (sdkSession != null) {
            usable(
                sdkSession.accessToken,
                sdkSession.expiresAt?.toEpochMilliseconds(),
            )?.let { token ->
                runCatching {
                    tokenStorage.saveTokens(
                        jwt = sdkSession.accessToken,
                        refreshToken = sdkSession.refreshToken,
                        expiresAt = sdkSession.expiresAt?.toEpochMilliseconds(),
                        tokenType = sdkSession.tokenType,
                    )
                }
                val exp = sdkSession.expiresAt?.toEpochMilliseconds() ?: jwtExpEpochMs(token)
                val needsRefresh = forceRefresh ||
                    (exp != null && exp <= now + REFRESH_SKEW_MS)
                if (needsRefresh) {
                    authRepository.refreshSession()
                        .onFailure {
                            println(
                                "EnsureFreshAccessToken: refresh failed: ${it.redactedRestMessage()}",
                            )
                        }
                    val refreshed = supabase.auth.currentSessionOrNull()
                    usable(
                        refreshed?.accessToken,
                        refreshed?.expiresAt?.toEpochMilliseconds(),
                    )?.let { return it }
                    // Soft failure: keep previous usable token only if still unexpired.
                    if (!forceRefresh) return token.takeIf { usable(it, exp) != null }
                    return null
                }
                return token
            }
        }

        // SDK empty — hydrate from TokenStorage, then refresh once under SessionRefreshCoordinator.
        runCatching { SupabaseConfig.importStoredSessionWithoutRefresh(tokenStorage) }
        authRepository.refreshSession()
            .onFailure {
                println(
                    "EnsureFreshAccessToken: refresh-after-import failed: ${it.redactedRestMessage()}",
                )
            }

        val after = supabase.auth.currentSessionOrNull()
        if (after != null) {
            usable(
                after.accessToken,
                after.expiresAt?.toEpochMilliseconds(),
            )?.let { token ->
                runCatching {
                    tokenStorage.saveTokens(
                        jwt = after.accessToken,
                        refreshToken = after.refreshToken,
                        expiresAt = after.expiresAt?.toEpochMilliseconds(),
                        tokenType = after.tokenType,
                    )
                }
                return token
            }
        }

        // Last resort: TokenStorage only if JWT is not expired.
        val stored = tokenStorage.getJwt()?.trim()?.takeIf { it.isNotEmpty() }
        return stored?.takeIf { jwt ->
            val exp = tokenStorage.getExpiresAt() ?: jwtExpEpochMs(jwt)
            exp == null || exp > now
        }
    }

    /** Best-effort JWT `exp` (seconds) → epoch ms; null if unparseable. */
    fun jwtExpEpochMs(jwt: String?): Long? {
        if (jwt.isNullOrBlank()) return null
        val parts = jwt.split('.')
        if (parts.size < 2) return null
        val payload = parts[1]
            .replace('-', '+')
            .replace('_', '/')
            .let { raw ->
                val pad = (4 - raw.length % 4) % 4
                raw + "=".repeat(pad)
            }
        return runCatching {
            val json = Json.parseToJsonElement(payload.decodeBase64ToString())
            val exp = (json as? JsonObject)
                ?.get("exp")
                ?.let { el ->
                    when (el) {
                        is JsonPrimitive ->
                            el.content.toLongOrNull() ?: el.content.toDoubleOrNull()?.toLong()
                        else -> null
                    }
                }
            exp?.times(1000L)
        }.getOrNull()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun String.decodeBase64ToString(): String {
        val bytes = Base64.decode(this)
        return bytes.decodeToString()
    }
}
