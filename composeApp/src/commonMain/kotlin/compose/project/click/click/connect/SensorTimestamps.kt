package compose.project.click.click.connect

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * ISO-8601 UTC with exactly three fractional digits (`2026-10-06T12:00:00.000Z`), the encoding
 * iOS uses (`ISO8601DateFormatter` + `.withFractionalSeconds`). `connection_moment` must be
 * byte-identical between `sensor_observation` and the follow-up bodies; the server matches
 * the two within ±2 ms.
 */
fun isoMillis(epochMs: Long): String {
    val t = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.UTC)
    val millis = ((epochMs % 1000L) + 1000L) % 1000L

    fun Int.pad(width: Int = 2) = toString().padStart(width, '0')
    return buildString {
        append(t.year.pad(4))
        append('-').append(t.monthNumber.pad())
        append('-').append(t.dayOfMonth.pad())
        append('T').append(t.hour.pad())
        append(':').append(t.minute.pad())
        append(':').append(t.second.pad())
        append('.').append(millis.toString().padStart(3, '0'))
        append('Z')
    }
}
