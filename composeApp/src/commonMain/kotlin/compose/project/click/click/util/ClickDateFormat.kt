package compose.project.click.click.util

import compose.project.click.click.events.formatEventClockLabel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/*
 * Human date labels shared by Send Later, plans and hangouts (en-US style, like the rest of the
 * app): "Today", "Tomorrow", "Saturday", "Sat, Oct 4", "7:00 PM".
 */

private fun LocalDateTime.shortMonth(): String =
    month.name
        .lowercase()
        .replaceFirstChar { it.uppercase() }
        .take(3)

private fun LocalDateTime.shortWeekday(): String =
    dayOfWeek.name
        .lowercase()
        .replaceFirstChar { it.uppercase() }
        .take(3)

private fun LocalDateTime.longWeekday(): String =
    dayOfWeek.name
        .lowercase()
        .replaceFirstChar { it.uppercase() }

/** "7:00 PM". */
fun formatClockTime(
    epochMs: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = formatEventClockLabel(Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(timeZone))

/** "Sat, Oct 4". */
fun formatWeekdayDate(
    epochMs: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val dt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(timeZone)
    return "${dt.shortWeekday()}, ${dt.shortMonth()} ${dt.dayOfMonth}"
}

/** "Oct 4, 7:00 PM" (abbreviated date + short time). */
fun formatShortDateTime(
    epochMs: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val dt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(timeZone)
    return "${dt.shortMonth()} ${dt.dayOfMonth}, ${formatEventClockLabel(dt)}"
}

/** "Wed, Oct 1, 7:00 PM". */
fun formatWeekdayDateTime(
    epochMs: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = "${formatWeekdayDate(epochMs, timeZone)}, ${formatClockTime(epochMs, timeZone)}"

/**
 * Relative day name: "Today", "Tomorrow", a weekday within the next week, otherwise "Sat, Oct 4".
 * Past dates fall back to the absolute form.
 */
fun formatRelativeDay(
    epochMs: Long,
    nowEpochMs: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val target = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(timeZone)
    val today = Instant.fromEpochMilliseconds(nowEpochMs).toLocalDateTime(timeZone).date
    val days = today.daysUntil(target.date)
    return when {
        days == 0 -> "Today"
        days == 1 -> "Tomorrow"
        days in 2..6 -> target.longWeekday()
        else -> formatWeekdayDate(epochMs, timeZone)
    }
}

/** First day of [epochMs]'s week offset helper for pickers: the date [days] after today. */
fun localDateAfter(
    nowEpochMs: Long,
    days: Int,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) = Instant
    .fromEpochMilliseconds(nowEpochMs)
    .toLocalDateTime(timeZone)
    .date
    .plus(DatePeriod(days = days))
