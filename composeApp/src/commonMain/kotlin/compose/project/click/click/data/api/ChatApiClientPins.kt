package compose.project.click.click.data.api // pragma: allowlist secret

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * Pinned messages in direct and group chats (click-web `app/api/chat/pins/route.ts`; iOS
 * `ChatRepository` pins). Bearer-only. Re-pinning moves a message to the top.
 */

@Serializable
data class ChatPinDto(
    @SerialName("message_id") val messageId: String,
    @SerialName("pinned_by") val pinnedBy: String? = null,
    @SerialName("pinned_at") val pinnedAt: String? = null,
)

@Serializable
internal data class ChatPinsEnvelope(
    val pins: List<ChatPinDto> = emptyList(),
)

private suspend fun ChatApiClient.pinsBearer(): String? = resolveClickWebAccessToken(tokenStorage)

/** GET `/api/chat/pins?chatId=` — newest first. */
internal suspend fun ChatApiClient.getChatPinsImpl(chatId: String): Result<List<ChatPinDto>> =
    try {
        val token = pinsBearer() ?: return Result.failure(Exception("Session expired. Sign in again."))
        val response =
            client.get("$clickWebBaseUrl/api/chat/pins") {
                header(HttpHeaders.Authorization, clickWebBearerHeader(token))
                parameter("chatId", chatId)
            }
        if (response.status.value in 200..299) {
            Result.success(response.body<ChatPinsEnvelope>().pins)
        } else {
            Result.failure(ClickWebRequestException(response.status.value, readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

/** POST (pin) or DELETE (unpin) `/api/chat/pins {messageId}`. */
internal suspend fun ChatApiClient.setChatPinImpl(
    messageId: String,
    pinned: Boolean,
): Result<Unit> =
    try {
        val token = pinsBearer() ?: return Result.failure(Exception("Session expired. Sign in again."))
        val body = buildJsonObject { put("messageId", messageId) }.toString()
        val response =
            if (pinned) {
                client.post("$clickWebBaseUrl/api/chat/pins") {
                    header(HttpHeaders.Authorization, clickWebBearerHeader(token))
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            } else {
                client.delete("$clickWebBaseUrl/api/chat/pins") {
                    header(HttpHeaders.Authorization, clickWebBearerHeader(token))
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        if (response.status.value in 200..299) {
            Result.success(Unit)
        } else {
            Result.failure(ClickWebRequestException(response.status.value, readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

/** The pin the banner shows after [currentMessageId]: newest first, wrapping around (iOS banner taps). */
fun nextPinnedMessageId(
    pins: List<ChatPinDto>,
    currentMessageId: String?,
): String? {
    if (pins.isEmpty()) return null
    val index = pins.indexOfFirst { it.messageId == currentMessageId }
    return pins[(index + 1).mod(pins.size)].messageId
}
