package compose.project.click.click.data.repository

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private val BASE64_VALUE_PATTERN = Regex("^[A-Za-z0-9+/=_-]+$")
private val JSON_BASE64_FIELD_PATTERN = Regex("\"(?:data|blob|payload|content)\"\\s*:\\s*\"([^\"]+)\"")

internal fun normalizeEncryptedMediaPayload(rawPayload: ByteArray): ByteArray {
    if (rawPayload.isEmpty()) return rawPayload
    if (!rawPayload.looksLikeMostlyPrintableText()) return rawPayload

    val text = runCatching { rawPayload.decodeToString().trim() }.getOrNull() ?: return rawPayload
    if (text.isEmpty()) return rawPayload

    val candidate = extractBase64Candidate(text) ?: return rawPayload
    val compact = candidate.filterNot(Char::isWhitespace)
    if (compact.isEmpty()) return rawPayload
    if (!BASE64_VALUE_PATTERN.matches(compact)) return rawPayload

    val decoded = decodeBase64Flexible(compact) ?: return rawPayload
    return if (decoded.isNotEmpty()) decoded else rawPayload
}

private fun ByteArray.looksLikeMostlyPrintableText(): Boolean {
    if (isEmpty()) return false
    var printable = 0
    for (b in this) {
        val c = b.toInt() and 0xFF
        if (c == 9 || c == 10 || c == 13 || c in 32..126) printable += 1
    }
    return printable * 100 >= size * 92
}

private fun extractBase64Candidate(text: String): String? {
    val dataUrlCandidate = text.substringAfter("base64,", missingDelimiterValue = "")
    if (text.startsWith("data:", ignoreCase = true) && dataUrlCandidate.isNotBlank()) {
        return dataUrlCandidate
    }

    val jsonCandidate = JSON_BASE64_FIELD_PATTERN.find(text)?.groupValues?.getOrNull(1)
    if (!jsonCandidate.isNullOrBlank()) return jsonCandidate

    val unquoted = text.removeSurrounding("\"")
    return unquoted.takeIf { it.isNotBlank() }
}

@OptIn(ExperimentalEncodingApi::class)
private fun decodeBase64Flexible(value: String): ByteArray? {
    runCatching { Base64.decode(value) }.getOrNull()?.let { return it }

    val urlSafe = value.replace('-', '+').replace('_', '/')
    val padded = when (urlSafe.length % 4) {
        0 -> urlSafe
        else -> urlSafe + "=".repeat(4 - (urlSafe.length % 4))
    }
    return runCatching { Base64.decode(padded) }.getOrNull()
}
