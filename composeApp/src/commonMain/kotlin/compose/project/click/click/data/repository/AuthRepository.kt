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
                    refreshToken = session.refreshToken
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
                    refreshToken = session.refreshToken
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
                val session = UserSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresIn = 3600,
                    tokenType = "bearer",
                    user = null
                )
                supabase.auth.importSession(session)
                val user = supabase.auth.currentUserOrNull()
                if (user != null) {
                    Result.success(user)
                } else {
                    Result.failure(Exception("Session expired"))
                }
            } else {
                Result.failure(Exception("No tokens found"))
            }
        } catch (e: Exception) {
            tokenStorage.clearTokens()
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
                    refreshToken = session.refreshToken
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

