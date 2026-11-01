package compose.project.click.click.data.api // pragma: allowlist secret

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * `POST /api/chat/devices` answered `409 {code: "DEVICE_REVOKED"}`: this device's key was removed
 * from the account (Settings › Devices on another device). That key can never come back; the device
 * must start over with a new one, which then asks to be approved like any new device.
 */
class ChatDeviceRevokedException : Exception("This device was removed from your account")

/** `409 "Device already registered"`: registration is idempotent, so this is success. */
class ChatDeviceAlreadyRegisteredException : Exception("Device already registered")

/** Maps a failed registration response (click-web `app/api/chat/devices/route.ts`) to a typed error. */
internal fun chatDeviceRegistrationFailure(
    status: Int,
    body: String,
): Exception {
    val json = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()

    fun field(key: String): String? = (json?.get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    val code = field("code")
    val message = field("error")
    return when {
        status == 409 && code == "DEVICE_REVOKED" -> ChatDeviceRevokedException()
        status == 409 && message?.contains("already registered", ignoreCase = true) == true -> ChatDeviceAlreadyRegisteredException()
        else -> Exception(message ?: "Device registration failed ($status)")
    }
}

/** True when this device is registered after the call (fresh, or already there). */
internal fun Result<*>.isChatDeviceRegistered(): Boolean = isSuccess || exceptionOrNull() is ChatDeviceAlreadyRegisteredException
