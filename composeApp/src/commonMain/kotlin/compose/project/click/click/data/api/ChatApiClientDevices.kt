package compose.project.click.click.data.api // pragma: allowlist secret

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Settings › Devices (click-web `app/api/me/devices/route.ts`, `DELETE /api/chat/devices`; iOS
 * `DevicesSettingsView`). Removing a device is about chats, not sign-in: the server stops sharing
 * chat keys with it, and it stays signed in until it signs out.
 */

/** One active chat device of this account. */
@Serializable
data class MyChatDeviceDto(
    @SerialName("device_id") val deviceId: String,
    val label: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
)

@Serializable
internal data class MyChatDevicesEnvelope(
    val devices: List<MyChatDeviceDto> = emptyList(),
)

private suspend fun ChatApiClient.bearer(): String? = resolveClickWebAccessToken(tokenStorage)

/** GET `/api/me/devices` — this account's active chat devices, most recently seen first. */
internal suspend fun ChatApiClient.getMyChatDevicesImpl(): Result<List<MyChatDeviceDto>> =
    try {
        val token = bearer() ?: return Result.failure(Exception("Session expired. Sign in again."))
        val response =
            client.get("$clickWebBaseUrl/api/me/devices") {
                header(HttpHeaders.Authorization, clickWebBearerHeader(token))
            }
        if (response.status.value in 200..299) {
            Result.success(response.body<MyChatDevicesEnvelope>().devices)
        } else {
            Result.failure(ClickWebRequestException(response.status.value, readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

/** DELETE `/api/chat/devices?device_id=` — stop sharing chat keys with one of this account's devices. */
internal suspend fun ChatApiClient.removeChatDeviceImpl(deviceId: String): Result<Unit> =
    try {
        require(deviceId.isNotBlank()) { "device_id is required" }
        val token = bearer() ?: return Result.failure(Exception("Session expired. Sign in again."))
        val response =
            client.delete("$clickWebBaseUrl/api/chat/devices") {
                header(HttpHeaders.Authorization, clickWebBearerHeader(token))
                parameter("device_id", deviceId)
            }
        if (response.status.value in 200..299) {
            Result.success(Unit)
        } else {
            Result.failure(ClickWebRequestException(response.status.value, readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
