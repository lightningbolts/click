package compose.project.click.click.qr

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Base URL for the Click web app — used when generating profile QR codes. */
const val CLICK_WEB_BASE_URL = "click-us.vercel.app"

@Serializable
data class QrPayload(
    val userId: String,
    val shareKey: String? = null, // Changed to String? as User model has shareKey as String? or removed?
    // User model in Models.kt doesn't have shareKey. It was removed in migration script.
    // So maybe we don't need shareKey.
    val name: String? = null
)

private val json = Json { ignoreUnknownKeys = true }

fun QrPayload.toJson(): String = json.encodeToString(QrPayload.serializer(), this)

fun String.toQrPayloadOrNull(): QrPayload? =
    try {
        json.decodeFromString(QrPayload.serializer(), this)
    } catch (e: Exception) {
        null
    }

/**
 * Extracts a Click userId from a URL-format QR code.
 * Handles both:
 *   - https://<domain>/connect/<uuid>   (website QR codes)
 *   - click://connect/<uuid>            (deep link format)
 * Returns the UUID string, or null if the input doesn’t match either pattern.
 */
private val HTTP_CONNECT_PATTERN = Regex("""https?://[^/]+/connect/([0-9a-fA-F\-]{36})""")
private val DEEP_LINK_PATTERN    = Regex("""click://connect/([0-9a-fA-F\-]{36})""")

fun String.toUserIdFromClickUrl(): String? {
    HTTP_CONNECT_PATTERN.find(this)?.groupValues?.getOrNull(1)?.let { return it }
    DEEP_LINK_PATTERN.find(this)?.groupValues?.getOrNull(1)?.let { return it }
    return null
}
