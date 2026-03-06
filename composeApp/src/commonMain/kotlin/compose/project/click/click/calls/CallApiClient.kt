package compose.project.click.click.calls

import compose.project.click.click.qr.CLICK_WEB_BASE_URL
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class CallApiClient(
    private val baseUrl: String = CLICK_WEB_BASE_URL,
    private val httpClient: HttpClient? = null
) {
    private val client = httpClient ?: HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    @Serializable
    data class LiveKitTokenRequest(
        val roomName: String,
        val participantName: String,
        val userId: String
    )

    @Serializable
    data class LiveKitTokenResponse(
        val token: String,
        val wsUrl: String
    )

    suspend fun fetchToken(
        authToken: String,
        roomName: String,
        participantName: String,
        userId: String
    ): Result<LiveKitTokenResponse> {
        return try {
            val response = client.post("$baseUrl/api/livekit/token") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $authToken")
                setBody(
                    LiveKitTokenRequest(
                        roomName = roomName,
                        participantName = participantName,
                        userId = userId
                    )
                )
            }

            if (response.status.value in 200..299) {
                Result.success(response.body<LiveKitTokenResponse>())
            } else {
                Result.failure(Exception("Failed to create call token"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}