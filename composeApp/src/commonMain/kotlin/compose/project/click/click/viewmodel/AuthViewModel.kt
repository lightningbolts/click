package compose.project.click.click.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.api.ApiClient
import compose.project.click.click.data.storage.TokenStorage
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val userId: String, val email: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(
    private val apiClient: ApiClient = ApiClient(),
    private val tokenStorage: TokenStorage
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
                val jwt = tokenStorage.getJwt()
                val refreshToken = tokenStorage.getRefreshToken()

                if (jwt != null && refreshToken != null) {
                    // TODO: Validate JWT expiration and refresh if needed
                    isAuthenticated = true
                    // Extract email from JWT if needed
                    authState = AuthState.Success("", "")
                } else {
                    isAuthenticated = false
                    authState = AuthState.Idle
                }
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

                val result = apiClient.login(email, password)

                result.fold(
                    onSuccess = { response ->
                        tokenStorage.saveTokens(response.jwt, response.refresh)
                        isAuthenticated = true
                        authState = AuthState.Success(
                            userId = response.user?.email ?: "",
                            email = response.user?.email ?: email
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

                val result = apiClient.signUp(email, password, name)

                result.fold(
                    onSuccess = { response ->
                        tokenStorage.saveTokens(response.jwt, response.refresh)
                        isAuthenticated = true
                        authState = AuthState.Success(
                            userId = response.user?.email ?: "",
                            email = response.user?.email ?: email
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

    fun signInWithGoogle(googleToken: String) {
        viewModelScope.launch {
            try {
                authState = AuthState.Loading

                val result = apiClient.authenticateWithGoogle(googleToken)

                result.fold(
                    onSuccess = { response ->
                        tokenStorage.saveTokens(response.jwt, response.refresh)
                        isAuthenticated = true
                        authState = AuthState.Success(
                            userId = response.user?.email ?: "",
                            email = response.user?.email ?: ""
                        )
                    },
                    onFailure = { error ->
                        authState = AuthState.Error(error.message ?: "Failed to sign in with Google")
                    }
                )
            } catch (e: Exception) {
                authState = AuthState.Error(e.message ?: "An error occurred during Google sign in")
            }
        }
    }

    suspend fun refreshTokenIfNeeded() {
        try {
            val refreshToken = tokenStorage.getRefreshToken() ?: return

            val result = apiClient.refreshToken(refreshToken)
            result.fold(
                onSuccess = { newJwt ->
                    val currentRefreshToken = tokenStorage.getRefreshToken() ?: ""
                    tokenStorage.saveTokens(newJwt, currentRefreshToken)
                },
                onFailure = {
                    // Refresh failed, clear tokens and require re-login
                    tokenStorage.clearTokens()
                    isAuthenticated = false
                    authState = AuthState.Idle
                }
            )
        } catch (e: Exception) {
            // Refresh failed, clear tokens
            tokenStorage.clearTokens()
            isAuthenticated = false
            authState = AuthState.Idle
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                val refreshToken = tokenStorage.getRefreshToken()
                if (refreshToken != null) {
                    apiClient.logout(refreshToken)
                }
                tokenStorage.clearTokens()
                isAuthenticated = false
                authState = AuthState.Idle
            } catch (e: Exception) {
                // Even if logout fails on server, clear local tokens
                tokenStorage.clearTokens()
                isAuthenticated = false
                authState = AuthState.Idle
            }
        }
    }

    fun resetAuthState() {
        authState = AuthState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        apiClient.close()
    }
}

