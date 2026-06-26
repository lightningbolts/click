package compose.project.click.click.auth

import compose.project.click.click.data.storage.TokenStorage
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Reads a usable offline session identity from persisted tokens without network I/O.
 */
data class LocalSessionIdentity(
    val userId: String,
    val email: String,
    val name: String?,
    val expiresAtEpochMs: Long?,
)

object LocalSessionCache {
    private const val EXPIRY_SKEW_MS = 60_000L

    suspend fun read(tokenStorage: TokenStorage, nowEpochMs: Long = Clock.System.now().toEpochMilliseconds()): LocalSessionIdentity? {
        val jwt = tokenStorage.getJwt()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val refreshToken = tokenStorage.getRefreshToken()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (refreshToken.isEmpty()) return null

        val identity = parseIdentityFromJwt(jwt) ?: return null
        val expiresAt = tokenStorage.getExpiresAt() ?: identity.expiresAtEpochMs
        if (!isUsable(expiresAt, nowEpochMs)) return null

        return identity.copy(expiresAtEpochMs = expiresAt)
    }

    fun isUsable(expiresAtEpochMs: Long?, nowEpochMs: Long): Boolean {
        if (expiresAtEpochMs == null) return true
        return expiresAtEpochMs > nowEpochMs + EXPIRY_SKEW_MS
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun parseIdentityFromJwt(jwt: String): LocalSessionIdentity? {
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
            val expSec = claims["exp"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            val expiresAtEpochMs = expSec?.times(1_000L)

            LocalSessionIdentity(
                userId = userId,
                email = email,
                name = name,
                expiresAtEpochMs = expiresAtEpochMs,
            )
        }.getOrNull()
    }
}
