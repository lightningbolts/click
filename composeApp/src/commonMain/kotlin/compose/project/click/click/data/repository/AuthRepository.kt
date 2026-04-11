package compose.project.click.click.data.repository

import compose.project.click.click.data.AVATARS_BUCKET
import compose.project.click.click.data.SupabaseConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import compose.project.click.click.util.redactedRestMessage
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.data.storage.createTokenStorage
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock

class AuthRepository(
    private val tokenStorage: TokenStorage = createTokenStorage()
) {
    /** Lazy so [AppDataManager] and JVM tests can load without touching Supabase / Android crypto. */
    private val supabase by lazy { SupabaseConfig.client }
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
                    println("AuthRepository: Failed to refresh session from TokenStorage: ${e.message}")
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
            println("Error restoring session: ${e.message}")
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
     * Uploads a JPEG (or other image) to the `avatars` bucket and sets [public.users.image] to the public URL.
     */
    suspend fun uploadProfilePicture(imageBytes: ByteArray): Result<String> {
        if (imageBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty image"))
        }
        if (imageBytes.size > MAX_PROFILE_IMAGE_BYTES) {
            return Result.failure(IllegalArgumentException("Image must be under 2 MB"))
        }
        val user = supabase.auth.currentUserOrNull()
            ?: return Result.failure(Exception("Not signed in"))
        val path = "${user.id}/${Clock.System.now().toEpochMilliseconds()}.jpg"
        return try {
            supabase.storage.from(AVATARS_BUCKET).upload(path, imageBytes) {
                upsert = true
            }
            val publicUrl = supabase.storage.from(AVATARS_BUCKET).publicUrl(path)
            supabase.from("users").update({
                set("image", publicUrl)
            }) {
                filter {
                    eq("id", user.id)
                }
            }
            Result.success(publicUrl)
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

            normalized.contains("network") ||
                normalized.contains("timeout") ||
                normalized.contains("timed out") ||
                normalized.contains("unable to resolve host") ||
                normalized.contains("offline") ||
                normalized.contains("socket") ||
                normalized.contains("connection") ->
                "You're offline or the network is unstable. Please try again when you're connected."

            rawMessage.isNotBlank() -> rawMessage
            else -> defaultMessage
        }
    }
}

