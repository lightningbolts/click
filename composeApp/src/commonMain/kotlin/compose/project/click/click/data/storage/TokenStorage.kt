package compose.project.click.click.data.storage

interface TokenStorage {
    suspend fun saveTokens(jwt: String, refreshToken: String)
    suspend fun getJwt(): String?
    suspend fun getRefreshToken(): String?
    suspend fun clearTokens()
}

expect fun createTokenStorage(): TokenStorage

