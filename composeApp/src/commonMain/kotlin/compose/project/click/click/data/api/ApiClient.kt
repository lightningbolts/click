package compose.project.click.click.data.api

import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.models.AuthResponse
import compose.project.click.click.data.models.ErrorResponse
import compose.project.click.click.data.models.LoginRequest
import compose.project.click.click.data.models.SignUpRequest
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.User
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * JSON body for `GET /api/ping` on [ApiConfig.CLICK_WEB_BASE_URL] (verified Supabase JWT).
 */
@Serializable
data class SecurePingResponse(
    val status: String,
    val message: String,
    @SerialName("user_id") val userId: String,
)

class ApiClient(private val baseUrl: String = BASE_URL) {

    companion object {
        /**
         * Base URL for the Click Python/Flask backend.
         *
         * • Local dev (iOS simulator): http://localhost:5000
         * • Local dev (Android emulator): http://10.0.2.2:5000
         *   (Android emulator maps 10.0.2.2 → host machine's localhost)
         * • Production: replace with your deployed server URL
         *   e.g. https://api.your-domain.com
         */
        const val BASE_URL = "http://localhost:5000"

        private val clickWebAuthOrigin: String
            get() = ApiConfig.CLICK_WEB_BASE_URL.trimEnd('/')

        private val clickWebAuthHost: String
            get() = io.ktor.http.Url(clickWebAuthOrigin).host
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** HTTP client for Flask backend calls — no bearer-auth plugin. */
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    /** HTTP client for click-web (Next.js) calls — attaches Supabase JWT automatically. */
    private val clickWebClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(Auth) {
            bearer {
                loadTokens {
                    val session = SupabaseConfig.client.auth.currentSessionOrNull()
                        ?: return@loadTokens null
                    val access = session.accessToken
                    if (access.isBlank()) return@loadTokens null
                    BearerTokens(access, session.refreshToken.orEmpty())
                }
                refreshTokens {
                    try {
                        SupabaseConfig.client.auth.refreshCurrentSession()
                    } catch (_: Exception) {
                        return@refreshTokens null
                    }
                    val session = SupabaseConfig.client.auth.currentSessionOrNull()
                        ?: return@refreshTokens null
                    val access = session.accessToken
                    if (access.isBlank()) return@refreshTokens null
                    BearerTokens(access, session.refreshToken.orEmpty())
                }
                sendWithoutRequest { request ->
                    request.url.host == clickWebAuthHost
                }
            }
        }
    }

    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return try {
            val response = client.post("$baseUrl/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(email, password))
            }

            if (response.status.value in 200..299) {
                Result.success(response.body<AuthResponse>())
            } else {
                val error = try {
                    response.body<ErrorResponse>()
                } catch (e: Exception) {
                    ErrorResponse("Login failed")
                }
                Result.failure(Exception(error.error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUp(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        birthdayIso: String,
    ): Result<AuthResponse> {
        return try {
            val response = client.post("$baseUrl/create_account") {
                contentType(ContentType.Application.Json)
                setBody(SignUpRequest(email, password, firstName, lastName, birthdayIso))
            }

            if (response.status.value in 200..299) {
                Result.success(response.body<AuthResponse>())
            } else {
                val error = try {
                    response.body<ErrorResponse>()
                } catch (e: Exception) {
                    ErrorResponse("Sign up failed")
                }
                Result.failure(Exception(error.error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun authenticateWithGoogle(token: String): Result<AuthResponse> {
        return try {
            val response = client.post("$baseUrl/google") {
                parameter("token", token)
            }

            if (response.status.value in 200..299) {
                Result.success(response.body<AuthResponse>())
            } else {
                Result.failure(Exception("Google authentication failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshToken(refreshToken: String): Result<String> {
        return try {
            val response = client.post("$baseUrl/refresh") {
                header("Authorization", refreshToken)
            }

            if (response.status.value in 200..299) {
                Result.success(response.body<String>())
            } else {
                Result.failure(Exception("Token refresh failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout(refreshToken: String): Result<Unit> {
        return try {
            val response = client.post("$baseUrl/logout") {
                header("Authorization", refreshToken)
            }

            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Logout failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createConnection(
        authToken: String,
        user1Id: String,
        user2Id: String,
        latitude: Double,
        longitude: Double,
        contextTag: String? = null
    ): Result<Connection> {
        return try {
            val response = client.post("$baseUrl/connection/new/") {
                header("Authorization", authToken)
                contentType(ContentType.Application.Json)
                parameter("id1", user1Id)
                parameter("id2", user2Id)
                parameter("lat", latitude)
                parameter("long", longitude)
                contextTag?.let { parameter("context_tag_id", it) }
            }

            if (response.status.value in 200..299) {
                Result.success(response.body<Connection>())
            } else {
                val error = try {
                    response.body<ErrorResponse>()
                } catch (e: Exception) {
                    ErrorResponse("Failed to create connection")
                }
                Result.failure(Exception(error.error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserById(userId: String): Result<User> {
        return try {
            val response = client.get("$baseUrl/user/$userId")

            if (response.status.value in 200..299) {
                Result.success(response.body<User>())
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Temporary helper: calls Next.js `GET /api/ping` with a Supabase JWT (see Ktor [Auth] bearer config).
     */
    suspend fun testSecurePing(): Result<SecurePingResponse> {
        return try {
            val response: HttpResponse = clickWebClient.get("$clickWebAuthOrigin/api/ping")
            if (response.status.value in 200..299) {
                val body = response.body<SecurePingResponse>()
                println(
                    "ApiClient.testSecurePing: status=${response.status.value} " +
                        "body=${body.status} message=${body.message} user_id=${body.userId}",
                )
                Result.success(body)
            } else {
                val errText = runCatching { response.body<String>() }.getOrElse { it.message ?: "error" }
                println("ApiClient.testSecurePing: failed status=${response.status.value} body=$errText")
                Result.failure(Exception("Ping failed (${response.status.value}): $errText"))
            }
        } catch (e: Exception) {
            println("ApiClient.testSecurePing: exception ${e.message}")
            Result.failure(e)
        }
    }

    fun close() {
        client.close()
        clickWebClient.close()
    }
}
