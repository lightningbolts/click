@file:OptIn(kotlin.time.ExperimentalTime::class)

package compose.project.click.click.events

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Maximum scheduled event duration (30 days). */
const val MAX_EVENT_DURATION_MS = 30L * 24L * 60L * 60_000L

data class EventSchedule(
    val startEpochMs: Long,
    val endEpochMs: Long,
)

enum class EventScheduleValidationError {
    EndBeforeStart,
    StartInPast,
    DurationExceedsOneMonth,
}

fun validateEventSchedule(
    startEpochMs: Long,
    endEpochMs: Long,
    nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
): EventScheduleValidationError? {
    if (endEpochMs <= startEpochMs) return EventScheduleValidationError.EndBeforeStart
    if (startEpochMs < nowEpochMs - 60_000L) return EventScheduleValidationError.StartInPast
    if (endEpochMs - startEpochMs > MAX_EVENT_DURATION_MS) {
        return EventScheduleValidationError.DurationExceedsOneMonth
    }
    return null
}

fun eventScheduleMetadata(
    schedule: EventSchedule,
): JsonObject =
    buildJsonObject {
        put("event_start_at", Instant.fromEpochMilliseconds(schedule.startEpochMs).toString())
        put("event_end_at", Instant.fromEpochMilliseconds(schedule.endEpochMs).toString())
    }

fun parseEventScheduleFromMetadata(raw: JsonObject?): EventSchedule? {
    if (raw == null) return null
    fun parseInstant(key: String): Long? {
        val text = raw[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        return runCatching { Instant.parse(text).toEpochMilliseconds() }.getOrNull()
    }
    val start = parseInstant("event_start_at") ?: parseInstant("eventStartAt") ?: return null
    val end = parseInstant("event_end_at") ?: parseInstant("eventEndAt") ?: return null
    return EventSchedule(startEpochMs = start, endEpochMs = end)
}

fun EventSchedule.isEnded(nowEpochMs: Long = Clock.System.now().toEpochMilliseconds()): Boolean =
    nowEpochMs >= endEpochMs

fun EventSchedule.isVisible(nowEpochMs: Long = Clock.System.now().toEpochMilliseconds()): Boolean =
    !isEnded(nowEpochMs)

/** Compact range for discovery cards and beacon detail sheets, e.g. `Jun 12, 7:00 PM – 9:00 PM`. */
fun formatEventScheduleRange(
    schedule: EventSchedule,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val start = Instant.fromEpochMilliseconds(schedule.startEpochMs).toLocalDateTime(timeZone)
    val end = Instant.fromEpochMilliseconds(schedule.endEpochMs).toLocalDateTime(timeZone)
    val startLabel = formatEventDateTimeLabel(start)
    val endLabel = if (start.date == end.date) {
        formatEventTimeLabel(end)
    } else {
        formatEventDateTimeLabel(end)
    }
    return "$startLabel – $endLabel"
}

private fun formatEventDateTimeLabel(dt: kotlinx.datetime.LocalDateTime): String {
    val mon = dt.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    return "$mon ${dt.dayOfMonth}, ${formatEventTimeLabel(dt)}"
}

private fun formatEventTimeLabel(dt: kotlinx.datetime.LocalDateTime): String {
    val hour24 = dt.hour
    val h12 = ((hour24 + 11) % 12) + 1
    val amPm = if (hour24 < 12) "AM" else "PM"
    return "$h12:${dt.minute.toString().padStart(2, '0')} $amPm"
}

enum class EventReminderKind {
    DayOf,
    OneHourBefore,
}

/**
 * Returns reminder kinds that should fire at [nowEpochMs] (within a 15-minute cron window).
 */
fun eventReminderKindsDue(
    schedule: EventSchedule,
    nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
    windowMs: Long = 15L * 60_000L,
): Set<EventReminderKind> {
    val due = mutableSetOf<EventReminderKind>()
    val startMs = schedule.startEpochMs
    val dayOfWindowStart = startOfLocalDayEpochMs(startMs)
    if (nowEpochMs in dayOfWindowStart until dayOfWindowStart + windowMs) {
        due += EventReminderKind.DayOf
    }
    val oneHourBefore = startMs - 60L * 60_000L
    if (nowEpochMs in oneHourBefore until oneHourBefore + windowMs) {
        due += EventReminderKind.OneHourBefore
    }
    return due
}

/** Start of UTC day for [epochMs] — sufficient for cron + in-app day-of reminders. */
internal fun startOfLocalDayEpochMs(epochMs: Long): Long {
    val dayMs = 24L * 60L * 60_000L
    return (epochMs / dayMs) * dayMs
}

fun eventReminderTitle(kind: EventReminderKind, eventDescription: String): String =
    when (kind) {
        EventReminderKind.DayOf -> "Event today"
        EventReminderKind.OneHourBefore -> "Event starting soon"
    }

fun eventReminderBody(kind: EventReminderKind, eventDescription: String, eventTitle: String? = null): String {
    val label = eventTitle?.trim()?.take(80)?.takeIf { it.isNotEmpty() }
        ?: eventDescription.trim().take(80).ifEmpty { "Your event" }
    return when (kind) {
        EventReminderKind.DayOf -> "$label starts today — tap to view on the map."
        EventReminderKind.OneHourBefore -> "$label starts in about an hour."
    }
}

/** Rounds up to the next whole hour in [timeZone] (e.g. 2:37 → 3:00). */
fun roundEpochToNextWholeHour(
    epochMs: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Long {
    val local = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(timeZone)
    val dayStartMs = local.date.atStartOfDayIn(timeZone).toEpochMilliseconds()
    val hourMs = 60L * 60_000L
    val msIntoDay = (epochMs - dayStartMs).coerceAtLeast(0L)
    val hourIndex = ((msIntoDay + hourMs - 1) / hourMs).toInt()
    if (hourIndex >= 24) {
        val nextDayStart = local.date.plus(kotlinx.datetime.DatePeriod(days = 1)).atStartOfDayIn(timeZone)
        return nextDayStart.toEpochMilliseconds()
    }
    var candidate = dayStartMs + hourIndex * hourMs
    if (candidate <= epochMs) {
        candidate += hourMs
    }
    return candidate
}

/**
 * Default event window: next nice on-the-hour start (≥45 min lead time) and a 2-hour block
 * ending on the hour (e.g. 7:00 PM – 9:00 PM).
 */
fun defaultEventSchedule(
    nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
    durationHours: Int = 2,
): EventSchedule {
    val minLeadMs = 45L * 60_000L
    val start = roundEpochToNextWholeHour(nowEpochMs + minLeadMs)
    val end = start + durationHours.coerceAtLeast(1).coerceAtMost(24) * 60L * 60_000L
    return EventSchedule(startEpochMs = start, endEpochMs = end)
}
