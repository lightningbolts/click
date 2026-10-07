package compose.project.click.click.data.api // pragma: allowlist secret

import io.ktor.client.call.body
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

/*
 * Email-approved chat history for the user's newer devices (click-web
 * `app/api/chat/devices/history-backfill` + `app/api/chat/key-transfer`, migration
 * `20260929120000_device_history_requests.sql`). An older device lists what its approved newer
 * devices are missing, wraps those epoch keys for them and uploads the envelopes.
 */

/** One newer device of this account and the epochs of [chatId] it still needs. */
@Serializable
internal data class HistoryBackfillItemDto(
    @SerialName("request_id") val requestId: String,
    @SerialName("recipient_device_id") val recipientDeviceId: String,
    @SerialName("recipient_public_key") val recipientPublicKey: String,
    @SerialName("chat_id") val chatId: String,
    val epochs: List<Int> = emptyList(),
)

@Serializable
internal data class HistoryBackfillEnvelope(
    val items: List<HistoryBackfillItemDto> = emptyList(),
)

@Serializable
internal data class KeyTransferEnvelopeDto(
    val epoch: Int,
    @SerialName("recipient_device_id") val recipientDeviceId: String,
    @SerialName("sender_device_id") val senderDeviceId: String,
    val envelope: String,
)

@Serializable
internal data class KeyTransferBody(
    @SerialName("chat_id") val chatId: String,
    @SerialName("approving_device_id") val approvingDeviceId: String,
    @SerialName("recipient_device_id") val recipientDeviceId: String,
    @SerialName("historical_envelopes") val historicalEnvelopes: List<KeyTransferEnvelopeDto>,
)

/** What this (older) device should share with the account's email-approved newer devices. */
internal suspend fun ChatApiClient.getHistoryBackfill(
    deviceId: String,
    authToken: String,
): Result<List<HistoryBackfillItemDto>> =
    try {
        require(deviceId.isNotBlank()) { "deviceId is required" }
        val response =
            client.get("$clickWebBaseUrl/api/chat/devices/history-backfill") {
                header(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                parameter("device_id", deviceId)
            }
        if (response.status.value in 200..299) {
            Result.success(response.body<HistoryBackfillEnvelope>().items)
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

/** Uploads historical epoch-key envelopes wrapped by this device for one of the user's devices. */
internal suspend fun ChatApiClient.postKeyTransfer(
    body: KeyTransferBody,
    authToken: String,
): Result<Unit> =
    try {
        require(body.historicalEnvelopes.isNotEmpty()) { "no envelopes to transfer" }
        val response =
            client.post("$clickWebBaseUrl/api/chat/key-transfer") {
                header(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        if (response.status.value in 200..299) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
