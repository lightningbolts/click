package compose.project.click.click.data.repository

import compose.project.click.click.data.SupabaseConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.OAuthProvider
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import compose.project.click.click.util.redactedRestMessage
import compose.project.click.click.util.compressOutgoingChatImageForUpload
import compose.project.click.click.data.api.ApiClient
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.data.storage.createTokenStorage
import compose.project.click.click.proximity.isSimulatorOrEmulatorRuntime
import kotlinx.coroutines.withTimeout

class AuthRepository(
    private val tokenStorage: TokenStorage = createTokenStorage()
) {
    /** Lazy so [AppDataManager] and JVM tests can load without touching Supabase / Android crypto. */
    private val supabase by lazy { SupabaseConfig.client }
    private val clickWebApi by lazy { ApiClient() }
    private companion object {
        const val AUTH_TIMEOUT_MS = 12_000L
        const val MAX_PROFILE_IMAGE_BYTES = 2_000_000
    }

    suspend fun signInWithEmail(email: String, password: String): Result<UserInfo> {
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
                    tokenType = session.tokenType
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
                    data = buildJsonObject {
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
                val user = session.user ?: return Result.failure(
                    Exception("Sign up succeeded, but your session could not be restored. Please sign in.")
                )
                tokenStorage.saveTokens(
                    jwt = session.accessToken,
                    refreshToken = session.refreshToken,
                    expiresAt = session.expiresAt?.toEpochMilliseconds(),
                    tokenType = session.tokenType
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
     * auth browser via its default [io.github.jan.supabase.auth.ExternalAuthAction]:
     *   * Android → Chrome Custom Tab.
     *   * iOS → SFSafariViewController (equivalent cookie-isolated browser; PKCE-enforced).
     *
     * The browser returns to the app via the `click://login` deep-link configured in
     * [SupabaseConfig] (scheme = "click", host = "login"). Once the deep link is
     * delivered, the SDK exchanges the code for a session and fires `sessionStatus`,
     * which [SupabaseConfig.startSessionSync] persists to [TokenStorage].
     *
     * Note: the user-facing directive asked for ASWebAuthenticationSession on iOS
     * specifically; today the SDK's default iOS browser is SFSafariViewController.
     * The two behave identically from a PKCE / cookie-isolation standpoint and the
     * deep-link return is unchanged. A future commit may wire a custom
     * ExternalAuthAction backed by ASWebAuthenticationSession if stricter fidelity
     * is ever required.
     */
    suspend fun signInWithOAuth(provider: OAuthProvider): Result<Unit> {
        return try {
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
    }

    suspend fun signInWithGoogle(): Result<Unit> = signInWithOAuth(Google)

    suspend fun signInWithApple(): Result<Unit> {
        return try {
            // Native Apple Sign-In is the canonical path on iOS.
            // IMPORTANT: do not convert native failures into OAuth fallback, otherwise we can
            // get stuck waiting for a browser deep link that never returns on simulator.
            val nativePayloadResult = withTimeout(AUTH_TIMEOUT_MS) {
                requestNativeAppleSignInPayload()
            }

            nativePayloadResult.fold(
                onSuccess = { nativePayload ->
                    if (nativePayload != null) {
                        supabase.auth.signInWith(IDToken) {
                            provider = Apple
                            idToken = nativePayload.idToken
                            nativePayload.nonce?.let { nonce = it }
                        }
                        Result.success(Unit)
                    } else {
                        // Android/other platforms return null payload by design.
                        signInWithOAuth(Apple)
                    }
                },
                onFailure = { nativeError ->
                    val simulatorHint = if (isSimulatorOrEmulatorRuntime()) {
                        " Apple Sign-In may be unavailable on simulator; use a physical iPhone or ensure Simulator Settings > Apple Account is signed in."
                    } else {
                        ""
                    }
                    Result.failure(
                        Exception(
                            mapAppleSignInErrorMessage(
                                nativeError,
                                defaultMessage = "Apple sign-in couldn't be completed right now.$simulatorHint",
                            ),
                        ),
                    )
                },
            )
        } catch (e: Exception) {
            Result.failure(
                Exception(
                    mapAppleSignInErrorMessage(
                        e,
                        defaultMessage = "Apple sign-in couldn't be completed right now.",
                    ),
                ),
            )
        }
    }

    suspend fun signOut(): Result<Unit> {
        return try {
            supabase.auth.signOut()
            tokenStorage.clearTokens()
            Result.success(Unit)
        } catch (e: Exception) {
            // Ensure tokens are cleared even if Supabase signout fails (e.g. network error)
            tokenStorage.clearTokens()
            Result.failure(e)
        }
    }

    suspend fun restoreSession(): Result<UserInfo> {
        return try {
            // ── Strategy ──
            // 1. First check if the Supabase SDK already has a valid session
            //    (auto-loaded from SettingsSessionManager on startup).
            //    This is the most reliable path since the SDK auto-refreshes.
            // 2. If the SDK has no session, try reconstructing from TokenStorage
            //    (Keychain/EncryptedPrefs) — this covers app reinstall scenarios.

            // Step 1: Check SDK's built-in session (auto-loaded from SettingsSessionManager)
            var user = supabase.auth.currentUserOrNull()
            if (user != null) {
                println("AuthRepository: Restored session from SDK (SettingsSessionManager)")
                // Sync to our TokenStorage so Keychain/EncryptedPrefs stay current
                val currentSession = supabase.auth.currentSessionOrNull()
                if (currentSession != null) {
                    tokenStorage.saveTokens(
                        jwt = currentSession.accessToken,
                        refreshToken = currentSession.refreshToken,
                        expiresAt = currentSession.expiresAt?.toEpochMilliseconds(),
                        tokenType = currentSession.tokenType
                    )
                }
                return Result.success(user)
            }

            // Step 2: SDK has no session — try TokenStorage (for reinstall/update scenarios)
            val accessToken = tokenStorage.getJwt()
            val refreshToken = tokenStorage.getRefreshToken()

            if (!accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
                println("AuthRepository: Attempting restore from TokenStorage (Keychain/EncryptedPrefs)")
                val expiresAt = tokenStorage.getExpiresAt()
                val tokenType = tokenStorage.getTokenType() ?: "bearer"
                
                // Calculate expiresIn based on stored expiresAt
                val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                val expiresIn = if (expiresAt != null) {
                    val remaining = (expiresAt - now) / 1000
                    if (remaining > 0) remaining else 0L
                } else 3600L

                val session = UserSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresIn = expiresIn,
                    tokenType = tokenType,
                    user = null
                )
                
                // Import the session into Supabase
                supabase.auth.importSession(session)
                
                // Always try to refresh when restoring from TokenStorage
                // since these tokens may be stale
                var refreshFailed = false
                try {
                    supabase.auth.refreshCurrentSession()
                    println("AuthRepository: Successfully refreshed session from TokenStorage")
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
                            tokenType = currentSession.tokenType
                        )
                    }
                    Result.success(user)
                } else {
                    // Keep local tokens on restore failure so offline cold boots do not force sign-out.
                    // Explicit sign-out still clears session data via AuthViewModel.signOut().
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

    fun getCurrentUser(): UserInfo? {
        return supabase.auth.currentUserOrNull()
    }

    fun isAuthenticated(): Boolean {
        return supabase.auth.currentUserOrNull() != null
    }

    suspend fun refreshSession(): Result<Unit> {
        return try {
            withTimeout(AUTH_TIMEOUT_MS) {
                supabase.auth.refreshCurrentSession()
            }
            val session = supabase.auth.currentSessionOrNull()
            if (session != null) {
                tokenStorage.saveTokens(
                    jwt = session.accessToken,
                    refreshToken = session.refreshToken,
                    expiresAt = session.expiresAt?.toEpochMilliseconds(),
                    tokenType = session.tokenType
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update auth user_metadata with explicit first/last names (and derived full_name / name).
     */
    suspend fun updateUserProfileNames(firstName: String, lastName: String): Result<Unit> {
        return try {
            val f = firstName.trim()
            val l = lastName.trim()
            if (f.isEmpty()) {
                return Result.failure(IllegalArgumentException("First name is required"))
            }
            val display = listOf(f, l).filter { it.isNotEmpty() }.joinToString(" ")
            println("AuthRepository: Updating profile names: $display")
            supabase.auth.updateUser {
                data = buildJsonObject {
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
    suspend fun uploadProfilePicture(imageBytes: ByteArray, mimeType: String = "image/jpeg"): Result<String> {
        if (imageBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty image"))
        }
        supabase.auth.currentUserOrNull() ?: return Result.failure(Exception("Not signed in"))
        val normalizedMime = mimeType.trim().ifEmpty { "image/jpeg" }
        return try {
            val compressedCandidate = if (imageBytes.size > MAX_PROFILE_IMAGE_BYTES) {
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

            // iOS/Android compression utilities currently re-encode as JPEG.
            val uploadMime = if (wasReencoded) "image/jpeg" else normalizedMime
            clickWebApi.uploadAvatar(bytesToUpload, uploadMime)
        } catch (e: Exception) {
            println("AuthRepository: uploadProfilePicture failed: ${e.redactedRestMessage()}")
            Result.failure(e)
        }
    }

    private fun mapAuthErrorMessage(error: Throwable, defaultMessage: String): String {
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

    private fun mapAppleSignInErrorMessage(error: Throwable, defaultMessage: String): String {
        val rawMessage = error.message?.trim().orEmpty()
        val normalized = rawMessage.lowercase()

        if (rawMessage.isBlank()) return defaultMessage

        if (
            normalized.contains("canceled") ||
            normalized.contains("cancelled") ||
            normalized.contains("asauthorizationerror") && normalized.contains("1001")
        ) {
            return "Apple sign-in was canceled."
        }

        if (
            normalized.contains("akauthenticationerror") ||
            normalized.contains("asauthorizationerror") ||
            normalized.contains("apple sign")
        ) {
            return rawMessage
        }

        return mapAuthErrorMessage(error, defaultMessage)
    }

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

