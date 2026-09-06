package compose.project.click.click.data.auth // pragma: allowlist secret

import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.auth.SessionHydrationPolicy // pragma: allowlist secret
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
 * Prefer the live GoTrue session; import [TokenStorage] over it only when the canonical dual-store
 * policy confirms storage contains a later rotation. Rejects expired access JWTs — callers must
 * not send them to PostgREST / Realtime / click-web.
 */
object EnsureFreshAccessToken {
    /** Minimal live-session view used to keep token selection unit-testable. */
    data class SessionSnapshot(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtMs: Long?,
        val tokenType: String?,
    )

    /** Refresh when access token expires within this skew. */
    const val REFRESH_SKEW_MS = 90_000L

    suspend fun get(
        tokenStorage: TokenStorage = createTokenStorage(),
        authRepository: AuthRepository = AuthRepository(tokenStorage),
        forceRefresh: Boolean = false,
        sdkSessionProvider: () -> SessionSnapshot? = {
            SupabaseConfig.client.auth.currentSessionOrNull()?.let { session ->
                SessionSnapshot(
                    accessToken = session.accessToken,
                    refreshToken = session.refreshToken,
                    expiresAtMs = session.expiresAt?.toEpochMilliseconds(),
                    tokenType = session.tokenType,
                )
            }
        },
        storedSessionImporter: suspend (TokenStorage) -> Boolean = {
            SupabaseConfig.importStoredSessionWithoutRefresh(it)
        },
    ): String? {
        // At most one GoTrue refresh per get() — persist + import + after-import used to
        // fire 2–3 refreshSession(force) calls, which turns one rate-limit into a storm.
        var refreshResult: Result<Unit>? = null

        suspend fun refreshOnce(force: Boolean): Result<Unit> {
            val alreadyCoolingDown = SessionRefreshCoordinator.recentlyFailed()
            refreshResult?.let { return it }
            val result = authRepository.refreshSession(forceRefresh = force)
            refreshResult = result
            if (!alreadyCoolingDown) {
                result.onFailure {
                    println("EnsureFreshAccessToken: refresh failed: ${it.redactedRestMessage()}")
                }
            }
            return result
        }

        if (!SessionResumeGate.isCompleted()) {
            refreshOnce(force = true)
        }
        val now = Clock.System.now().toEpochMilliseconds()

        fun usable(
            token: String?,
            expiresAtMs: Long?,
        ): String? {
            val t = token?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val exp = expiresAtMs ?: jwtExpEpochMs(t)
            if (exp != null && exp <= now) return null
            return t
        }

        // Read the live GoTrue session first. TokenStorage is a mirror and may lag behind a
        // refresh-token rotation; returning its JWT before this check can send an old token.
        var sdkSession = sdkSessionProvider()
        var stored = tokenStorage.getJwt()?.trim()?.takeIf { it.isNotEmpty() }
        var storedExp = tokenStorage.getExpiresAt() ?: jwtExpEpochMs(stored)

        var storageWonLaterRotation = false
        if (sdkSession != null) {
            storageWonLaterRotation =
                SessionHydrationPolicy.shouldImportStoredSession(
                    sdkHasSession = true,
                    sdkRefresh = sdkSession.refreshToken,
                    storedRefresh = tokenStorage.getRefreshToken(),
                    sdkAccessExpMs = sdkSession.expiresAtMs,
                    storedAccessExpMs = storedExp,
                )
            if (!forceRefresh && !storageWonLaterRotation) {
                val live = usable(sdkSession.accessToken, sdkSession.expiresAtMs)
                val liveExp = sdkSession.expiresAtMs ?: jwtExpEpochMs(sdkSession.accessToken)
                if (live != null && liveExp != null && liveExp > now + REFRESH_SKEW_MS) {
                    runCatching {
                        tokenStorage.saveTokens(
                            jwt = sdkSession.accessToken,
                            refreshToken = sdkSession.refreshToken.orEmpty(),
                            expiresAt = sdkSession.expiresAtMs,
                            tokenType = sdkSession.tokenType,
                        )
                    }
                    return live
                }
            }
        } else if (!forceRefresh) {
            // SDK empty — retain the offline fast path, but only after the live-session check.
            val storedOk = usable(stored, storedExp)
            if (storedOk != null && storedExp != null && storedExp > now + REFRESH_SKEW_MS) {
                return storedOk
            }
        }

        val supabase by lazy { SupabaseConfig.client }

        // Preserve the existing dual-store rule for the exceptional case where storage has a
        // demonstrably later refresh-token rotation. Import before persisting the SDK snapshot,
        // otherwise the older live snapshot would overwrite the newer stored credentials.
        val importedLaterStorage =
            if (storageWonLaterRotation) {
                runCatching {
                    storedSessionImporter(tokenStorage)
                }.getOrDefault(false)
            } else {
                false
            }
        if (storageWonLaterRotation && !importedLaterStorage) {
            sdkSession = sdkSessionProvider()
            stored = tokenStorage.getJwt()?.trim()?.takeIf { it.isNotEmpty() }
            storedExp = tokenStorage.getExpiresAt() ?: jwtExpEpochMs(stored)
            val storageStillWon =
                sdkSession != null &&
                    SessionHydrationPolicy.shouldImportStoredSession(
                        sdkHasSession = true,
                        sdkRefresh = sdkSession?.refreshToken,
                        storedRefresh = tokenStorage.getRefreshToken(),
                        sdkAccessExpMs = sdkSession?.expiresAtMs,
                        storedAccessExpMs = storedExp,
                    )
            if (sdkSession != null && !storageStillWon) {
                storageWonLaterRotation = false
            } else {
                val storedWithHeadroom = usable(stored, storedExp)
                if (
                    !forceRefresh &&
                    storedWithHeadroom != null &&
                    storedExp != null &&
                    storedExp > now + REFRESH_SKEW_MS
                ) {
                    return storedWithHeadroom
                }
                return null
            }
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
            refreshOnce(force = forceRefresh || token == null)
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

        val liveSession =
            if (storageWonLaterRotation) {
                supabase.auth.currentSessionOrNull()?.let { session ->
                    SessionSnapshot(
                        accessToken = session.accessToken,
                        refreshToken = session.refreshToken,
                        expiresAtMs = session.expiresAt?.toEpochMilliseconds(),
                        tokenType = session.tokenType,
                    )
                }
            } else {
                sdkSession ?: supabase.auth.currentSessionOrNull()?.let { session ->
                    SessionSnapshot(
                        accessToken = session.accessToken,
                        refreshToken = session.refreshToken,
                        expiresAtMs = session.expiresAt?.toEpochMilliseconds(),
                        tokenType = session.tokenType,
                    )
                }
            }
        if (liveSession != null) {
            persistAndMaybeRefresh(
                accessToken = liveSession.accessToken,
                refreshToken = liveSession.refreshToken,
                expiresAtMs = liveSession.expiresAtMs,
                tokenType = liveSession.tokenType,
            )?.let { return it }
        }

        // SDK empty — hydrate from TokenStorage. Skip network refresh when the stored
        // access token still has headroom so unit tests and offline writes do not block
        // on GoTrue (refresh still runs on forceRefresh, near-expiry, or 401 retry).
        runCatching { storedSessionImporter(tokenStorage) }
        val imported = supabase.auth.currentSessionOrNull()
        if (imported != null) {
            persistAndMaybeRefresh(
                accessToken = imported.accessToken,
                refreshToken = imported.refreshToken,
                expiresAtMs = imported.expiresAt?.toEpochMilliseconds(),
                tokenType = imported.tokenType,
            )?.let { return it }
        }

        val storedAfterImport = tokenStorage.getJwt()?.trim()?.takeIf { it.isNotEmpty() }
        val storedExpAfterImport =
            tokenStorage.getExpiresAt() ?: jwtExpEpochMs(storedAfterImport)
        val storedUsable = usable(storedAfterImport, storedExpAfterImport)
        if (storedUsable != null && !forceRefresh) {
            val needsRefresh =
                storedExpAfterImport != null && storedExpAfterImport <= now + REFRESH_SKEW_MS
            if (!needsRefresh) return storedUsable
        }

        if (refreshResult == null) {
            refreshOnce(force = forceRefresh).onFailure {
                println(
                    "EnsureFreshAccessToken: refresh-after-import failed: ${it.redactedRestMessage()}",
                )
            }
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

        return null
    }

    fun sdkAccessIsFresh(): Boolean {
        val token =
            runCatching {
                SupabaseConfig.client.auth.currentSessionOrNull()?.accessToken
            }.getOrNull()
        return isAccessTokenFresh(token)
    }

    /** True when [jwt] has a parseable `exp` strictly in the future. */
    fun isAccessTokenFresh(
        jwt: String?,
        nowMs: Long = Clock.System.now().toEpochMilliseconds(),
    ): Boolean {
        val t = jwt?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val exp = jwtExpEpochMs(t) ?: return false
        return exp > nowMs
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
