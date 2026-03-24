package compose.project.click.click.qr

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Base URL for the Click web app — used when generating profile QR codes. */
const val CLICK_WEB_BASE_URL = "https://click-us.vercel.app"

/**
 * Legacy QR payload — JSON with userId (and optional name).
 * Used by older QR codes before the proximity verification system.
 */
@Serializable
data class QrPayload(
    val userId: String,
    val shareKey: String? = null,
    val name: String? = null
)

/**
 * Token-based QR payload — the new format for proximity verification.
 * Contains a single-use, time-bounded token that must be redeemed server-side.
 *
 * Format: { "token": "abc123…", "userId": "uuid…", "exp": 1709500800000 }
 */
@Serializable
data class TokenQrPayload(
    val token: String,
    val userId: String,
    val exp: Long,
    val name: String? = null,
    val issuedAt: Long? = null
)

private val json = Json { ignoreUnknownKeys = true }

fun QrPayload.toJson(): String = json.encodeToString(QrPayload.serializer(), this)

fun buildOfflineQrPayload(userId: String, name: String?): String =
    QrPayload(userId = userId, name = name).toJson()

fun String.toQrPayloadOrNull(): QrPayload? =
    try {
        json.decodeFromString(QrPayload.serializer(), this)
    } catch (e: Exception) {
        null
    }

/**
 * Attempt to parse a token-based QR payload from a JSON string.
 * Returns null if the string doesn't match the expected format.
 */
fun String.toTokenQrPayloadOrNull(): TokenQrPayload? =
    try {
        val payload = json.decodeFromString(TokenQrPayload.serializer(), this)
        // Validate: must have non-blank token and userId
        if (payload.token.isNotBlank() && payload.userId.isNotBlank()) payload else null
    } catch (e: Exception) {
        null
    }

/**
 * Extracts a Click userId from a URL-format QR code.
 * Handles both:
 *   - https://<domain>/connect/<uuid>   (website QR codes)
 *   - click://connect/<uuid>            (deep link format)
 * Returns the UUID string, or null if the input doesn't match either pattern.
 */
private val HTTP_CONNECT_PATTERN = Regex("""https?://[^/]+/connect/([0-9a-fA-F\-]{36})""")
private val DEEP_LINK_PATTERN    = Regex("""click://connect/([0-9a-fA-F\-]{36})""")

fun String.toUserIdFromClickUrl(): String? {
    HTTP_CONNECT_PATTERN.find(this)?.groupValues?.getOrNull(1)?.let { return it }
    DEEP_LINK_PATTERN.find(this)?.groupValues?.getOrNull(1)?.let { return it }
    return null
}

/**
 * Sealed class representing the result of parsing a QR code.
 * Supports both token-based (new) and legacy (old) formats.
 */
sealed class QrParseResult {
    /** New token-based format — requires server-side redemption. */
    data class TokenBased(val payload: TokenQrPayload) : QrParseResult()
    /** Legacy format — userId extracted directly, no token validation. */
    data class Legacy(val userId: String) : QrParseResult()
    /** Unrecognized format — not a Click QR code. */
    object Invalid : QrParseResult()
}

/**
 * Parse a raw QR code string into a [QrParseResult].
 *
 * Tries formats in order:
 * 1. Token-based JSON payload (new format with token field)
 * 2. URL format (https://.../connect/{uuid} or click://connect/{uuid})
 * 3. Legacy JSON payload ({"userId":"...", ...})
 * 4. Invalid
 */
fun parseQrCode(rawData: String): QrParseResult {
    // 1. Try token-based JSON  
    rawData.toTokenQrPayloadOrNull()?.let {
        return QrParseResult.TokenBased(it)
    }

    // 2. Try URL format
    rawData.toUserIdFromClickUrl()?.let {
        return QrParseResult.Legacy(it)
    }

    // 3. Try legacy JSON
    rawData.toQrPayloadOrNull()?.userId?.takeIf { it.isNotBlank() }?.let {
        return QrParseResult.Legacy(it)
    }

    // 4. Invalid
    return QrParseResult.Invalid
}
