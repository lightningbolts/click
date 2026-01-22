package compose.project.click.click.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.repository.AuthRepository
import compose.project.click.click.data.storage.TokenStorage
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
    var authState by mutableStateOf<AuthState>(AuthState.Idle)
        private set

    var isAuthenticated by mutableStateOf(false)
        private set

    init {
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            try {
                // Try to restore session from storage if not already authenticated in memory
                val userResult = if (authRepository.isAuthenticated()) {
                    val user = authRepository.getCurrentUser()
                    if (user != null) Result.success(user) else Result.failure(Exception("No user"))
                } else {
                    authRepository.restoreSession()
                }

                userResult.fold(
                    onSuccess = { user ->
                        isAuthenticated = true
                        authState = AuthState.Success(
                            userId = user.id,
                            email = user.email ?: "",
                            name = user.userMetadata?.get("name")?.toString()?.removeSurrounding("\"")
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

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            try {
                authState = AuthState.Loading

                val result = authRepository.signInWithEmail(email, password)

                result.fold(
                    onSuccess = { user ->
                        isAuthenticated = true
                        authState = AuthState.Success(
                            userId = user.id,
                            email = user.email ?: email,
                            name = user.userMetadata?.get("name")?.toString()?.removeSurrounding("\"")
                        )
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
                        authState = AuthState.Success(
                            userId = user.id,
                            email = user.email ?: email,
                            name = user.userMetadata?.get("name")?.toString()?.removeSurrounding("\"")
                        )
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
                authRepository.signOut()
                // tokenStorage clearing is now handled in repository
                isAuthenticated = false
                authState = AuthState.Idle
            } catch (e: Exception) {
                // If repo logout fails, we still want to clear UI state
                isAuthenticated = false
                authState = AuthState.Idle
            }
        }
    }

    fun resetAuthState() {
        authState = AuthState.Idle
    }
}

