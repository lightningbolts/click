@file:Suppress(
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.data.api // pragma: allowlist secret

import compose.project.click.click.data.models.* // pragma: allowlist secret
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
internal data class ClickWebChatDeviceDto(
    val id: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("identity_public_key") val identityPublicKey: String,
    @SerialName("key_algorithm") val keyAlgorithm: String,
    @SerialName("crypto_version") val cryptoVersion: Int,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("user_id") val userId: String? = null,
)

@Serializable
internal data class ClickWebChatDeviceEnvelope(
    val device: ClickWebChatDeviceDto,
)

@Serializable
internal data class ClickWebChatDevicesEnvelope(
    val devices: List<ClickWebChatDeviceDto> = emptyList(),
)

@Serializable
internal data class ClickWebRegisterChatDeviceBody(
    @SerialName("device_id") val deviceId: String,
    @SerialName("identity_public_key") val identityPublicKey: String,
)

@Serializable
internal data class ClickWebChatEpochEnvelopeDto(
    @SerialName("chat_id") val chatId: String,
    val epoch: Int,
    /** The response exposes the persisted recipient row UUID, not the logical device id. */
    @SerialName("recipient_device_id") val recipientDeviceId: String,
    @SerialName("sender_device_id") val senderDeviceId: String,
    val envelope: String,
)

@Serializable
internal data class ClickWebChatE2eeV2StateEnvelope(
    @SerialName("chat_id") val chatId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("current_epoch") val currentEpoch: Int? = null,
    @SerialName("membership_fingerprint") val membershipFingerprint: String? = null,
    val envelopes: List<ClickWebChatEpochEnvelopeDto> = emptyList(),
)

@Serializable
internal data class ClickWebHubEpochEnvelopeDto(
    @SerialName("hub_id") val hubId: String,
    val epoch: Int,
    /** The response exposes the persisted recipient row UUID, not the logical device id. */
    @SerialName("recipient_device_id") val recipientDeviceId: String,
    @SerialName("sender_device_id") val senderDeviceId: String,
    val envelope: String,
)

@Serializable
internal data class ClickWebHubE2eeV2StateEnvelope(
    @SerialName("hub_id") val hubId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("current_epoch") val currentEpoch: Int? = null,
    @SerialName("membership_fingerprint") val membershipFingerprint: String? = null,
    val envelopes: List<ClickWebHubEpochEnvelopeDto> = emptyList(),
)

@Serializable
internal data class ClickWebHubEpochWriteEnvelope(
    @SerialName("recipient_device_id") val recipientDeviceId: String,
    val envelope: String,
)

@Serializable
internal data class ClickWebHubEpochWriteBody(
    @SerialName("hub_id") val hubId: String,
    val epoch: Int,
    @SerialName("sender_device_id") val senderDeviceId: String,
    @SerialName("membership_fingerprint") val membershipFingerprint: String,
    val envelopes: List<ClickWebHubEpochWriteEnvelope>,
)

@Serializable
internal data class ClickWebChatEpochWriteEnvelope(
    @SerialName("recipient_device_id") val recipientDeviceId: String,
    val envelope: String,
)

@Serializable
internal data class ClickWebChatEpochWriteBody(
    @SerialName("chat_id") val chatId: String,
    val epoch: Int,
    @SerialName("sender_device_id") val senderDeviceId: String,
    @SerialName("membership_fingerprint") val membershipFingerprint: String,
    val envelopes: List<ClickWebChatEpochWriteEnvelope>,
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
    val bucket: String? = null,
    val url: String? = null,
    @SerialName("ttl_seconds") val ttlSeconds: Long? = null,
)

@Serializable
internal data class ChatMediaUploadUrlResponse(
    val url: String? = null,
    val path: String? = null,
)

internal fun ChatMediaUploadUrlResponse.trimmedUrlOrNull(): String? = url?.trim()?.takeIf { it.isNotEmpty() }

@Serializable
internal data class ChatMediaUploadJsonBody(
    @SerialName("chat_id") val chatId: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("file_b64") val fileBase64: String,
    @SerialName("e2ee_v2_envelope") val e2eeV2Envelope: String? = null,
    @SerialName("media_ciphertext_sha256") val mediaCiphertextSha256: String? = null,
    val epoch: Int? = null,
    @SerialName("sender_device_id") val senderDeviceId: String? = null,
    @SerialName("client_message_id") val clientMessageId: String? = null,
)

@Serializable
internal data class ChatAttachmentUploadJsonBody(
    @SerialName("chat_id") val chatId: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("file_b64") val fileBase64: String,
    @SerialName("e2ee_v2_envelope") val e2eeV2Envelope: String? = null,
    @SerialName("media_ciphertext_sha256") val mediaCiphertextSha256: String? = null,
    val epoch: Int? = null,
    @SerialName("sender_device_id") val senderDeviceId: String? = null,
    @SerialName("client_message_id") val clientMessageId: String? = null,
)

/** The v2 metadata sent alongside an opaque media upload. */
data class E2eeV2MediaUploadRequest(
    val envelope: String,
    val mediaCiphertextSha256: String,
    val epoch: Int,
    val senderDeviceId: String,
    val clientMessageId: String,
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
