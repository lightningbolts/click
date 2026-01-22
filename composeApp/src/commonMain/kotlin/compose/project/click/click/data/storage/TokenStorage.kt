package compose.project.click.click.data.storage

interface TokenStorage {
    suspend fun saveTokens(jwt: String, refreshToken: String, expiresAt: Long? = null, tokenType: String? = null)
    suspend fun getJwt(): String?
    suspend fun getRefreshToken(): String?
    suspend fun getExpiresAt(): Long?
    suspend fun getTokenType(): String?
    suspend fun clearTokens()
}

expect fun createTokenStorage(): TokenStorage

