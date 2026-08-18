package compose.project.click.click.data.auth // pragma: allowlist secret

import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.data.repository.AuthRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
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

        fun usable(
            token: String?,
            expiresAtMs: Long?,
        ): String? {
            val t = token?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val exp = expiresAtMs ?: jwtExpEpochMs(t)
            if (exp != null && exp <= now) return null
            return t
        }

        suspend fun persistAndMaybeRefresh(
            accessToken: String,
            refreshToken: String?,
            expiresAtMs: Long?,
            tokenType: String?,
        ): String? {
            runCatching {
                tokenStorage.saveTokens(
                    jwt = accessToken,
                    refreshToken = refreshToken.orEmpty(),
                    expiresAt = expiresAtMs,
                    tokenType = tokenType,
                )
            }
            val token = usable(accessToken, expiresAtMs)
            val exp = expiresAtMs ?: jwtExpEpochMs(accessToken)
            val needsRefresh =
                forceRefresh ||
                    token == null ||
                    exp == null ||
                    exp <= now + REFRESH_SKEW_MS
            if (!needsRefresh && token != null) return token
            authRepository
                .refreshSession(forceRefresh = forceRefresh || token == null)
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
            // Soft failure: keep previous usable token only if still unexpired *now*.
            // Refresh can take AUTH_TIMEOUT_MS, so the timestamp captured at method entry
            // can be stale; never return a JWT with unknown exp after a failed refresh.
            if (
                !forceRefresh &&
                token != null &&
                exp != null &&
                exp > Clock.System.now().toEpochMilliseconds()
            ) {
                return token
            }
            return null
        }

        val sdkSession = supabase.auth.currentSessionOrNull()
        if (sdkSession != null) {
            persistAndMaybeRefresh(
                accessToken = sdkSession.accessToken,
                refreshToken = sdkSession.refreshToken,
                expiresAtMs = sdkSession.expiresAt?.toEpochMilliseconds(),
                tokenType = sdkSession.tokenType,
            )?.let { return it }
        }

        // SDK empty — hydrate from TokenStorage. Skip network refresh when the stored
        // access token still has headroom so unit tests and offline writes do not block
        // on GoTrue (refresh still runs on forceRefresh, near-expiry, or 401 retry).
        runCatching { SupabaseConfig.importStoredSessionWithoutRefresh(tokenStorage) }
        val imported = supabase.auth.currentSessionOrNull()
        if (imported != null) {
            persistAndMaybeRefresh(
                accessToken = imported.accessToken,
                refreshToken = imported.refreshToken,
                expiresAtMs = imported.expiresAt?.toEpochMilliseconds(),
                tokenType = imported.tokenType,
            )?.let { return it }
        }

        val stored = tokenStorage.getJwt()?.trim()?.takeIf { it.isNotEmpty() }
        val storedExp = tokenStorage.getExpiresAt() ?: jwtExpEpochMs(stored)
        val storedUsable = usable(stored, storedExp)
        if (storedUsable != null && !forceRefresh) {
            val needsRefresh = storedExp != null && storedExp <= now + REFRESH_SKEW_MS
            if (!needsRefresh) return storedUsable
        }

        authRepository
            .refreshSession(forceRefresh = forceRefresh)
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

        return storedUsable
    }

    /** Best-effort JWT `exp` (seconds) → epoch ms; null if unparseable. */
    fun jwtExpEpochMs(jwt: String?): Long? {
        if (jwt.isNullOrBlank()) return null
        val parts = jwt.split('.')
        if (parts.size < 2) return null
        val payload =
            parts[1]
                .replace('-', '+')
                .replace('_', '/')
                .let { raw ->
                    val pad = (4 - raw.length % 4) % 4
                    raw + "=".repeat(pad)
                }
        return runCatching {
            val json = Json.parseToJsonElement(payload.decodeBase64ToString())
            val exp =
                (json as? JsonObject)
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
