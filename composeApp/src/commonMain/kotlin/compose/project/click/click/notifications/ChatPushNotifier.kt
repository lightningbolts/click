package compose.project.click.click.notifications

import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.data.storage.createTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ChatPushNotifier(
    private val tokenStorage: TokenStorage = createTokenStorage(),
    httpClient: HttpClient? = null,
) {
    private val client = httpClient ?: HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun notifyNewMessage(chatId: String, messageId: String, senderUserId: String): Result<Unit> {
        val jwt = tokenStorage.getJwt()
            ?: return Result.failure(IllegalStateException("Missing auth token"))

        return try {
            val response = client.post(SupabaseConfig.functionUrl("send-push-notification")) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $jwt")
                setBody(
                    PushRequestBody(
                        data = buildJsonObject {
                            put("type", "chat_message")
                            put("chat_id", chatId)
                            put("message_id", messageId)
                            put("sender_user_id", senderUserId)
                        }
                    )
                )
            }

            if (!response.status.isSuccess()) {
                return Result.failure(IllegalStateException("Chat message push failed with ${response.status.value}"))
            }

            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    @Serializable
    private data class PushRequestBody(
        val title: String? = null,
        val body: String? = null,
        @SerialName("recipient_user_id")
        val recipientUserId: String? = null,
        val data: kotlinx.serialization.json.JsonObject,
    )
}