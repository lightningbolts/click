package compose.project.click.click.calls

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
import io.ktor.http.isSuccess
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CallPushNotifier(
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

    suspend fun notifyIncomingCall(invite: CallInvite): Result<Unit> {
        val jwt = tokenStorage.getJwt()
            ?: return Result.failure(IllegalStateException("Missing auth token"))

        return try {
            val response = client.post(SupabaseConfig.functionUrl("send-push-notification")) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $jwt")
                setBody(
                    PushRequestBody(
                        recipientUserId = invite.calleeId,
                        title = if (invite.videoEnabled) {
                            "Incoming video call from ${invite.callerName}"
                        } else {
                            "Incoming call from ${invite.callerName}"
                        },
                        body = "Open Click to answer",
                        data = buildJsonObject {
                            put("type", "incoming_call")
                            put("call_id", invite.callId)
                            put("connection_id", invite.connectionId)
                            put("room_name", invite.roomName)
                            put("caller_id", invite.callerId)
                            put("caller_name", invite.callerName)
                            put("callee_id", invite.calleeId)
                            put("callee_name", invite.calleeName)
                            put("video_enabled", invite.videoEnabled)
                            put("created_at", invite.createdAt)
                        }
                    )
                )
            }

            if (!response.status.isSuccess()) {
                return Result.failure(IllegalStateException("Incoming call push failed with ${response.status.value}"))
            }

            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    @Serializable
    private data class PushRequestBody(
        @kotlinx.serialization.SerialName("recipient_user_id")
        val recipientUserId: String,
        val title: String,
        val body: String,
        val data: kotlinx.serialization.json.JsonObject,
    )
}