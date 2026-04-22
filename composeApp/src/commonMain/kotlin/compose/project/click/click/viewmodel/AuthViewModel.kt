package compose.project.click.click.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.displayNameFromMetadata
import compose.project.click.click.data.repository.AuthRepository
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.util.redactedRestMessage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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
        observeOAuthCompletion()
    }

    /**
     * OAuth profile hydration (birthday / first name) is enforced in [compose.project.click.click.App]
     * once [AppDataManager] loads [public.users] — see [compose.project.click.click.data.models.isPublicUserProfileIncomplete].
     *
     * Reflect deep-link-driven OAuth completion into the UI state machine (Phase 2 — C16).
     *
     * When the PKCE callback (`click://login`) is delivered to the app, supabase-kt
     * exchanges the code for a session and transitions [sessionStatus] to
     * [SessionStatus.Authenticated]. We watch for that transition and flip
     * [authState] so the login screen dismisses and downstream data loads.
     *
     * We intentionally only react while we're currently in [AuthState.Loading] from
     * an OAuth launch — other state transitions are owned by the email/password
     * flow and by [checkAuthStatus] at cold boot.
     */
    private fun observeOAuthCompletion() {
        viewModelScope.launch {
            SupabaseConfig.client.auth.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) {
                    val user = status.session.user
                    if (user != null && !isAuthenticated) {
                        isAuthenticated = true
                        authState = AuthState.Success(
                            userId = user.id,
                            email = user.email ?: "",
                            name = user.displayNameFromMetadata(),
                        )
                        AppDataManager.resetAndReload()
                    }
                }
            }
        }
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
                        authState = AuthState.Success(
                            userId = user.id,
                            email = user.email ?: "",
                            name = user.displayNameFromMetadata()
                        )
                    },
                    onFailure = { error ->
                        val restoredOffline = restoreOfflineSessionIfPossible(error)
                        if (!restoredOffline) {
                            isAuthenticated = false
                            authState = AuthState.Idle
                        }
                    }
                )
            } catch (e: Exception) {
                val restoredOffline = restoreOfflineSessionIfPossible(e)
                if (!restoredOffline) {
                    isAuthenticated = false
                    authState = AuthState.Idle
                }
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
                        println("AuthViewModel: Background token refresh failed: ${e.redactedRestMessage()}")
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
                        authState = AuthState.Success(
                            userId = user.id,
                            email = user.email ?: email,
                            name = user.displayNameFromMetadata()
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

    /**
     * Begin a Google OAuth sign-in. The UI should show a loading state until either
     * [authState] flips to [AuthState.Success] (deep-link callback completed the
     * PKCE handshake) or [AuthState.Error] (browser launch failed / user cancelled).
     *
     * Completion is driven by [SupabaseConfig.startSessionSync], which observes
     * `sessionStatus` changes triggered by the deep-link → PKCE code exchange.
     */
    fun signInWithGoogle() {
        launchOAuthSignIn(providerLabel = "Google") { authRepository.signInWithGoogle() }
    }

    /**
     * Begin an Apple OAuth sign-in. See [signInWithGoogle] for deep-link completion
     * semantics — the flow is identical; only the upstream IdP differs.
     */
    fun signInWithApple() {
        viewModelScope.launch {
            authState = AuthState.Loading
            var browserOpenedAwaitingDeepLink = false
            try {
                val result = authRepository.signInWithApple()
                result.fold(
                    onSuccess = {
                        // Native Apple ID-token sign-in or browser OAuth launch both transition
                        // through Supabase session callbacks; keep Loading while awaiting completion.
                        browserOpenedAwaitingDeepLink = true
                    },
                    onFailure = { error ->
                        authState = AuthState.Error(
                            error.message ?: "Could not start Apple sign-in right now.",
                        )
                    },
                )
            } catch (e: Exception) {
                println("AuthViewModel: Apple sign-in failed: ${e.redactedRestMessage()}")
                authState = AuthState.Error(
                    e.message ?: "Could not start Apple sign-in right now.",
                )
            } finally {
                // Equivalent to clearing loading state when startup failed before auth callback.
                if (!browserOpenedAwaitingDeepLink && authState is AuthState.Loading) {
                    authState = AuthState.Idle
                }
            }
        }
    }

    private fun launchOAuthSignIn(providerLabel: String, launch: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            authState = AuthState.Loading
            var browserOpenedAwaitingDeepLink = false
            try {
                launch().fold(
                    onSuccess = {
                        // The browser is open. The final AuthState.Success flip happens when
                        // the deep link returns and `sessionStatus` transitions to
                        // Authenticated. `checkAuthStatus()` is not re-invoked here to avoid
                        // racing the session manager.
                        browserOpenedAwaitingDeepLink = true
                    },
                    onFailure = { error ->
                        authState = AuthState.Error(
                            error.message ?: "Could not start $providerLabel sign-in right now.",
                        )
                    },
                )
            } catch (e: Exception) {
                authState = AuthState.Error(
                    e.message ?: "Could not start $providerLabel sign-in right now.",
                )
            } finally {
                // Never leave the login UI spinning if the flow failed to open the browser
                // or threw before we are waiting on the OAuth deep link.
                if (!browserOpenedAwaitingDeepLink && authState is AuthState.Loading) {
                    authState = AuthState.Idle
                }
            }
        }
    }

    fun signUpWithEmail(
        firstName: String,
        lastName: String,
        birthdayIso: String,
        email: String,
        password: String,
        avatarBytes: ByteArray? = null,
        avatarMime: String? = null,
    ) {
        viewModelScope.launch {
            try {
                authState = AuthState.Loading

                val result = authRepository.signUpWithEmail(
                    email = email,
                    password = password,
                    firstName = firstName,
                    lastName = lastName,
                    birthdayIso = birthdayIso,
                )

                result.fold(
                    onSuccess = { user ->
                        fun finishSuccess() {
                            isAuthenticated = true
                            authState = AuthState.Success(
                                userId = user.id,
                                email = user.email ?: email,
                                name = user.displayNameFromMetadata()
                            )
                            AppDataManager.resetAndReload()
                        }

                        val bytes = avatarBytes
                        if (bytes != null && bytes.isNotEmpty()) {
                            val mime = avatarMime?.trim()?.takeIf { it.isNotEmpty() } ?: "image/jpeg"
                            authRepository.uploadProfilePicture(bytes, mime).fold(
                                onSuccess = { finishSuccess() },
                                onFailure = { uploadErr ->
                                    finishSuccess()
                                    AppDataManager.postTransientUserMessage(
                                        uploadErr.message?.lines()?.firstOrNull()?.take(200)
                                            ?: "Could not upload profile photo. You can add one later in Settings.",
                                    )
                                },
                            )
                        } else {
                            finishSuccess()
                        }
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
                onFailure = { error ->
                    val keptOffline = restoreOfflineSessionIfPossible(error)
                    if (!keptOffline) {
                        // Refresh failed for a non-network reason, require re-login
                        isAuthenticated = false
                        authState = AuthState.Idle
                    }
                }
            )
        } catch (e: Exception) {
            val keptOffline = restoreOfflineSessionIfPossible(e)
            if (!keptOffline) {
                isAuthenticated = false
                authState = AuthState.Idle
            }
        }
    }

    private suspend fun restoreOfflineSessionIfPossible(error: Throwable?): Boolean {
        if (!isLikelyNetworkFailure(error)) return false

        val jwt = tokenStorage.getJwt()
        val refreshToken = tokenStorage.getRefreshToken()
        if (jwt.isNullOrBlank() || refreshToken.isNullOrBlank()) return false

        val identity = parseCachedIdentityFromJwt(jwt) ?: return false

        isAuthenticated = true
        authState = AuthState.Success(
            userId = identity.userId,
            email = identity.email,
            name = identity.name,
        )
        println("AuthViewModel: Using cached offline auth state from persisted tokens")
        return true
    }

    private fun isLikelyNetworkFailure(error: Throwable?): Boolean {
        if (error == null) return false
        var cursor: Throwable? = error
        while (cursor != null) {
            val message = cursor.message?.lowercase().orEmpty()
            if (
                message.contains("network") ||
                message.contains("offline") ||
                message.contains("no network") ||
                message.contains("no internet") ||
                message.contains("timeout") ||
                message.contains("timed out") ||
                message.contains("unable to resolve host") ||
                message.contains("socket") ||
                message.contains("connect") ||
                message.contains("dns") ||
                message.contains("unreachable")
            ) {
                return true
            }
            cursor = cursor.cause
        }
        return false
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun parseCachedIdentityFromJwt(jwt: String): CachedIdentity? {
        return runCatching {
            val payload = jwt.split('.')
                .getOrNull(1)
                ?.replace('-', '+')
                ?.replace('_', '/')
                ?.let { segment ->
                    val padding = (4 - (segment.length % 4)) % 4
                    segment + "=".repeat(padding)
                }
                ?: return null

            val json = Base64.decode(payload).decodeToString()
            val claims = Json.parseToJsonElement(json).jsonObject

            val userId = claims["sub"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return null
            val email = claims["email"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val name = claims["full_name"]?.jsonPrimitive?.contentOrNull
                ?: claims["name"]?.jsonPrimitive?.contentOrNull

            CachedIdentity(userId = userId, email = email, name = name)
        }.getOrNull()
    }

    private data class CachedIdentity(
        val userId: String,
        val email: String,
        val name: String?,
    )

    fun signOut() {
        viewModelScope.launch {
            try {
                authState = AuthState.Loading
                AppDataManager.clearData()
                tokenStorage.clearSessionData()
                authRepository.signOut()
                isAuthenticated = false
                authState = AuthState.Idle
            } catch (e: Exception) {
                authState = AuthState.Loading
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
