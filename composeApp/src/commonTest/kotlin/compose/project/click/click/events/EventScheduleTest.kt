package compose.project.click.click.events

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
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
    fun isLive_onlyDuringWindow() {
        val schedule = EventSchedule(startEpochMs = 100L, endEpochMs = 200L)
        assertFalse(schedule.isLive(nowEpochMs = 99L))
        assertTrue(schedule.isLive(nowEpochMs = 100L))
        assertTrue(schedule.isLive(nowEpochMs = 150L))
        assertFalse(schedule.isLive(nowEpochMs = 200L))
    }

    @Test
    fun formatEventStartEndTimeLabels_useClockOnly() {
        val tz = TimeZone.UTC
        val schedule =
            EventSchedule(
                startEpochMs = Instant.parse("2026-06-12T19:00:00Z").toEpochMilliseconds(),
                endEpochMs = Instant.parse("2026-06-13T00:00:00Z").toEpochMilliseconds(),
            )
        assertEquals("7:00 PM", formatEventStartTimeLabel(schedule, tz))
        assertEquals("12:00 AM", formatEventEndTimeLabel(schedule, tz))
        assertEquals("Jun 12", formatEventStartDateLabel(schedule, tz))
        assertEquals("Jun 13", formatEventEndDateLabel(schedule, tz))
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

    @Test
    fun formatEventScheduleRange_sameDay_usesCompactEndTime() {
        val tz = TimeZone.UTC
        val schedule =
            EventSchedule(
                startEpochMs = Instant.parse("2026-06-12T19:00:00Z").toEpochMilliseconds(),
                endEpochMs = Instant.parse("2026-06-12T21:00:00Z").toEpochMilliseconds(),
            )
        val label = formatEventScheduleRange(schedule, tz)
        assertTrue(label.contains("Jun 12"))
        assertTrue(label.contains("7:00 PM"))
        assertTrue(label.contains("9:00 PM"))
        assertTrue(label.contains("–"))
    }

    @Test
    fun eventScheduleMetadata_includesEventTimezone() {
        val schedule = EventSchedule(startEpochMs = 1_000L, endEpochMs = 2_000L)
        val meta = eventScheduleMetadata(schedule)
        assertTrue(meta.containsKey("event_timezone"))
        assertEquals(TimeZone.currentSystemDefault().id, meta["event_timezone"]?.jsonPrimitive?.contentOrNull)
    }
}
