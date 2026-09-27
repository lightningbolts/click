package compose.project.click.click.data.api // pragma: allowlist secret

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Per-conversation push mutes (click-web `app/api/chat/notifications`, migration
 * `20260926120000_chat_mutes.sql`). Enforced server-side by send-push-notification.
 */

@Serializable
data class ChatMuteDto(
    @SerialName("chat_id") val chatId: String,
    /** ISO timestamp; null mutes until turned back on. */
    @SerialName("muted_until") val mutedUntil: String? = null,
)

@Serializable
internal data class ChatMutesResponseDto(
    val mutes: List<ChatMuteDto> = emptyList(),
)

@Serializable
internal data class ChatMuteBody(
    @SerialName("chat_id") val chatId: String,
    val muted: Boolean,
    @SerialName("muted_until") val mutedUntil: String? = null,
)

/** GET `/api/chat/notifications` — the viewer's active mutes. */
internal suspend fun ApiClient.getChatMutesImpl(): Result<List<ChatMuteDto>> =
    try {
        val response = clickWebClient.get("${ApiClient.clickWebAuthOrigin}/api/chat/notifications")
        if (response.status.value in 200..299) {
            Result.success(response.body<ChatMutesResponseDto>().mutes)
        } else {
            clickWebFailure(response)
        }
    } catch (e: ClientRequestException) {
        clickWebFailure(e.response)
    } catch (e: Exception) {
        Result.failure(e)
    }

/** PUT `/api/chat/notifications` — mute until [mutedUntilIso] (null: until turned back on) or unmute. */
internal suspend fun ApiClient.putChatMuteImpl(
    chatId: String,
    muted: Boolean,
    mutedUntilIso: String?,
): Result<Unit> {
    val id = chatId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("Missing chat id"))
    return try {
        val response =
            clickWebClient.put("${ApiClient.clickWebAuthOrigin}/api/chat/notifications") {
                contentType(ContentType.Application.Json)
                setBody(ChatMuteBody(chatId = id, muted = muted, mutedUntil = if (muted) mutedUntilIso else null))
            }
        if (response.status.value in 200..299) Result.success(Unit) else clickWebFailure(response)
    } catch (e: ClientRequestException) {
        clickWebFailure(e.response)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
