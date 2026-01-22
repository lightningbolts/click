package compose.project.click.click.data.repository

import compose.project.click.click.data.SupabaseConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.data.storage.createTokenStorage

class AuthRepository(
    private val tokenStorage: TokenStorage = createTokenStorage()
) {
    private val supabase = SupabaseConfig.client

    suspend fun signInWithEmail(email: String, password: String): Result<UserInfo> {
        return try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }

            val session = supabase.auth.currentSessionOrNull()
            val user = session?.user

            if (user != null && session != null) {
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
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmail(email: String, password: String, name: String): Result<UserInfo> {
        return try {
            // Sign up with Supabase
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
                data = buildJsonObject {
                    put("name", name)
                }
            }

            // Get the current session
            val session = supabase.auth.currentSessionOrNull()
            val user = session?.user

            if (user != null && session != null) {
                tokenStorage.saveTokens(
                    jwt = session.accessToken,
                    refreshToken = session.refreshToken,
                    expiresAt = session.expiresAt?.toEpochMilliseconds(),
                    tokenType = session.tokenType
                )
                Result.success(user)
            } else if (user != null) {
                // User created but no session (e.g. confirm email), strictly shouldn't happen with implicit login unless configured otherwise
                Result.success(user)
            } else {
                Result.failure(Exception("Sign up successful! Please check your email to confirm your account, then sign in."))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Sign up failed: ${e.message}"))
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
            val accessToken = tokenStorage.getJwt()
            val refreshToken = tokenStorage.getRefreshToken()

            if (!accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
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
                
                // If the session is expired, try to refresh it
                var user = supabase.auth.currentUserOrNull()
                if (user == null || (expiresAt != null && now >= expiresAt)) {
                    try {
                        supabase.auth.refreshCurrentSession()
                        user = supabase.auth.currentUserOrNull()
                    } catch (e: Exception) {
                        println("Failed to refresh session: ${e.message}")
                    }
                }

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
                    Result.failure(Exception("Session expired and could not be refreshed"))
                }
            } else {
                // If no tokens in TokenStorage, check if Supabase already has a session (from SettingsSessionManager)
                val user = supabase.auth.currentUserOrNull()
                if (user != null) {
                    Result.success(user)
                } else {
                    Result.failure(Exception("No saved session found"))
                }
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
            supabase.auth.refreshCurrentSession()
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
}

