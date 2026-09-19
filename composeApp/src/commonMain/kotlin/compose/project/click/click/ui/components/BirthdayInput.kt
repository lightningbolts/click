package compose.project.click.click.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import compose.project.click.click.events.localDateToUtcMidnightMillis
import compose.project.click.click.events.utcMidnightMillisToLocalDate
import kotlinx.datetime.LocalDate

/** Returns the eight editable date digits used as the text field's source of truth. */
fun birthdayDigitsInput(raw: String): String {
    val trimmed = raw.trim().replace('/', '-')
    val cut =
        when {
            trimmed.contains('T') -> trimmed.substringBefore('T')
            trimmed.contains(' ') -> trimmed.substringBefore(' ')
            else -> trimmed
        }
    return cut.filter(Char::isDigit).take(8)
}

/**
 * Formats birthday digits as ISO date text. This is for parsing/persistence and pasted input;
 * interactive fields keep [birthdayDigitsInput] as state and render separators visually.
 */
fun formatBirthdayDigitsInput(raw: String): String {
    val digits = birthdayDigitsInput(raw)
    return buildString {
        digits.forEachIndexed { i, c ->
            if (i == 4 || i == 6) append('-')
            append(c)
        }
    }
}

/**
 * Visual-only YYYY-MM-DD formatter. Because dashes are not stored in the editable value,
 * backspace/delete never gets trapped re-inserting a separator.
 */
object BirthdayVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = birthdayDigitsInput(text.text)
        val formatted = formatBirthdayDigitsInput(digits)
        return TransformedText(
            text = AnnotatedString(formatted),
            offsetMapping =
                object : OffsetMapping {
                    override fun originalToTransformed(offset: Int): Int {
                        val safe = offset.coerceIn(0, digits.length)
                        val mapped =
                            when {
                                safe <= 4 -> safe
                                safe <= 6 -> safe + 1
                                else -> safe + 2
                            }
                        return mapped.coerceAtMost(formatted.length)
                    }

                    override fun transformedToOriginal(offset: Int): Int {
                        val safe = offset.coerceIn(0, formatted.length)
                        val mapped =
                            when {
                                safe <= 4 -> safe
                                safe == 5 -> 4
                                safe <= 7 -> safe - 1
                                safe == 8 -> 6
                                else -> safe - 2
                            }
                        return mapped.coerceIn(0, digits.length)
                    }
                },
        )
    }
}

fun parseBirthdayIsoLocalDate(raw: String): LocalDate? {
    val formatted = formatBirthdayDigitsInput(raw)
    if (formatted.length != 10) return null
    return runCatching { LocalDate.parse(formatted) }.getOrNull()
}

fun birthdayIsoToUtcMidnightMillis(iso: String): Long? = parseBirthdayIsoLocalDate(iso)?.let { localDateToUtcMidnightMillis(it) }

fun utcMidnightMillisToBirthdayIso(ms: Long): String = utcMidnightMillisToLocalDate(ms).toString()
