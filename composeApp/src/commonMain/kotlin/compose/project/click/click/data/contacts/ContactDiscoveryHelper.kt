package compose.project.click.click.data.contacts // pragma: allowlist secret

import compose.project.click.click.crypto.PlatformCrypto // pragma: allowlist secret

enum class KnownSinceBucket {
    Childhood,
    HighSchool,
    College,
    ThisYear,
    Unspecified,
    ;

    val apiValue: String
        get() =
            when (this) {
                Childhood -> "childhood"
                HighSchool -> "high_school"
                College -> "college"
                ThisYear -> "this_year"
                Unspecified -> "unspecified"
            }

    val label: String
        get() =
            when (this) {
                Childhood -> "Childhood"
                HighSchool -> "High School"
                College -> "College"
                ThisYear -> "This Year"
                Unspecified -> "Unspecified"
            }

    companion object {
        fun fromApi(value: String?): KnownSinceBucket =
            when (value?.trim()?.lowercase()) {
                "childhood" -> Childhood
                "high_school" -> HighSchool
                "college" -> College
                "this_year" -> ThisYear
                else -> Unspecified
            }
    }
}

/**
 * On-device hashing for privacy-first contact matching.
 * Plaintext emails/phones never leave this helper as return values — only SHA-256 hex.
 */
object ContactDiscoveryHelper {
    const val PRIOR_BADGE_LABEL = "Prior Connection · Self-Reported"
    const val TIMELINE_EMPTY_COPY =
        "No shared physical encounters logged yet. Timeline begins upon your first real-world Handshake."

    fun normalizeEmail(raw: String): String? {
        val trimmed = raw.trim().lowercase()
        if (trimmed.length < 3 || !trimmed.contains('@')) return null
        return trimmed
    }

    fun normalizePhoneE164(raw: String): String? {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length < 10) return null
        if (trimmed.startsWith('+')) return "+$digits"
        if (digits.length == 10) return "+1$digits"
        if (digits.length == 11 && digits.startsWith('1')) return "+$digits"
        return "+$digits"
    }

    fun hashUtf8(value: String): String = PlatformCrypto.sha256(value.encodeToByteArray()).toHexLower()

    fun hashesFromRaw(
        emails: Iterable<String>,
        phones: Iterable<String>,
    ): List<String> {
        val out = LinkedHashSet<String>()
        for (email in emails) {
            val normalized = normalizeEmail(email) ?: continue
            out.add(hashUtf8(normalized))
        }
        for (phone in phones) {
            val normalized = normalizePhoneE164(phone) ?: continue
            out.add(hashUtf8(normalized))
        }
        return out.toList()
    }
}

internal fun ByteArray.toHexLower(): String {
    val alphabet = "0123456789abcdef"
    val hex = CharArray(size * 2)
    for (i in indices) {
        val v = this[i].toInt() and 0xFF
        hex[i * 2] = alphabet[v ushr 4]
        hex[i * 2 + 1] = alphabet[v and 0x0F]
    }
    return hex.concatToString()
}
