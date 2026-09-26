@file:Suppress("ktlint:standard:max-line-length")

package compose.project.click.click.data.repository // pragma: allowlist secret

import compose.project.click.click.auth.LocalSessionCache // pragma: allowlist secret
import compose.project.click.click.auth.LocalSessionIdentity // pragma: allowlist secret
import compose.project.click.click.auth.SessionHydrationPolicy // pragma: allowlist secret
import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.auth.EnsureFreshAccessToken // pragma: allowlist secret
import compose.project.click.click.data.auth.SessionRefreshCoordinator // pragma: allowlist secret
import compose.project.click.click.data.auth.SessionResumeGate // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.util.compressOutgoingChatImageForUpload // pragma: allowlist secret
import compose.project.click.click.util.isHardAuthFailure // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.OAuthProvider
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthRepository(
    private val tokenStorage: TokenStorage = createTokenStorage(),
) {
    /** Lazy so [AppDataManager] and JVM tests can load without touching Supabase / Android crypto. */
    private val supabase by lazy { SupabaseConfig.client }
    private val clickWebApi by lazy { ApiClient() }

    private companion object {
        const val AUTH_TIMEOUT_MS = 12_000L

        /** Idle / cold-resume refresh can wait longer than interactive sign-in. */
        const val AUTH_RESUME_TIMEOUT_MS = 25_000L
        const val AUTH_INTERACTIVE_TIMEOUT_MS = 120_000L
        const val MAX_PROFILE_IMAGE_BYTES = 2_000_000
    }

    suspend fun signInWithEmail(
        email: String,
        password: String,
    ): Result<UserInfo> {
        return try {
            withTimeout(AUTH_TIMEOUT_MS) {
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
            }

            val session = supabase.auth.currentSessionOrNull()

            if (session != null) {
                val user = session.user ?: return Result.failure(Exception("Failed to get user info after sign in"))
                tokenStorage.saveTokens(
                    jwt = session.accessToken,
                    refreshToken = session.refreshToken,
                    expiresAt = session.expiresAt?.toEpochMilliseconds(),
                    tokenType = session.tokenType,
                )
                Result.success(user)
            } else {
                Result.failure(Exception("Failed to get user info after sign in"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapAuthErrorMessage(e, defaultMessage = "Couldn't sign in. Check your credentials and try again.")))
        }
    }

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        birthdayIso: String,
    ): Result<UserInfo> {
        return try {
            val f = firstName.trim()
            val l = lastName.trim()
            val b = birthdayIso.trim()
            val display = listOf(f, l).filter { it.isNotEmpty() }.joinToString(" ")
            // Sign up with Supabase
            withTimeout(AUTH_TIMEOUT_MS) {
                supabase.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                    data =
                        buildJsonObject {
                            put("first_name", f)
                            put("last_name", l)
                            put("birthday", b)
                            put("full_name", display.ifEmpty { f })
                            put("name", display.ifEmpty { f })
                        }
                }
            }

            // Get the current session
            val session = supabase.auth.currentSessionOrNull()

            if (session != null) {
                val user =
                    session.user ?: return Result.failure(
                        Exception("Sign up succeeded, but your session could not be restored. Please sign in."),
                    )
                tokenStorage.saveTokens(
                    jwt = session.accessToken,
                    refreshToken = session.refreshToken,
                    expiresAt = session.expiresAt?.toEpochMilliseconds(),
                    tokenType = session.tokenType,
                )
                Result.success(user)
            } else {
                Result.failure(Exception("Sign up successful! Please check your email to confirm your account, then sign in."))
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapAuthErrorMessage(e, defaultMessage = "Couldn't create your account right now. Please try again.")))
        }
    }

    /**
     * Kick off a Supabase-hosted OAuth flow for the given provider (Phase 2 — C16).
     *
     * The Supabase KMP SDK handles the PKCE handshake internally and dispatches the
     * auth browser via its default [io.github.jan.supabase.auth.ExternalAuthAction]
     * (Chrome Custom Tab on Android).
     *
     * The browser returns to the app via the `click://login` deep-link configured in
     * [SupabaseConfig] (scheme = "click", host = "login"). Once the deep link is
     * delivered, the SDK exchanges the code for a session and fires `sessionStatus`,
     * which [SupabaseConfig.startSessionSync] persists to [TokenStorage].
     */
    suspend fun signInWithOAuth(provider: OAuthProvider): Result<Unit> =
        try {
            supabase.auth.signInWith(provider)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(
                Exception(
                    mapAuthErrorMessage(
                        e,
                        defaultMessage = "We couldn't open the sign-in browser. Please try again.",
                    ),
                ),
            )
        }

    suspend fun signInWithGoogle(): Result<Unit> =
        try {
            val nativePayloadResult =
                withTimeout(AUTH_INTERACTIVE_TIMEOUT_MS) {
                    requestNativeGoogleSignInPayload()
                }

            nativePayloadResult.fold(
                onSuccess = { nativePayload ->
                    if (nativePayload != null) {
                        runCatching {
                            supabase.auth.signInWith(IDToken) {
                                provider = Google
                                idToken = nativePayload.idToken
                                nativePayload.accessToken?.let { accessToken = it }
                                nativePayload.nonce?.let { nonce = it }
                            }
                        }.fold(
                            onSuccess = {
                                val session = supabase.auth.currentSessionOrNull()
                                if (session != null) {
                                    tokenStorage.saveTokens(
                                        jwt = session.accessToken,
                                        refreshToken = session.refreshToken,
                                        expiresAt = session.expiresAt?.toEpochMilliseconds(),
                                        tokenType = session.tokenType,
                                    )
                                }
                                Result.success(Unit)
                            },
                            onFailure = { idTokenError ->
                                Result.failure(
                                    Exception(
                                        mapGoogleSignInErrorMessage(
                                            idTokenError,
                                            defaultMessage = "Google sign-in couldn't be completed right now.",
                                        ),
                                    ),
                                )
                            },
                        )
                    } else {
                        signInWithOAuth(Google)
                    }
                },
                onFailure = { nativeError ->
                    Result.failure(
                        Exception(
                            mapGoogleSignInErrorMessage(
                                nativeError,
                                defaultMessage = "Google sign-in couldn't be completed right now.",
                            ),
                        ),
                    )
                },
            )
        } catch (e: Exception) {
            Result.failure(
                Exception(
                    mapGoogleSignInErrorMessage(
                        e,
                        defaultMessage = "Google sign-in couldn't be completed right now.",
                    ),
                ),
            )
        }

    /** Apple sign-in via the Supabase-hosted web OAuth flow. */
    suspend fun signInWithApple(): Result<Unit> = signInWithOAuth(Apple)

    suspend fun signOut(): Result<Unit> =
        run {
            SessionRefreshCoordinator.clearSuccessfulRefresh()
            signOutWithCleanup(
                remoteSignOut = { supabase.auth.signOut() },
                clearSdkSession = { supabase.auth.clearSession() },
                clearTokenStorage = { tokenStorage.clearTokens() },
            )
        }

    suspend fun restoreSession(): Result<UserInfo> {
        return try {
            // Fast path: valid local tokens — import into SDK without awaiting network refresh.
            LocalSessionCache.read(tokenStorage)?.let { identity ->
                SupabaseConfig.importStoredSessionWithoutRefresh(tokenStorage)
                supabase.auth.currentUserOrNull()?.let { user ->
                    println("AuthRepository: Restored session from local cache (offline-capable)")
                    return Result.success(user)
                }
                println("AuthRepository: Admitting offline session for ${identity.userId} from local cache")
                return Result.success(offlineUserInfoFromIdentity(identity))
            }

            // ── Strategy ──
            // 1. First check if the Supabase SDK already has a valid session
            //    (auto-loaded from SettingsSessionManager on startup).
            //    This is the most reliable path since the SDK auto-refreshes.
            // 2. If the SDK has no session, try reconstructing from TokenStorage
            //    (EncryptedPrefs) — this covers app reinstall scenarios.

            // Step 1: Check SDK's built-in session (auto-loaded from SettingsSessionManager)
            var user = supabase.auth.currentUserOrNull()
            if (user != null) {
                println("AuthRepository: Restored session from SDK (SettingsSessionManager)")
                // Sync to our TokenStorage so EncryptedPrefs stay current
                val currentSession = supabase.auth.currentSessionOrNull()
                if (currentSession != null) {
                    tokenStorage.saveTokens(
                        jwt = currentSession.accessToken,
                        refreshToken = currentSession.refreshToken,
                        expiresAt = currentSession.expiresAt?.toEpochMilliseconds(),
                        tokenType = currentSession.tokenType,
                    )
                }
                return Result.success(user)
            }

            // Step 2: SDK has no session — try TokenStorage (for reinstall/update scenarios)
            val accessToken = tokenStorage.getJwt()
            val refreshToken = tokenStorage.getRefreshToken()

            if (!accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
                println("AuthRepository: Attempting restore from TokenStorage (EncryptedPrefs)")
                val expiresAt = tokenStorage.getExpiresAt()
                val tokenType = tokenStorage.getTokenType() ?: "bearer"

                // Calculate expiresIn based on stored expiresAt
                val now =
                    kotlinx.datetime.Clock.System
                        .now()
                        .toEpochMilliseconds()
                val expiresIn =
                    if (expiresAt != null) {
                        val remaining = (expiresAt - now) / 1000
                        if (remaining > 0) remaining else 0L
                    } else {
                        3600L
                    }

                val identity = LocalSessionCache.parseIdentityFromJwt(accessToken)
                val sessionUser = identity?.let { offlineUserInfoFromIdentity(it) }
                val session =
                    UserSession(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresIn = expiresIn,
                        tokenType = tokenType,
                        user = sessionUser,
                    )

                // Import the session into Supabase
                supabase.auth.importSession(session)

                // Refresh when online; offline boots must not block on this call.
                var refreshFailed = false
                try {
                    withTimeout(AUTH_TIMEOUT_MS) {
                        val refreshResult = refreshSession()
                        if (refreshResult.isFailure) {
                            refreshFailed = true
                        }
                    }
                    if (!refreshFailed) {
                        println("AuthRepository: Successfully refreshed session from TokenStorage")
                    }
                } catch (e: Exception) {
                    refreshFailed = true
                    println("AuthRepository: Failed to refresh session from TokenStorage: ${e.redactedRestMessage()}")
                }

                user = supabase.auth.currentUserOrNull()

                if (user != null) {
                    // Update stored tokens with newly refreshed ones
                    val currentSession = supabase.auth.currentSessionOrNull()
                    if (currentSession != null) {
                        tokenStorage.saveTokens(
                            jwt = currentSession.accessToken,
                            refreshToken = currentSession.refreshToken,
                            expiresAt = currentSession.expiresAt?.toEpochMilliseconds(),
                            tokenType = currentSession.tokenType,
                        )
                    }
                    Result.success(user)
                } else {
                    // Offline or refresh failed — admit from local JWT identity without clearing tokens.
                    LocalSessionCache.read(tokenStorage)?.let { identity ->
                        println("AuthRepository: Admitting offline session for ${identity.userId} without SDK user hydration")
                        return Result.success(offlineUserInfoFromIdentity(identity))
                    }
                    if (refreshFailed) {
                        println("AuthRepository: Session refresh failed; preserving local tokens for offline recovery")
                    } else {
                        println("AuthRepository: Session user unavailable after import; preserving local tokens")
                    }
                    Result.failure(Exception("Session could not be restored right now"))
                }
            } else {
                Result.failure(Exception("No saved session found"))
            }
        } catch (e: Exception) {
            println("Error restoring session: ${e.redactedRestMessage()}")
            Result.failure(e)
        }
    }

    fun getCurrentUser(): UserInfo? = supabase.auth.currentUserOrNull()

    fun isAuthenticated(): Boolean {
        if (supabase.auth.currentUserOrNull() != null) return true
        return false
    }

    suspend fun hasValidLocalSession(): Boolean = LocalSessionCache.read(tokenStorage) != null

    suspend fun refreshSession(forceRefresh: Boolean = false): Result<Unit> {
        val credential = supabase.auth.currentSessionOrNull()?.refreshToken ?: tokenStorage.getRefreshToken()
        return SessionRefreshCoordinator.singleFlightRefresh(credential = credential) {
            try {
                // Never skip GoTrue just because `exp` is still in the future. TestFlight updates
                // and dual-store drift (SettingsSessionManager vs TokenStorage) keep access JWTs
                // that click-web / Realtime reject as 401 until a real refreshCurrentSession().
                // Coalesce only non-forced callers that race in right after a successful refresh
                // (e.g. EnsureFreshAccessToken + chat send). Forced 401 retries always hit GoTrue
                // unless another refresh is already in-flight (single-flight).
                if (
                    !forceRefresh &&
                    accessTokenHasRefreshHeadroom() &&
                    SessionRefreshCoordinator.recentlyRefreshed()
                ) {
                    persistCurrentSessionToTokenStorage()
                    return@singleFlightRefresh Result.success(Unit)
                }
                hydrateGoTrueFromTokenStorageIfNeeded()
                if (accessTokenHasRefreshHeadroom() && !forceRefresh) {
                    persistCurrentSessionToTokenStorage()
                    SessionRefreshCoordinator.markSuccessfulRefresh()
                    SessionResumeGate.markCompleted()
                    return@singleFlightRefresh Result.success(Unit)
                }
                val timeoutMs = if (forceRefresh) AUTH_RESUME_TIMEOUT_MS else AUTH_TIMEOUT_MS
                withTimeout(timeoutMs) {
                    supabase.auth.refreshCurrentSession()
                }
                persistCurrentSessionToTokenStorage()
                SessionRefreshCoordinator.markSuccessfulRefresh()
                SessionResumeGate.markCompleted()
                Result.success(Unit)
            } catch (e: Exception) {
                SessionResumeGate.markCompleted()
                // Lost a refresh-token rotation race: the SDK (or another client) may already
                // hold the new access JWT. Never import TokenStorage here — that replays the
                // burned refresh token ("Already Used").
                if (e.isHardAuthFailure()) {
                    val existing = supabase.auth.currentSessionOrNull()
                    if (
                        EnsureFreshAccessToken.isAccessTokenFresh(existing?.accessToken)
                    ) {
                        persistCurrentSessionToTokenStorage()
                        SessionRefreshCoordinator.markSuccessfulRefresh()
                        return@singleFlightRefresh Result.success(Unit)
                    }
                }
                Result.failure(e)
            }
        }
    }

    private suspend fun accessTokenHasRefreshHeadroom(): Boolean {
        if (shouldHydrateFromTokenStorage()) return false
        val existing = supabase.auth.currentSessionOrNull() ?: return false
        val existingExp = existing.expiresAt?.toEpochMilliseconds() ?: return false
        val now = Clock.System.now().toEpochMilliseconds()
        val userOk =
            supabase.auth
                .currentUserOrNull()
                ?.id
                ?.isNotBlank() == true
        return userOk && existingExp > now + 120_000L
    }

    /**
     * SettingsSessionManager can keep an older access token that still has wall-clock headroom
     * after TestFlight while TokenStorage already holds the rotated refresh token (or vice versa).
     * Import storage when the SDK session is missing/expired/near-expiry, or storage is newer.
     */
    private suspend fun shouldHydrateFromTokenStorage(): Boolean {
        val existing = supabase.auth.currentSessionOrNull()
        return SessionHydrationPolicy.shouldImportStoredSession(
            sdkHasSession = existing != null,
            sdkRefresh = existing?.refreshToken,
            storedRefresh = tokenStorage.getRefreshToken(),
            sdkAccessExpMs = existing?.expiresAt?.toEpochMilliseconds(),
            storedAccessExpMs =
                tokenStorage.getExpiresAt()
                    ?: EnsureFreshAccessToken.jwtExpEpochMs(tokenStorage.getJwt()),
        )
    }

    private suspend fun hydrateGoTrueFromTokenStorageIfNeeded() {
        if (shouldHydrateFromTokenStorage()) {
            runCatching { SupabaseConfig.importStoredSessionWithoutRefresh(tokenStorage) }
        }
    }

    private suspend fun persistCurrentSessionToTokenStorage() {
        val session = supabase.auth.currentSessionOrNull() ?: return
        if (
            !SessionHydrationPolicy.shouldSyncSdkTokensToStorage(
                storedRefresh = tokenStorage.getRefreshToken(),
                sdkRefresh = session.refreshToken,
            ) &&
            tokenStorage.getJwt()?.trim() == session.accessToken
        ) {
            return
        }
        tokenStorage.saveTokens(
            jwt = session.accessToken,
            refreshToken = session.refreshToken,
            expiresAt = session.expiresAt?.toEpochMilliseconds(),
            tokenType = session.tokenType,
        )
    }

    /**
     * Update auth user_metadata with explicit first/last names (and derived full_name / name).
     */
    suspend fun updateUserProfileNames(
        firstName: String,
        lastName: String,
    ): Result<Unit> {
        return try {
            val f = firstName.trim()
            val l = lastName.trim()
            if (f.isEmpty()) {
                return Result.failure(IllegalArgumentException("First name is required"))
            }
            val display = listOf(f, l).filter { it.isNotEmpty() }.joinToString(" ")
            println("AuthRepository: Updating profile names: $display")
            supabase.auth.updateUser {
                data =
                    buildJsonObject {
                        put("first_name", JsonPrimitive(f))
                        put("last_name", JsonPrimitive(l))
                        put("full_name", JsonPrimitive(display))
                        put("name", JsonPrimitive(display))
                    }
            }
            println("AuthRepository: Successfully updated user profile names")
            Result.success(Unit)
        } catch (e: Exception) {
            println("AuthRepository: Error updating user profile names (redacted): ${e.redactedRestMessage()}")
            Result.failure(e)
        }
    }

    /** Splits a single display string into first/last and calls [updateUserProfileNames]. */
    suspend fun updateUserMetadata(fullName: String): Result<Unit> {
        val trimmed = fullName.trim()
        val spaceIdx = trimmed.indexOf(' ')
        val first = if (spaceIdx < 0) trimmed else trimmed.take(spaceIdx).trim()
        val last = if (spaceIdx < 0) "" else trimmed.substring(spaceIdx + 1).trim()
        return updateUserProfileNames(first, last)
    }

    /**
     * Uploads a profile image via click-web [POST /api/user/avatar] (thin client); updates [public.users.image] server-side.
     */
    suspend fun uploadProfilePicture(
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
    ): Result<String> {
        if (imageBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty image"))
        }
        supabase.auth.currentUserOrNull() ?: return Result.failure(Exception("Not signed in"))
        val normalizedMime = mimeType.trim().ifEmpty { "image/jpeg" }
        return try {
            val compressedCandidate =
                if (imageBytes.size > MAX_PROFILE_IMAGE_BYTES) {
                    compressOutgoingChatImageForUpload(imageBytes, normalizedMime)
                } else {
                    imageBytes
                }
            val wasReencoded = compressedCandidate !== imageBytes
            val bytesToUpload = compressedCandidate
            if (bytesToUpload.size > MAX_PROFILE_IMAGE_BYTES) {
                return Result.failure(
                    IllegalArgumentException("Image is too large to upload. Please choose a smaller photo."),
                )
            }

            // Android compression utilities currently re-encode as JPEG.
            val uploadMime = if (wasReencoded) "image/jpeg" else normalizedMime
            clickWebApi.uploadAvatar(bytesToUpload, uploadMime)
        } catch (e: Exception) {
            println("AuthRepository: uploadProfilePicture failed: ${e.redactedRestMessage()}")
            Result.failure(e)
        }
    }

    private fun mapAuthErrorMessage(
        error: Throwable,
        defaultMessage: String,
    ): String {
        val rawMessage = error.message?.trim().orEmpty()
        val normalized = rawMessage.lowercase()

        return when {
            normalized.contains("already registered") ||
                normalized.contains("user already registered") ||
                normalized.contains("email already") ||
                normalized.contains("duplicate key") ||
                normalized.contains("already exists") ->
                "That email is already in use. Try signing in instead."

            normalized.contains("invalid login credentials") ||
                normalized.contains("invalid credentials") ->
                "That email or password is incorrect."

            isLikelyNetworkErrorMessage(normalized) ->
                "You're offline or the network is unstable. Please try again when you're connected."

            rawMessage.isNotBlank() -> rawMessage
            else -> defaultMessage
        }
    }

    private fun isAppleAudienceMismatchError(normalizedMessage: String): Boolean {
        if (normalizedMessage.isBlank()) return false
        val unacceptableAudience = "unacceptable audience in id_token"
        val audienceClaim = "audience in id_token"
        return unacceptableAudience in normalizedMessage || audienceClaim in normalizedMessage
    }

    private fun isGoogleAudienceMismatchError(normalizedMessage: String): Boolean {
        if (normalizedMessage.isBlank()) return false
        return normalizedMessage.contains("invalid_audience") ||
            normalizedMessage.contains("same project") ||
            isAppleAudienceMismatchError(normalizedMessage)
    }

    private fun mapGoogleSignInErrorMessage(
        error: Throwable,
        defaultMessage: String,
    ): String {
        val rawMessage = error.message?.trim().orEmpty()
        val normalized = rawMessage.lowercase()
        if (normalized.contains("cancel")) {
            return "Google sign-in was canceled."
        }
        if (isGoogleAudienceMismatchError(normalized)) {
            return "Google Sign-In client mismatch: Android and web OAuth clients must be in the same Google Cloud project."
        }
        if (normalized.contains("nonce") && normalized.contains("id_token")) {
            return "Google sign-in nonce verification failed. Rebuild the app, or enable Skip nonce check in Supabase → Auth → Google."
        }
        return mapAuthErrorMessage(error, defaultMessage = defaultMessage)
    }

    private fun offlineUserInfoFromIdentity(identity: LocalSessionIdentity): UserInfo =
        UserInfo(
            id = identity.userId,
            aud = "authenticated",
            email = identity.email.takeIf { it.isNotBlank() },
            userMetadata =
                buildJsonObject {
                    identity.name?.let { put("name", it) }
                },
        )

    private fun isLikelyNetworkErrorMessage(normalizedMessage: String): Boolean {
        if (normalizedMessage.isBlank()) return false

        if (normalizedMessage.contains("network")) return true
        if (normalizedMessage.contains("offline")) return true
        if (normalizedMessage.contains("unable to resolve host")) return true
        if (normalizedMessage.contains("socket")) return true
        if (normalizedMessage.contains("dns")) return true
        if (normalizedMessage.contains("host unreachable")) return true
        if (normalizedMessage.contains("connection reset")) return true
        if (normalizedMessage.contains("connection refused")) return true
        if (normalizedMessage.contains("connection timed out")) return true
        if (normalizedMessage.contains("timed out")) return true
        if (normalizedMessage.contains("timeout")) return true

        return false
    }
}
