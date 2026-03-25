package compose.project.click.click.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.repository.AuthRepository
import compose.project.click.click.data.storage.TokenStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val userId: String, val email: String, val name: String? = null) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(
    private val tokenStorage: TokenStorage,
    private val authRepository: AuthRepository = AuthRepository(tokenStorage)
) : ViewModel() {
    var authState by mutableStateOf<AuthState>(AuthState.Loading)
        private set

    var isAuthenticated by mutableStateOf(false)
        private set

    init {
        // Start syncing SDK session refreshes → TokenStorage (Keychain/EncryptedPrefs).
        // This is the primary fix for "random logouts" — without this, the SDK
        // auto-refreshes its tokens but our Keychain/EncryptedPrefs storage goes stale.
        SupabaseConfig.startSessionSync(tokenStorage)

        checkAuthStatus()
        startBackgroundTokenRefresh()
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            try {
                // Try to restore session from storage
                val userResult = if (authRepository.isAuthenticated()) {
                    val user = authRepository.getCurrentUser()
                    if (user != null) Result.success(user) else Result.failure(Exception("No user"))
                } else {
                    authRepository.restoreSession()
                }

                userResult.fold(
                    onSuccess = { user ->
                        isAuthenticated = true
                        // Prefer full_name over name (full_name is updated by user, name is legacy)
                        val fullName = user.userMetadata?.get("full_name")?.toString()?.removeSurrounding("\"")
                        val legacyName = user.userMetadata?.get("name")?.toString()?.removeSurrounding("\"")
                        authState = AuthState.Success(
                            userId = user.id,
                            email = user.email ?: "",
                            name = fullName ?: legacyName
                        )
                    },
                    onFailure = {
                        isAuthenticated = false
                        authState = AuthState.Idle
                    }
                )
            } catch (e: Exception) {
                isAuthenticated = false
                authState = AuthState.Idle
            }
        }
    }

    /**
     * Background coroutine that proactively refreshes the session every 45 minutes.
     * Supabase tokens expire after ~1 hour by default. By refreshing at 45 min,
     * we ensure the user is never logged out during active app use.
     */
    private fun startBackgroundTokenRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(45 * 60 * 1000L) // 45 minutes
                if (isAuthenticated) {
                    try {
                        authRepository.refreshSession()
                        println("AuthViewModel: Background token refresh successful")
                    } catch (e: Exception) {
                        println("AuthViewModel: Background token refresh failed: ${e.message}")
                    }
                }
            }
        }
    }

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            try {
                authState = AuthState.Loading

                val result = authRepository.signInWithEmail(email, password)

                result.fold(
                    onSuccess = { user ->
                        isAuthenticated = true
                        val fullName = user.userMetadata?.get("full_name")?.toString()?.removeSurrounding("\"")
                        val legacyName = user.userMetadata?.get("name")?.toString()?.removeSurrounding("\"")
                        authState = AuthState.Success(
                            userId = user.id,
                            email = user.email ?: email,
                            name = fullName ?: legacyName
                        )
                        // Trigger data load for the newly logged-in user
                        AppDataManager.resetAndReload()
                    },
                    onFailure = { error ->
                        authState = AuthState.Error(error.message ?: "Failed to sign in")
                    }
                )
            } catch (e: Exception) {
                authState = AuthState.Error(e.message ?: "An error occurred during sign in")
            }
        }
    }

    fun signUpWithEmail(name: String, email: String, password: String) {
        viewModelScope.launch {
            try {
                authState = AuthState.Loading

                val result = authRepository.signUpWithEmail(email, password, name)

                result.fold(
                    onSuccess = { user ->
                        isAuthenticated = true
                        val fullName = user.userMetadata?.get("full_name")?.toString()?.removeSurrounding("\"")
                        val legacyName = user.userMetadata?.get("name")?.toString()?.removeSurrounding("\"")
                        authState = AuthState.Success(
                            userId = user.id,
                            email = user.email ?: email,
                            name = fullName ?: legacyName
                        )
                        // Trigger data load for the newly signed-up user
                        AppDataManager.resetAndReload()
                    },
                    onFailure = { error ->
                        authState = AuthState.Error(error.message ?: "Failed to create account")
                    }
                )
            } catch (e: Exception) {
                authState = AuthState.Error(e.message ?: "An error occurred during sign up")
            }
        }
    }

    suspend fun refreshTokenIfNeeded() {
        try {
            val result = authRepository.refreshSession()
            result.fold(
                onSuccess = {
                    // Session refreshed successfully
                },
                onFailure = {
                    // Refresh failed, require re-login
                    isAuthenticated = false
                    authState = AuthState.Idle
                }
            )
        } catch (e: Exception) {
            isAuthenticated = false
            authState = AuthState.Idle
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                AppDataManager.clearData()
                tokenStorage.clearSessionData()
                authRepository.signOut()
                isAuthenticated = false
                authState = AuthState.Idle
            } catch (e: Exception) {
                AppDataManager.clearData()
                runCatching { tokenStorage.clearSessionData() }
                isAuthenticated = false
                authState = AuthState.Idle
            }
        }
    }

    fun resetAuthState() {
        authState = AuthState.Idle
    }
}
