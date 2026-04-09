package compose.project.click.click.qr

import kotlinx.serialization.SerialName
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
    val issuedAt: Long? = null,
    /** B2B / Community Hub venue — server maps coordinates; scanner GPS is ignored when set. */
    @SerialName("venue_id")
    val venueId: String? = null,
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

/** Ephemeral community hub: click://hub/{hub_id} or https://…/hub/{hub_id} */
private val HUB_ID_SEGMENT = """([a-zA-Z0-9][a-zA-Z0-9_\-]{0,127})"""
private val HTTP_HUB_PATTERN = Regex("""https?://[^/]+/hub/$HUB_ID_SEGMENT""")
private val DEEP_LINK_HUB_PATTERN = Regex("""click://hub/$HUB_ID_SEGMENT""")

fun String.toHubIdFromClickHubUrl(): String? {
    DEEP_LINK_HUB_PATTERN.find(this)?.groupValues?.getOrNull(1)?.let { return it }
    HTTP_HUB_PATTERN.find(this)?.groupValues?.getOrNull(1)?.let { return it }
    return null
}

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
    /** Ephemeral community hub deep link — proximity check then hub chat. */
    data class CommunityHub(val hubId: String) : QrParseResult()
    /** Unrecognized format — not a Click QR code. */
    object Invalid : QrParseResult()
}

/**
 * Parse a raw QR code string into a [QrParseResult].
 *
 * Tries formats in order:
 * 1. Token-based JSON payload (new format with token field)
 * 2. Hub deep link (click://hub/{id} or https://.../hub/{id})
 * 3. URL format (https://.../connect/{uuid} or click://connect/{uuid})
 * 4. Legacy JSON payload ({"userId":"...", ...})
 * 5. Invalid
 */
fun parseQrCode(rawData: String): QrParseResult {
    val trimmed = rawData.trim()

    // 1. Try token-based JSON  
    trimmed.toTokenQrPayloadOrNull()?.let {
        return QrParseResult.TokenBased(it)
    }

    // 2. Community hub
    trimmed.toHubIdFromClickHubUrl()?.let { hubId ->
        return QrParseResult.CommunityHub(hubId)
    }

    // 3. Profile connect URL
    trimmed.toUserIdFromClickUrl()?.let {
        return QrParseResult.Legacy(it)
    }

    // 4. Legacy JSON
    trimmed.toQrPayloadOrNull()?.userId?.takeIf { it.isNotBlank() }?.let {
        return QrParseResult.Legacy(it)
    }

    // 5. Invalid
    return QrParseResult.Invalid
}
