package compose.project.click.click.auth

import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.viewmodel.AuthState

/**
 * Immediate offline boot resolver used by [compose.project.click.click.viewmodel.AuthViewModel]
 * before any network-backed session restore runs.
 */
internal object AuthBootFastPath {
    suspend fun resolveLoggedInState(tokenStorage: TokenStorage): AuthState.Success? {
        val identity = LocalSessionCache.read(tokenStorage) ?: return null
        return AuthState.Success(
            userId = identity.userId,
            email = identity.email,
            name = identity.name,
        )
    }
}
