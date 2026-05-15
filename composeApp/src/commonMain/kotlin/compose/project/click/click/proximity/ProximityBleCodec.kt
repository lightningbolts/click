package compose.project.click.click.proximity

/**
 * BLE manufacturer payload for tri-factor handshake tokens (4 decimal digits).
 * Prefix bytes are ASCII "CK" so scans can ignore unrelated advertisements.
 */
internal const val CLICK_BLE_MANUFACTURER_ID: Int = 0xCAFE
internal const val CLICK_SERVICE_UUID: String = "6f1c8c2a-1111-4000-8000-00cafe000001"
internal const val CLICK_TOKEN_CHARACTERISTIC_UUID: String = "6f1c8c2a-2222-4000-8000-00cafe000001"
private const val CLICK_SERVICE_UUID_PREFIX = "6f1c8c2a"
private const val CLICK_SERVICE_UUID_SUFFIX = "4000-8000-00cafe000001"

internal fun normalizeGattHandshakeToken(raw: String): String? =
    raw.trim().takeIf { it.isNotEmpty() }

internal fun buildGattTokenPayload(token: String): ByteArray {
    val normalized = normalizeGattHandshakeToken(token) ?: error("Invalid handshake token")
    return normalized.encodeToByteArray()
}

internal fun parseGattTokenPayload(data: ByteArray?): String? {
    if (data == null || data.isEmpty()) return null
    return runCatching { data.decodeToString() }
        .getOrNull()
        ?.let(::normalizeGattHandshakeToken)
}

internal fun normalizeHandshakeToken(raw: String): String? {
    val digits = raw.filter { it.isDigit() }.takeLast(4).padStart(4, '0')
    return if (digits.length == 4) digits else null
}

internal fun buildBleManufacturerPayload(token: String): ByteArray {
    val n = normalizeHandshakeToken(token) ?: error("Invalid handshake token")
    return byteArrayOf(0x43, 0x4B) + n.map { it.code.toByte() }.toByteArray()
}

internal fun parseBleManufacturerPayload(data: ByteArray?): String? {
    if (data == null || data.size < 6) return null
    if (data[0] != 0x43.toByte() || data[1] != 0x4B.toByte()) return null
    val s = buildString {
        for (i in 2 until minOf(6, data.size)) {
            append(data[i].toInt().toChar())
        }
    }
    return normalizeHandshakeToken(s)
}

internal fun buildProximityServiceUuidString(token: String): String {
    val n = normalizeHandshakeToken(token) ?: error("Invalid handshake token")
    return "$CLICK_SERVICE_UUID_PREFIX-$n-$CLICK_SERVICE_UUID_SUFFIX"
}

internal fun parseProximityServiceUuidString(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val parts = raw.lowercase().split('-')
    if (parts.size != 5) return null
    if (parts[0] != CLICK_SERVICE_UUID_PREFIX || parts.drop(2).joinToString("-") != CLICK_SERVICE_UUID_SUFFIX) {
        return null
    }
    return normalizeHandshakeToken(parts[1])
}
