package compose.project.click.click.proximity

import kotlin.random.Random

/**
 * BLE manufacturer payload for tri-factor handshake tokens (4 decimal digits).
 * Prefix bytes are ASCII "CK" so scans can ignore unrelated advertisements.
 */
internal const val CLICK_BLE_MANUFACTURER_ID: Int = 0xCAFE

/**
 * Primary GATT service for tap handshake. Advertisers expose a readable
 * characteristic under this service; scanners may connect and read when scan
 * payloads omit manufacturer data (e.g. iOS background).
 */
internal const val CLICK_PRIMARY_SERVICE_UUID_STRING: String =
    "6f1c8c2a-0000-4000-8000-00cafe000001"

/** Readable handshake payload characteristic under [CLICK_PRIMARY_SERVICE_UUID_STRING]. */
internal const val CLICK_TOKEN_CHARACTERISTIC_UUID_STRING: String =
    "6f1c8c2a-0000-4000-8000-00cafe000002"

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

/**
 * Packs the CK+digits manufacturer payload into the first six octets of a
 * random RFC-4122-style UUID string so it can appear in BLE service UUID lists
 * (iOS keeps these in foreground and background; manufacturer data may not).
 */
internal fun encodeBleTokenIntoAdvertisedUuidString(token: String): String {
    val payload = buildBleManufacturerPayload(token)
    require(payload.size == 6)
    val b = ByteArray(16)
    payload.copyInto(b, destinationOffset = 0, startIndex = 0, endIndex = 6)
    val r = Random.Default
    b[6] = ((r.nextInt(256) and 0x0F) or 0x40).toByte()
    b[7] = r.nextInt(256).toByte()
    b[8] = ((r.nextInt(256) and 0x3F) or 0x80).toByte()
    for (i in 9 until 16) {
        b[i] = r.nextInt(256).toByte()
    }
    return bleUuidBytesToString(b)
}

internal fun decodeBleTokenFromAdvertisedUuidString(uuid: String?): String? {
    val bytes = bleUuidStringToBytes(uuid) ?: return null
    if (bytes.size != 16) return null
    return parseBleManufacturerPayload(bytes.copyOfRange(0, 6))
}

internal fun bleUuidBytesToString(bytes: ByteArray): String {
    require(bytes.size == 16)
    fun hb(v: Int) = v.toString(16).padStart(2, '0')
    return buildString(36) {
        for (i in 0 until 16) {
            if (i == 4 || i == 6 || i == 8 || i == 10) append('-')
            append(hb(bytes[i].toInt() and 0xFF))
        }
    }
}

internal fun bleUuidStringToBytes(uuid: String?): ByteArray? {
    if (uuid == null) return null
    val hex = uuid.filter { it != '-' }
    if (hex.length != 32) return null
    val out = ByteArray(16)
    for (i in 0 until 16) {
        val hi = hex[i * 2].digitToIntOrNull(16) ?: return null
        val lo = hex[i * 2 + 1].digitToIntOrNull(16) ?: return null
        out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
}
