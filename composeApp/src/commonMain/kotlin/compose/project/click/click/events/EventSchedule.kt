package compose.project.click.click.events

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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

fun eventReminderBody(kind: EventReminderKind, eventDescription: String): String {
    val label = eventDescription.trim().take(80).ifEmpty { "Your event" }
    return when (kind) {
        EventReminderKind.DayOf -> "$label starts today — tap to view on the map."
        EventReminderKind.OneHourBefore -> "$label starts in about an hour."
    }
}
