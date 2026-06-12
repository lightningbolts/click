package compose.project.click.click.events

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class EventScheduleTest {

    @Test
    fun validateEventSchedule_rejectsEndBeforeStart() {
        assertEquals(
            EventScheduleValidationError.EndBeforeStart,
            validateEventSchedule(startEpochMs = 5_000L, endEpochMs = 4_000L, nowEpochMs = 0L),
        )
    }

    @Test
    fun validateEventSchedule_rejectsDurationOverOneMonth() {
        val start = 0L
        val end = start + MAX_EVENT_DURATION_MS + 1L
        assertEquals(
            EventScheduleValidationError.DurationExceedsOneMonth,
            validateEventSchedule(start, end, nowEpochMs = 0L),
        )
    }

    @Test
    fun validateEventSchedule_acceptsOneMonthWindow() {
        val start = 60_000L
        val end = start + MAX_EVENT_DURATION_MS
        assertNull(validateEventSchedule(start, end, nowEpochMs = 0L))
    }

    @Test
    fun isEnded_hidesEventAfterEndTime() {
        val schedule = EventSchedule(startEpochMs = 100L, endEpochMs = 200L)
        assertFalse(schedule.isEnded(nowEpochMs = 150L))
        assertTrue(schedule.isEnded(nowEpochMs = 200L))
    }

    @Test
    fun eventReminderKindsDue_includesDayOfAndOneHourBefore() {
        val start = 24L * 60 * 60_000L + 60L * 60_000L
        val schedule = EventSchedule(startEpochMs = start, endEpochMs = start + 60L * 60_000L)
        val dayOf = eventReminderKindsDue(schedule, nowEpochMs = startOfLocalDayEpochMs(start))
        assertTrue(dayOf.contains(EventReminderKind.DayOf))
        val oneHour = eventReminderKindsDue(schedule, nowEpochMs = start - 60L * 60_000L)
        assertTrue(oneHour.contains(EventReminderKind.OneHourBefore))
    }

    @Test
    fun defaultEventSchedule_usesOnTheHourTimes() {
        val tz = TimeZone.UTC
        val dayStart = Instant.parse("2026-06-11T00:00:00Z").toEpochMilliseconds()
        val nowMs = dayStart + (14 * 60 + 37) * 60_000L
        val schedule = defaultEventSchedule(nowEpochMs = nowMs)
        val startLocal = Instant.fromEpochMilliseconds(schedule.startEpochMs).toLocalDateTime(tz)
        val endLocal = Instant.fromEpochMilliseconds(schedule.endEpochMs).toLocalDateTime(tz)
        assertEquals(0, startLocal.minute)
        assertEquals(0, endLocal.minute)
        assertEquals(2, endLocal.hour - startLocal.hour)
        assertTrue(schedule.startEpochMs >= nowMs + 45 * 60_000L)
    }

    @Test
    fun roundEpochToNextWholeHour_roundsUp() {
        val tz = TimeZone.UTC
        val dayStart = Instant.parse("2026-06-11T00:00:00Z").toEpochMilliseconds()
        val hourMs = 60L * 60_000L
        val base = dayStart + 10 * hourMs + 37 * 60_000L
        val rounded = roundEpochToNextWholeHour(base, tz)
        assertEquals(dayStart + 11 * hourMs, rounded)
    }
}
