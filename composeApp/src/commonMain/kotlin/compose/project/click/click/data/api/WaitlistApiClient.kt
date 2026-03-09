package compose.project.click.click.data.api

import compose.project.click.click.qr.CLICK_WEB_BASE_URL
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class WaitlistApiClient(
    private val baseUrl: String = CLICK_WEB_BASE_URL,
    private val client: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
) {
    suspend fun joinWaitlist(email: String, source: String = "mobile_auth"): Result<String> {
        return try {
            val response = client.post("$baseUrl/api/waitlist") {
                contentType(ContentType.Application.Json)
                setBody(WaitlistRequest(email = email, source = source))
            }

            val body = response.body<WaitlistResponse>()
            if (response.status.value in 200..299 && body.success) {
                Result.success(body.message ?: "You're on the list! We'll be in touch.")
            } else {
                Result.failure(Exception(body.error ?: "Failed to join waitlist"))
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}

@Serializable
private data class WaitlistRequest(
    val email: String,
    val source: String
)

@Serializable
private data class WaitlistResponse(
    val success: Boolean = false,
    val message: String? = null,
    val error: String? = null
)