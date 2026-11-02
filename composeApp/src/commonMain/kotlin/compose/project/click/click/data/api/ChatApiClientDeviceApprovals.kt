package compose.project.click.click.data.api // pragma: allowlist secret

import io.ktor.client.call.body
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

/*
 * Approving a new device from one already in use (click-web
 * `app/api/chat/devices/history-requests/...`, iOS `ChatRepository.deviceApprovals` and friends).
 * The server decides who may approve what; this file only moves the bytes.
 */

@Serializable
internal data class DeviceApprovalDto(
    val id: String = "",
    val status: String = "",
    @SerialName("device_label") val deviceLabel: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val expired: Boolean = false,
    @SerialName("email_sent") val emailSent: Boolean = false,
)

@Serializable
internal data class DeviceApprovalsEnvelope(
    val incoming: List<DeviceApprovalDto> = emptyList(),
    val own: DeviceApprovalDto? = null,
)

@Serializable
internal data class AskForHistoryBody(
    @SerialName("device_id") val deviceId: String,
    val reopen: Boolean? = null,
)

@Serializable
internal data class ApprovingDeviceBody(
    @SerialName("approving_device_id") val approvingDeviceId: String,
)

/** A one-time challenge wrapped to the approving device's identity key, like an epoch key. */
@Serializable
internal data class DeviceApprovalChallengeDto(
    @SerialName("challenge_id") val challengeId: String,
    val envelope: String,
    @SerialName("chat_id") val chatId: String,
    val epoch: Int,
    @SerialName("sender_device_id") val senderDeviceId: String,
)

@Serializable
internal data class DeviceApprovalDecisionBody(
    val decision: String,
    @SerialName("approving_device_id") val approvingDeviceId: String,
    @SerialName("challenge_id") val challengeId: String,
    val proof: String,
)

@Serializable
internal data class EmailLinkEnvelope(
    val sent: Boolean = false,
)

/** `incoming`: the account's other devices this one can approve. `own`: this device's request. */
internal suspend fun ChatApiClient.getDeviceApprovals(
    deviceId: String,
    authToken: String,
): Result<DeviceApprovalsEnvelope> =
    runApproval {
        client.get("$clickWebBaseUrl/api/chat/devices/history-requests") {
            header(HttpHeaders.Authorization, bearerAuthHeader(authToken))
            parameter("device_id", deviceId)
        }
    }

/** Asks the account's other devices to share history with this one (idempotent). */
internal suspend fun ChatApiClient.askForHistory(
    deviceId: String,
    reopen: Boolean,
    authToken: String,
): Result<DeviceApprovalsEnvelope> =
    runApproval {
        client.post("$clickWebBaseUrl/api/chat/devices/history-requests") {
            header(HttpHeaders.Authorization, bearerAuthHeader(authToken))
            contentType(ContentType.Application.Json)
            setBody(AskForHistoryBody(deviceId, reopen.takeIf { it }))
        }
    }

internal suspend fun ChatApiClient.getDeviceApprovalChallenge(
    requestId: String,
    approvingDeviceId: String,
    authToken: String,
): Result<DeviceApprovalChallengeDto> =
    runApproval {
        client.post("$clickWebBaseUrl/api/chat/devices/history-requests/$requestId/challenge") {
            header(HttpHeaders.Authorization, bearerAuthHeader(authToken))
            contentType(ContentType.Application.Json)
            setBody(ApprovingDeviceBody(approvingDeviceId))
        }
    }

internal suspend fun ChatApiClient.postDeviceApprovalDecision(
    requestId: String,
    body: DeviceApprovalDecisionBody,
    authToken: String,
): Result<Unit> =
    runApproval<Unit> {
        client.post("$clickWebBaseUrl/api/chat/devices/history-requests/$requestId") {
            header(HttpHeaders.Authorization, bearerAuthHeader(authToken))
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

/** "Email me a link" from the waiting device. False when the email already went out. */
internal suspend fun ChatApiClient.emailDeviceApprovalLink(
    requestId: String,
    authToken: String,
): Result<Boolean> =
    runApproval<EmailLinkEnvelope> {
        client.post("$clickWebBaseUrl/api/chat/devices/history-requests/$requestId/email") {
            header(HttpHeaders.Authorization, bearerAuthHeader(authToken))
        }
    }.map { it.sent }

private suspend inline fun <reified T> ChatApiClient.runApproval(request: () -> HttpResponse): Result<T> =
    try {
        val response = request()
        if (response.status.value in 200..299) {
            Result.success(if (T::class == Unit::class) Unit as T else response.body<T>())
        } else {
            Result.failure(DeviceApprovalHttpException(response.status.value, readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

/** Keeps the status so the UI can tell "already decided" (409) and "expired" (410) apart. */
internal class DeviceApprovalHttpException(
    val status: Int,
    message: String,
) : Exception(message)
