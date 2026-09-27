package compose.project.click.click.data.api // pragma: allowlist secret

import compose.project.click.click.data.repository.AuthRepository // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/*
 * Send Later (click-web `app/api/chat/scheduled/route.ts`, migration
 * `20260925120000_scheduled_messages_read_cursors.sql`). Content is E2EE-encrypted when scheduled;
 * a per-minute cron delivers it as a normal `messages` insert. Mirrors iOS `ChatRepository`.
 */

/** A pending scheduled row as stored server-side (content is still wire ciphertext). */
@Serializable
data class ScheduledMessageDto(
    val id: String,
    @SerialName("chat_id") val chatId: String? = null,
    val content: String = "",
    @SerialName("message_type") val messageType: String = "text",
    val metadata: JsonElement? = null,
    /** Epoch milliseconds. */
    @SerialName("send_at") val sendAt: Long,
)

@Serializable
internal data class ScheduledMessageEnvelope(
    val scheduled: ScheduledMessageDto,
)

@Serializable
internal data class ScheduledMessagesEnvelope(
    val scheduled: List<ScheduledMessageDto> = emptyList(),
)

/** Same fields as [ClickWebSendMessageBody] plus `send_at`; `local_sent_at` is meaningless here. */
@Serializable
internal data class ClickWebScheduleMessageBody(
    @SerialName("chat_id") val chat_id: String? = null,
    @SerialName("connection_id") val connection_id: String? = null,
    @SerialName("user_id") val user_id: String,
    val content: String,
    @SerialName("message_type") val message_type: String,
    val metadata: JsonElement? = null,
    @SerialName("send_at") val send_at: Long,
)

/** DELETE answered 404: the message was already delivered (or isn't the caller's). */
class ScheduledMessageGoneException : Exception("Scheduled message not found")

private suspend fun <T> ChatApiClient.withClickWebToken(
    authToken: String,
    call: suspend (bearer: String) -> Result<T>,
): Result<T> =
    try {
        val token =
            resolveClickWebAccessToken(tokenStorage)
                ?: authToken.trim().takeIf { it.isNotEmpty() }
        if (token.isNullOrBlank()) {
            Result.failure(Exception("Session expired. Sign in again."))
        } else {
            val first = call(token)
            val msg = first.exceptionOrNull()?.redactedRestMessage()?.lowercase()
            if (msg != null && (msg.contains("unauthorized") || msg.contains("401"))) {
                AuthRepository(tokenStorage).refreshSession(forceRefresh = true)
                val retry = resolveClickWebAccessToken(tokenStorage, forceRefresh = true)
                if (!retry.isNullOrBlank()) call(retry) else first
            } else {
                first
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

private suspend fun ChatApiClient.failure(response: HttpResponse): Exception = Exception(readClickWebErrorMessage(response))

/** POST `/api/chat/scheduled` — same validation (and E2EE v2 gate) as sending now. */
internal suspend fun ChatApiClient.postScheduledMessage(
    chatId: String?,
    connectionId: String?,
    userId: String,
    content: String,
    messageType: String,
    metadata: JsonElement?,
    sendAtMs: Long,
    authToken: String,
): Result<ScheduledMessageDto> =
    withClickWebToken(authToken) { bearer ->
        val response =
            client.post("$clickWebBaseUrl/api/chat/scheduled") {
                header(HttpHeaders.Authorization, clickWebBearerHeader(bearer))
                contentType(ContentType.Application.Json)
                setBody(
                    ClickWebScheduleMessageBody(
                        chat_id =
                            chatId?.takeIf {
                                compose.project.click.click.util // pragma: allowlist secret
                                    .isPersistedApiChatId(it)
                            },
                        connection_id = connectionId?.takeIf { it.isNotBlank() },
                        user_id = userId,
                        content = content,
                        message_type = messageType,
                        metadata = metadata,
                        send_at = sendAtMs,
                    ),
                )
            }
        if (response.status.value in 200..299) {
            Result.success(response.body<ScheduledMessageEnvelope>().scheduled)
        } else {
            Result.failure(failure(response))
        }
    }

/** GET `/api/chat/scheduled?chatId=` — the caller's own pending rows, soonest first. */
internal suspend fun ChatApiClient.getScheduledMessages(
    chatId: String,
    authToken: String,
): Result<List<ScheduledMessageDto>> =
    withClickWebToken(authToken) { bearer ->
        val response =
            client.get("$clickWebBaseUrl/api/chat/scheduled") {
                header(HttpHeaders.Authorization, clickWebBearerHeader(bearer))
                parameter("chatId", chatId)
            }
        if (response.status.value in 200..299) {
            Result.success(response.body<ScheduledMessagesEnvelope>().scheduled)
        } else {
            Result.failure(failure(response))
        }
    }

/** DELETE `/api/chat/scheduled?id=`; fails with [ScheduledMessageGoneException] on 404. */
internal suspend fun ChatApiClient.deleteScheduledMessage(
    id: String,
    authToken: String,
): Result<Unit> =
    withClickWebToken(authToken) { bearer ->
        val response =
            client.delete("$clickWebBaseUrl/api/chat/scheduled") {
                header(HttpHeaders.Authorization, clickWebBearerHeader(bearer))
                parameter("id", id)
            }
        when (response.status.value) {
            in 200..299 -> Result.success(Unit)
            404 -> Result.failure(ScheduledMessageGoneException())
            else -> Result.failure(failure(response))
        }
    }
