package compose.project.click.click.events

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

data class EventClock12h(
    val hour12: Int,
    val minute: Int,
    val isPm: Boolean,
) {
    init {
        require(hour12 in 1..12)
        require(minute in 0..59)
    }
}

fun eventClock12hFrom24h(hour24: Int, minute: Int): EventClock12h {
    val h = hour24.coerceIn(0, 23)
    val m = minute.coerceIn(0, 59)
    val isPm = h >= 12
    val hour12 = when (val mod = h % 12) {
        0 -> 12
        else -> mod
    }
    return EventClock12h(hour12 = hour12, minute = m, isPm = isPm)
}

fun eventClock12hTo24h(clock: EventClock12h): Pair<Int, Int> {
    val hour24 = when {
        clock.hour12 == 12 && !clock.isPm -> 0
        clock.hour12 == 12 && clock.isPm -> 12
        clock.isPm -> clock.hour12 + 12
        else -> clock.hour12
    }
    return hour24 to clock.minute.coerceIn(0, 59)
}

fun formatEventClockLabel(hour: Int, minute: Int): String {
    val clock = eventClock12hFrom24h(hour, minute)
    val period = if (clock.isPm) "PM" else "AM"
    return "${clock.hour12}:${clock.minute.toString().padStart(2, '0')} $period"
}

fun formatEventDateOnlyLabel(
    epochMs: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val date = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(timeZone).date
    return formatPickerDateOnly(date)
}

fun formatEventDateRangeLabel(
    startEpochMs: Long,
    endEpochMs: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val start = Instant.fromEpochMilliseconds(startEpochMs).toLocalDateTime(timeZone)
    val end = Instant.fromEpochMilliseconds(endEpochMs).toLocalDateTime(timeZone)
    val startLabel = formatPickerDateOnly(start.date)
    return if (start.date == end.date) {
        startLabel
    } else {
        "$startLabel – ${formatPickerDateOnly(end.date)}"
    }
}

private fun formatPickerDateOnly(date: LocalDate): String {
    val mon = date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    return "$mon ${date.dayOfMonth}"
}

/** Material date pickers use UTC midnight millis for calendar days. */
fun utcMidnightMillisToLocalDate(utcMidnightMs: Long): LocalDate =
    Instant.fromEpochMilliseconds(utcMidnightMs).toLocalDateTime(TimeZone.UTC).date

fun localDateToUtcMidnightMillis(date: LocalDate): Long =
    date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

fun mergeLocalDateWithClock(
    date: LocalDate,
    hour: Int,
    minute: Int,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Long {
    val dayStart = date.atStartOfDayIn(timeZone).toEpochMilliseconds()
    return dayStart + (hour.coerceIn(0, 23) * 60L + minute.coerceIn(0, 59)) * 60_000L
}

/**
 * When start/end fall on the same calendar day and [candidate] would invert the window,
 * nudge by one minute so end stays after start.
 */
fun coerceSameDayEventTimes(
    editingStart: Boolean,
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
): Pair<Pair<Int, Int>, Pair<Int, Int>> {
    var start = startHour.coerceIn(0, 23) * 60 + startMinute.coerceIn(0, 59)
    var end = endHour.coerceIn(0, 23) * 60 + endMinute.coerceIn(0, 59)
    if (end <= start) {
        if (editingStart) {
            start = (end - 1).coerceAtLeast(0)
        } else {
            end = (start + 1).coerceAtMost(24 * 60 - 1)
        }
    }
    return (start / 60 to start % 60) to (end / 60 to end % 60)
}
