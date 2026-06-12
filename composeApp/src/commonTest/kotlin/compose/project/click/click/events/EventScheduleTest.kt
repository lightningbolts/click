package compose.project.click.click.events

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
}
