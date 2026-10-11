package compose.project.click.click.data.api // pragma: allowlist secret

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * The account's devices on click-web: approving a new sign-in from a device already in use
 * (`app/api/chat/devices/history-requests/...`, `lib/server/deviceApproval.ts`), Settings › Devices
 * (`GET /api/me/devices`, `DELETE /api/chat/devices?device_id=`) and in-app account deletion
 * (`DELETE /api/user/delete`). All bearer-authenticated through [ApiClient.clickWebClient].
 */

/** One device's request to read the account's past messages (`projection` in the list route). */
@Serializable
data class DeviceHistoryRequestDto(
    val id: String,
    /** `pending`, `approved` or `denied`. */
    val status: String,
    /** "Pixel 9", "Chrome on Mac"; null for builds that didn't send one. Absent on `GET …/{id}`. */
    @SerialName("device_label") val deviceLabel: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("decided_at") val decidedAt: String? = null,
    @SerialName("email_sent") val emailSent: Boolean = false,
    val expired: Boolean = false,
) {
    val isPending: Boolean get() = status == "pending"
    val isApproved: Boolean get() = status == "approved"
    val isDenied: Boolean get() = status == "denied"
}

/** An older active device of the account that could approve this one. */
@Serializable
data class DeviceApproverDto(
    val label: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
)

/** `GET /api/chat/devices/history-requests?device_id=`. */
@Serializable
data class DeviceHistoryRequestsDto(
    /** Other devices of this account waiting for approval (this device can approve them). */
    val incoming: List<DeviceHistoryRequestDto> = emptyList(),
    /** This device's own request, if it has one. */
    val own: DeviceHistoryRequestDto? = null,
    /** Most recently active first; at most 10. */
    val approvers: List<DeviceApproverDto> = emptyList(),
)

@Serializable
internal data class AskForHistoryBody(
    @SerialName("device_id") val deviceId: String,
    /** Only sent as true: the route is `.strict()` and asks a denied request again only with it. */
    val reopen: Boolean? = null,
)

/** `POST /api/chat/devices/history-requests`; `status` is `requested`, `first-device` or `exists`. */
@Serializable
data class AskForHistoryResponseDto(
    val status: String? = null,
    val own: DeviceHistoryRequestDto? = null,
)

@Serializable
internal data class ApprovalChallengeBody(
    @SerialName("approving_device_id") val approvingDeviceId: String,
)

/** A one-time nonce wrapped to this device's identity key, like an epoch key. */
@Serializable
data class DeviceApprovalChallengeDto(
    @SerialName("challenge_id") val challengeId: String,
    val envelope: String,
    @SerialName("chat_id") val chatId: String? = null,
    val epoch: Int? = null,
    @SerialName("sender_device_id") val senderDeviceId: String? = null,
)

@Serializable
data class DeviceApprovalDecisionBody(
    /** `approve` or `deny`. */
    val decision: String,
    @SerialName("approving_device_id") val approvingDeviceId: String,
    @SerialName("challenge_id") val challengeId: String,
    /** Standard base64 of the unwrapped 32-byte nonce. */
    val proof: String,
)

@Serializable
internal data class DeviceHistoryRequestEnvelope(
    val request: DeviceHistoryRequestDto? = null,
)

@Serializable
internal data class ApprovalEmailResponseDto(
    val sent: Boolean = false,
)

/** One active device of the account (`GET /api/me/devices`). */
@Serializable
data class MyDeviceDto(
    @SerialName("device_id") val deviceId: String,
    val label: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
)

@Serializable
internal data class MyDevicesDto(
    val devices: List<MyDeviceDto> = emptyList(),
)

@Serializable
internal data class ClickWebErrorCodeDto(
    val error: String? = null,
    val code: String? = null,
)

/** A click-web error with its machine-readable `code` (`lib/api/errors.ts`). */
class ClickWebCodedException(
    val statusCode: Int,
    val code: String?,
    message: String,
) : Exception(message)

private val errorJson = Json { ignoreUnknownKeys = true }

/** Reads `{ error, code }` from a failed click-web response body. */
internal fun parseClickWebCodedError(
    status: Int,
    body: String?,
): ClickWebCodedException {
    val parsed = body?.let { runCatching { errorJson.decodeFromString<ClickWebErrorCodeDto>(it) }.getOrNull() }
    val message =
        parsed
            ?.error
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(200) ?: "Request failed ($status)"
    return ClickWebCodedException(status, parsed?.code?.trim()?.takeIf { it.isNotEmpty() }, message)
}

private suspend fun codedFailure(response: HttpResponse): Result<Nothing> =
    Result.failure(parseClickWebCodedError(response.status.value, runCatching { response.bodyAsText() }.getOrNull()))

private suspend fun <T> deviceCall(block: suspend () -> Result<T>): Result<T> =
    try {
        block()
    } catch (e: ClientRequestException) {
        codedFailure(e.response)
    } catch (e: Exception) {
        Result.failure(e)
    }

private fun historyRequestPath(requestId: String): String =
    "${ApiClient.clickWebAuthOrigin}/api/chat/devices/history-requests/${requestId.trim().encodeURLPathPart()}"

/** `GET /api/chat/devices/history-requests?device_id=` — incoming, own and approvers for this device. */
internal suspend fun ApiClient.getDeviceHistoryRequestsImpl(deviceId: String): Result<DeviceHistoryRequestsDto> =
    deviceCall {
        val response =
            clickWebClient.get("${ApiClient.clickWebAuthOrigin}/api/chat/devices/history-requests") {
                parameter("device_id", deviceId)
            }
        if (response.status.value in 200..299) Result.success(response.decode<DeviceHistoryRequestsDto>()) else codedFailure(response)
    }

/** `POST /api/chat/devices/history-requests {device_id, reopen?}` — ask the other devices (again). */
internal suspend fun ApiClient.askForHistoryImpl(
    deviceId: String,
    reopen: Boolean,
): Result<AskForHistoryResponseDto> =
    deviceCall {
        val response =
            clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/chat/devices/history-requests") {
                contentType(ContentType.Application.Json)
                setBody(AskForHistoryBody(deviceId, reopen = if (reopen) true else null))
            }
        if (response.status.value in 200..299) Result.success(response.decode<AskForHistoryResponseDto>()) else codedFailure(response)
    }

/** `GET …/history-requests/{id}` — the request itself (no device label). */
internal suspend fun ApiClient.getDeviceHistoryRequestImpl(requestId: String): Result<DeviceHistoryRequestDto?> =
    deviceCall {
        val response = clickWebClient.get(historyRequestPath(requestId))
        if (response.status.value in 200..299) {
            Result.success(response.decode<DeviceHistoryRequestEnvelope>().request)
        } else {
            codedFailure(response)
        }
    }

/** `POST …/history-requests/{id}/challenge {approving_device_id}`. */
internal suspend fun ApiClient.postDeviceApprovalChallengeImpl(
    requestId: String,
    approvingDeviceId: String,
): Result<DeviceApprovalChallengeDto> =
    deviceCall {
        val response =
            clickWebClient.post("${historyRequestPath(requestId)}/challenge") {
                contentType(ContentType.Application.Json)
                setBody(ApprovalChallengeBody(approvingDeviceId))
            }
        if (response.status.value in 200..299) Result.success(response.decode<DeviceApprovalChallengeDto>()) else codedFailure(response)
    }

/** `POST …/history-requests/{id} {decision, approving_device_id, challenge_id, proof}`. */
internal suspend fun ApiClient.postDeviceApprovalDecisionImpl(
    requestId: String,
    body: DeviceApprovalDecisionBody,
): Result<DeviceHistoryRequestDto?> =
    deviceCall {
        val response =
            clickWebClient.post(historyRequestPath(requestId)) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        if (response.status.value in 200..299) {
            Result.success(response.decode<DeviceHistoryRequestEnvelope>().request)
        } else {
            codedFailure(response)
        }
    }

/** `POST …/history-requests/{id}/email` — false when the email already went out. */
internal suspend fun ApiClient.emailDeviceApprovalLinkImpl(requestId: String): Result<Boolean> =
    deviceCall {
        val response = clickWebClient.post("${historyRequestPath(requestId)}/email")
        if (response.status.value in 200..299) Result.success(response.decode<ApprovalEmailResponseDto>().sent) else codedFailure(response)
    }

/** `GET /api/me/devices` — the account's active devices, most recently seen first. */
internal suspend fun ApiClient.getMyDevicesImpl(): Result<List<MyDeviceDto>> =
    deviceCall {
        val response = clickWebClient.get("${ApiClient.clickWebAuthOrigin}/api/me/devices")
        if (response.status.value in 200..299) Result.success(response.decode<MyDevicesDto>().devices) else codedFailure(response)
    }

/** `DELETE /api/chat/devices?device_id=` — signs that device out of secure messaging. */
internal suspend fun ApiClient.removeChatDeviceImpl(deviceId: String): Result<Unit> =
    deviceCall {
        val response =
            clickWebClient.delete("${ApiClient.clickWebAuthOrigin}/api/chat/devices") {
                parameter("device_id", deviceId)
            }
        if (response.status.value in 200..299) Result.success(Unit) else codedFailure(response)
    }

/** `DELETE /api/user/delete` with no body (the Apple code is iOS-only) — permanently deletes the account. */
internal suspend fun ApiClient.deleteAccountImpl(): Result<Unit> =
    deviceCall {
        val response = clickWebClient.delete("${ApiClient.clickWebAuthOrigin}/api/user/delete")
        if (response.status.value in 200..299) Result.success(Unit) else codedFailure(response)
    }

private suspend inline fun <reified T> HttpResponse.decode(): T = errorJson.decodeFromString(bodyAsText())
