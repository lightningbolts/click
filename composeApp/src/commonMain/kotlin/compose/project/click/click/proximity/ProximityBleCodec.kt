package compose.project.click.click.proximity

/**
 * BLE manufacturer payload for tri-factor handshake tokens (4 decimal digits).
 * Prefix bytes are ASCII "CK" so scans can ignore unrelated advertisements.
 */
internal const val CLICK_BLE_MANUFACTURER_ID: Int = 0xCAFE

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
