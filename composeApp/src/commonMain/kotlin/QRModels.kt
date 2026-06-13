package compose.project.click.click.qr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Base URL for the Click web app — used when generating profile QR codes. */
const val CLICK_WEB_BASE_URL = "https://click-us.vercel.app"

/** App Store listing for the full Click iOS app (App Clip CTA). */
const val CLICK_IOS_APP_STORE_ID = "6757996346"
const val CLICK_IOS_APP_STORE_URL = "https://apps.apple.com/app/id$CLICK_IOS_APP_STORE_ID"

/** Universal Link path segment for connection routing (`/c/{userId}`). */
const val CONNECTION_PATH_SEGMENT = "c"

/** Builds the secure routing URL encoded in QR codes and Universal Links. */
fun buildConnectionUniversalLink(userId: String): String {
    val id = userId.trim()
    if (id.isBlank()) return ""
    return "$CLICK_WEB_BASE_URL/$CONNECTION_PATH_SEGMENT/$id"
}

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

/** Offline / immediate QR payload — always a Universal Link, never raw JSON. */
fun buildOfflineQrPayload(userId: String, name: String?): String =
    buildConnectionUniversalLink(userId)

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
private val UUID_CAPTURE = """([0-9a-fA-F\-]{36})"""
private val HTTP_C_PATTERN = Regex("""https?://[^/]+/c/$UUID_CAPTURE""")
private val DEEP_LINK_C_PATTERN = Regex("""click://c/$UUID_CAPTURE""")
private val HTTP_CONNECT_PATTERN = Regex("""https?://[^/]+/connect/$UUID_CAPTURE""")
private val DEEP_LINK_PATTERN = Regex("""click://connect/$UUID_CAPTURE""")

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
    HTTP_C_PATTERN.find(this)?.groupValues?.getOrNull(1)?.let { return it }
    DEEP_LINK_C_PATTERN.find(this)?.groupValues?.getOrNull(1)?.let { return it }
    HTTP_CONNECT_PATTERN.find(this)?.groupValues?.getOrNull(1)?.let { return it }
    DEEP_LINK_PATTERN.find(this)?.groupValues?.getOrNull(1)?.let { return it }
    return null
}

private val UNIVERSAL_LINK_C_UUID = Regex(
    """/c/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})""",
    RegexOption.IGNORE_CASE,
)
private val TOKEN_QUERY_PARAM = Regex("""(?:[?&])(?:token|qr_token|qt)=([^&#]+)""", RegexOption.IGNORE_CASE)
private val EXP_QUERY_PARAM = Regex("""(?:[?&])(?:exp|expires_at)=([0-9]+)""", RegexOption.IGNORE_CASE)
private val ISSUED_AT_QUERY_PARAM = Regex("""(?:[?&])(?:iat|issued_at)=([0-9]+)""", RegexOption.IGNORE_CASE)
private val VENUE_QUERY_PARAM = Regex("""(?:[?&])venue_id=([^&#]+)""", RegexOption.IGNORE_CASE)

private fun String.queryValue(pattern: Regex): String? =
    pattern.find(this)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }

private fun String.hasTokenQueryParam(): Boolean = queryValue(TOKEN_QUERY_PARAM) != null

/**
 * Token-bearing Universal/App Clip link.
 *
 * The QR must stay link-shaped for OS camera and App Clip routing, but the scanner still needs
 * the short-lived token so it can redeem proximity/GPS server-side instead of using legacy flow.
 */
fun String.toTokenQrPayloadFromClickLinkOrNull(): TokenQrPayload? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return null
    val token = trimmed.queryValue(TOKEN_QUERY_PARAM) ?: return null
    val userId = trimmed.toUserIdFromClickUrl() ?: return null
    val exp = trimmed.queryValue(EXP_QUERY_PARAM)?.toLongOrNull() ?: return null
    return TokenQrPayload(
        token = token,
        userId = userId,
        exp = exp,
        issuedAt = trimmed.queryValue(ISSUED_AT_QUERY_PARAM)?.toLongOrNull(),
        venueId = trimmed.queryValue(VENUE_QUERY_PARAM),
    )
}

/**
 * Polyglot QR payload parser — extracts a connection [userId] UUID from either:
 * - **Branch A (new):** Universal Link URLs (`https://…/c/{uuid}`)
 * - **Branch B (legacy):** JSON `{"userId":"…", …}`
 *
 * Returns `null` when neither format matches. Safe to call from a background dispatcher.
 */
fun parseQrPayload(rawPayload: String): String? {
    val trimmed = rawPayload.trim()
    if (trimmed.isEmpty()) return null

    if (trimmed.startsWith("http", ignoreCase = true) && trimmed.contains("/c/")) {
        if (trimmed.hasTokenQueryParam()) return null
        UNIVERSAL_LINK_C_UUID.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        trimmed.toUserIdFromClickUrl()?.let { return it }
    }

    if (trimmed.startsWith("{") && !trimmed.contains("\"token\"")) {
        try {
            trimmed.toQrPayloadOrNull()?.userId?.takeIf { it.isNotBlank() }?.let { return it }
        } catch (_: Exception) {
            // Legacy JSON branch failed — fall through to null.
        }
    }

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
 * 3. URL format (https://.../c/{uuid}, legacy /connect/{uuid}, or click:// variants)
 * 4. Legacy JSON payload ({"userId":"...", ...})
 * 5. Invalid
 */
fun parseQrCode(rawData: String): QrParseResult {
    val trimmed = rawData.trim()

    // 1. Try token-based JSON  
    trimmed.toTokenQrPayloadOrNull()?.let {
        return QrParseResult.TokenBased(it)
    }

    // 2. Try token-bearing Universal/App Clip link
    trimmed.toTokenQrPayloadFromClickLinkOrNull()?.let {
        return QrParseResult.TokenBased(it)
    }

    // 3. Community hub
    trimmed.toHubIdFromClickHubUrl()?.let { hubId ->
        return QrParseResult.CommunityHub(hubId)
    }

    // 4. Polyglot parser — Universal Link `/c/{uuid}` or legacy JSON `{"userId":…}`
    parseQrPayload(trimmed)?.let {
        return QrParseResult.Legacy(it)
    }

    // 5. Legacy connect URLs (`/connect/{uuid}`, `click://connect/{uuid}`)
    trimmed.toUserIdFromClickUrl()?.let {
        return QrParseResult.Legacy(it)
    }

    // 6. Invalid
    return QrParseResult.Invalid
}
