package compose.project.click.click.data.api

import compose.project.click.click.data.models.AuthResponse
import compose.project.click.click.data.models.ErrorResponse
import compose.project.click.click.data.models.GoogleAuthRequest
import compose.project.click.click.data.models.LoginRequest
import compose.project.click.click.data.models.SignUpRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ApiClient(private val baseUrl: String = "http://localhost:5000") {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
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

    suspend fun signUp(email: String, password: String, name: String): Result<AuthResponse> {
        return try {
            val response = client.post("$baseUrl/create_account") {
                contentType(ContentType.Application.Json)
                setBody(SignUpRequest(email, password, name))
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

    fun close() {
        client.close()
    }
}

