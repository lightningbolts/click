@file:Suppress(
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.data.api // pragma: allowlist secret

import compose.project.click.click.data.models.* // pragma: allowlist secret
import compose.project.click.click.data.repository.AuthRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.qr.CLICK_WEB_BASE_URL // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
internal data class ClickWebMessageDto(
    val id: String,
    @SerialName("chat_id") val chat_id: String,
    @SerialName("user_id") val user_id: String,
    val content: String,
    @SerialName("time_created") val time_created: Long,
    @SerialName("time_edited") val time_edited: Long? = null,
    @SerialName("is_read") val is_read: Boolean = false,
    @SerialName("message_type") val message_type: String = "text",
    val metadata: JsonElement? = null,
    @SerialName("local_sent_at") val local_sent_at: Long? = null,
    @SerialName("read_at") val read_at: Long? = null,
    @SerialName("delivered_at") val delivered_at: Long? = null,
) {
    fun toMessage(): Message =
        Message(
            id = id,
            user_id = user_id,
            content = content,
            timeCreated = time_created,
            timeEdited = time_edited,
            isRead = is_read,
            messageType = message_type,
            metadata = metadata,
            localSentAt = local_sent_at,
            readAt = read_at,
            deliveredAt = delivered_at,
        ).withDbDerivedDeliveryState()
}

@Serializable
internal data class ClickWebMessageEnvelope(
    val message: ClickWebMessageDto,
)

@Serializable
internal data class HubDetailsEnvelope(
    val hub: ChatApiClient.HubDetailsDto,
)

@Serializable
internal data class HubLeaveRequestBody(
    @SerialName("hub_id") val hubId: String,
)

@Serializable
internal data class ClickWebSendMessageBody(
    @SerialName("chat_id") val chat_id: String? = null,
    @SerialName("connection_id") val connection_id: String? = null,
    @SerialName("user_id") val user_id: String,
    val content: String,
    @SerialName("message_type") val message_type: String? = null,
    val metadata: JsonElement? = null,
    @SerialName("local_sent_at") val local_sent_at: Long? = null,
)

@Serializable
internal data class ClickWebPatchMessageBody(
    @SerialName("message_id") val message_id: String,
    @SerialName("chat_id") val chat_id: String,
    val content: String,
)

@Serializable
internal data class ClickWebMarkChatReadBody(
    @SerialName("chat_id") val chat_id: String,
)

@Serializable
internal data class ClickWebMarkChatUnreadBody(
    @SerialName("chat_id") val chat_id: String,
)

@Serializable
internal data class ClickWebMarkDeliveredBody(
    @SerialName("chat_id") val chat_id: String,
    @SerialName("message_ids") val message_ids: List<String>,
)

@Serializable
internal data class ClickWebReactionEnvelope(
    val action: String,
    val reaction: ChatApiClient.ReactionApiModel? = null,
)

@Serializable
internal data class ClickWebReactionPostBody(
    val messageId: String,
    val reactionType: String,
)

@Serializable
internal data class ClickWebReactionDeleteBody(
    val messageId: String,
    val reactionType: String,
)

@Serializable
internal data class ChatMediaUploadPathResponse(
    val path: String,
)

@Serializable
internal data class ChatMediaUploadUrlResponse(
    val url: String? = null,
    val path: String? = null,
)

@Serializable
internal data class ChatMediaUploadJsonBody(
    @SerialName("chat_id") val chatId: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("file_b64") val fileBase64: String,
)

@Serializable
internal data class ChatAttachmentUploadJsonBody(
    @SerialName("chat_id") val chatId: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("file_b64") val fileBase64: String,
)

@Serializable
internal data class ChatAttachmentUploadResponse(
    val path: String,
    val url: String? = null,
    @SerialName("ttl_seconds") val ttlSeconds: Int = 0,
)

@Serializable
internal data class ChatAttachmentSignBody(
    val path: String,
)

@Serializable
internal data class ChatAttachmentSignResponse(
    val url: String? = null,
    @SerialName("ttl_seconds") val ttlSeconds: Int = 0,
)

@Serializable
internal data class ClickWebHubMessageEnvelope(
    val message: ChatApiClient.HubMessageApiDto,
)

@Serializable
internal data class ConversationSearchEnvelope(
    val hits: List<ChatApiClient.ConversationSearchHitDto> = emptyList(),
)

@Serializable
internal data class ClickWebHubSendMessageBody(
    @SerialName("hub_id") val hubId: String,
    val body: String,
    @SerialName("user_lat") val userLat: Double,
    @SerialName("user_long") val userLong: Double,
    @SerialName("message_type") val messageType: String? = null,
    val metadata: JsonElement? = null,
)
