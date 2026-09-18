package compose.project.click.click.ui.components

import compose.project.click.click.events.localDateToUtcMidnightMillis
import compose.project.click.click.events.utcMidnightMillisToLocalDate
import kotlinx.datetime.LocalDate

/**
 * Strip to digits (max 8) and auto-insert dashes: `20000417` → `2000-04-17`.
 * Also accepts pasted `YYYY/MM/DD` / ISO datetime prefixes.
 */
fun formatBirthdayDigitsInput(raw: String): String {
    val trimmed = raw.trim().replace('/', '-')
    val cut = when {
        trimmed.contains('T') -> trimmed.substringBefore('T')
        trimmed.contains(' ') -> trimmed.substringBefore(' ')
        else -> trimmed
    }
    val digits = cut.filter { it.isDigit() }.take(8)
    return buildString {
        digits.forEachIndexed { i, c ->
            if (i == 4 || i == 6) append('-')
            append(c)
        }
    }
}

/**
 * Applies an interactive edit without making auto-inserted separators feel undeletable.
 *
 * When a user deletes one of the visual dashes, treat that backspace as deleting the
 * preceding date digit as well. Other edits and paste operations continue through the
 * canonical normalizer.
 */
fun editBirthdayDigitsInput(
    previous: String,
    incoming: String,
): String {
    val previousFormatted = formatBirthdayDigitsInput(previous)
    if (incoming.length == previousFormatted.length - 1) {
        val firstDifference =
            previousFormatted.indices.firstOrNull { index ->
                index >= incoming.length || previousFormatted[index] != incoming[index]
            } ?: incoming.length
        if (firstDifference < previousFormatted.length && previousFormatted[firstDifference] == '-') {
            val digits = previousFormatted.filter(Char::isDigit).toMutableList()
            val digitIndexBeforeSeparator =
                previousFormatted
                    .take(firstDifference)
                    .count(Char::isDigit) - 1
            if (digitIndexBeforeSeparator in digits.indices) {
                digits.removeAt(digitIndexBeforeSeparator)
                return formatBirthdayDigitsInput(digits.joinToString(""))
            }
        }
    }
    return formatBirthdayDigitsInput(incoming)
}

fun parseBirthdayIsoLocalDate(raw: String): LocalDate? {
    val formatted = formatBirthdayDigitsInput(raw)
    if (formatted.length != 10) return null
    return runCatching { LocalDate.parse(formatted) }.getOrNull()
}

fun birthdayIsoToUtcMidnightMillis(iso: String): Long? =
    parseBirthdayIsoLocalDate(iso)?.let { localDateToUtcMidnightMillis(it) }

fun utcMidnightMillisToBirthdayIso(ms: Long): String =
    utcMidnightMillisToLocalDate(ms).toString()
