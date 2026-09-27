package compose.project.click.click.data.models

import compose.project.click.click.util.formatRelativeDay
import compose.project.click.click.util.formatShortDateTime
import compose.project.click.click.util.formatWeekdayDateTime
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleSendTimingTest {
    private val minute = 60_000L

    @Test
    fun defaultIsNextQuarterHourAtLeastFifteenMinutesOut() {
        // 12:00:00 exactly -> 12:15.
        val noon = Instant.parse("2026-09-26T12:00:00Z").toEpochMilliseconds()
        assertEquals(noon + 15 * minute, ScheduleSendTiming.defaultSendAt(noon))
        // 12:01 -> 12:16 earliest -> 12:30.
        assertEquals(noon + 30 * minute, ScheduleSendTiming.defaultSendAt(noon + minute))
        // 12:14:59 -> 12:29:59 -> 12:30.
        assertEquals(noon + 30 * minute, ScheduleSendTiming.defaultSendAt(noon + 14 * minute + 59_000))
    }

    @Test
    fun validityMatchesServerWindow() {
        val now = 1_000_000_000_000L
        assertFalse(ScheduleSendTiming.isValid(now + 30_000, now))
        assertTrue(ScheduleSendTiming.isValid(now + minute, now))
        assertTrue(ScheduleSendTiming.isValid(now + ScheduleSendTiming.MAX_AHEAD_MS, now))
        assertFalse(ScheduleSendTiming.isValid(now + ScheduleSendTiming.MAX_AHEAD_MS + 1, now))
        assertEquals(now + minute, ScheduleSendTiming.clamp(now, now))
    }

    @Test
    fun dateLabels() {
        val utc = TimeZone.UTC
        val now = Instant.parse("2026-09-26T09:00:00Z").toEpochMilliseconds() // a Saturday
        val tonight = Instant.parse("2026-09-26T19:00:00Z").toEpochMilliseconds()
        assertEquals("Today", formatRelativeDay(tonight, now, utc))
        assertEquals("Tomorrow", formatRelativeDay(tonight + 86_400_000, now, utc))
        assertEquals("Wednesday", formatRelativeDay(tonight + 4 * 86_400_000, now, utc))
        assertEquals("Sat, Oct 3", formatRelativeDay(tonight + 7 * 86_400_000, now, utc))
        assertEquals("Sep 26, 7:00 PM", formatShortDateTime(tonight, utc))
        assertEquals("Sat, Sep 26, 7:00 PM", formatWeekdayDateTime(tonight, utc))
    }
}
