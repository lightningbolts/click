package compose.project.click.click.data.repository

import compose.project.click.click.data.SupabaseConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthRepository {
    private val supabase = SupabaseConfig.client

    suspend fun signInWithEmail(email: String, password: String): Result<UserInfo> {
        return try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }

            val user = supabase.auth.currentUserOrNull()
            if (user != null) {
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

            // Get the current session - it should be created even if email confirmation is pending
            val session = supabase.auth.currentSessionOrNull()
            val user = session?.user

            if (user != null) {
                Result.success(user)
            } else {
                // If no session but sign up succeeded, it means email confirmation is required
                // Create a temporary UserInfo-like result
                Result.failure(Exception("Sign up successful! Please check your email to confirm your account, then sign in."))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Sign up failed: ${e.message}"))
        }
    }

    suspend fun signOut(): Result<Unit> {
        return try {
            supabase.auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
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
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

