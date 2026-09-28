package compose.project.click.click.events

import kotlinx.serialization.Serializable

/** `recurrence.frequency` values accepted by `POST /api/beacons` (click-web `eventRecurrence.ts`). */
enum class EventRecurrenceFrequency(
    val apiValue: String,
    val label: String,
) {
    DAILY("daily", "Daily"),
    WEEKLY("weekly", "Weekly"),
    BIWEEKLY("biweekly", "Every 2 weeks"),
    MONTHLY("monthly", "Monthly"),
}

/** Total occurrences, including the first (server bounds). */
val EVENT_OCCURRENCE_RANGE = 2..26
const val DEFAULT_EVENT_OCCURRENCES = 4

/** Wire body: the server creates [count] events sharing one series, each with its own hub. */
@Serializable
data class EventRecurrence(
    val frequency: String,
    val count: Int,
)

private const val DAY_MS = 24L * 60L * 60_000L

/** Mirrors the server: an occurrence must end before the next one can start. */
fun eventRecurrenceValidationError(
    schedule: EventSchedule,
    frequency: EventRecurrenceFrequency?,
): String? {
    val minIntervalMs =
        when (frequency) {
            null -> return null
            EventRecurrenceFrequency.DAILY -> DAY_MS
            EventRecurrenceFrequency.WEEKLY -> 7 * DAY_MS
            EventRecurrenceFrequency.BIWEEKLY -> 14 * DAY_MS
            EventRecurrenceFrequency.MONTHLY -> 28 * DAY_MS
        }
    return if (schedule.endEpochMs - schedule.startEpochMs > minIntervalMs) {
        "A repeating event must end before the next one starts."
    } else {
        null
    }
}
