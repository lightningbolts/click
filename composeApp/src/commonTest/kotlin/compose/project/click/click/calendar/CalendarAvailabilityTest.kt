package compose.project.click.click.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarAvailabilityTest {

    @Test
    fun calculateAvailabilityOverlaps_findsGapLongerThan45Minutes() {
        val windowStart = 0L
        val windowEnd = 24 * 60 * 60 * 1000L
        val userCal = CalendarFreeBusy(
            busyBlocks = listOf(
                BusyBlock(0L, 2 * 60 * 60 * 1000L),
                BusyBlock(10 * 60 * 60 * 1000L, 12 * 60 * 60 * 1000L),
            ),
            windowStartEpochMs = windowStart,
            windowEndEpochMs = windowEnd,
        )
        val friendCal = CalendarFreeBusy(
            busyBlocks = listOf(
                BusyBlock(0L, 90 * 60 * 1000L),
                BusyBlock(11 * 60 * 60 * 1000L, 13 * 60 * 60 * 1000L),
            ),
            windowStartEpochMs = windowStart,
            windowEndEpochMs = windowEnd,
        )
        val overlaps = calculateAvailabilityOverlaps(userCal, friendCal, minGapMinutes = 45)
        assertTrue(overlaps.isNotEmpty())
        val first = overlaps.first()
        assertTrue(first.durationMs > 45 * 60 * 1000L)
        assertEquals(2 * 60 * 60 * 1000L, first.startEpochMs)
        assertEquals(10 * 60 * 60 * 1000L, first.endEpochMs)
    }

    @Test
    fun calculateAvailabilityOverlaps_ignoresShortGaps() {
        val windowStart = 0L
        val windowEnd = 4 * 60 * 60 * 1000L
        val userCal = CalendarFreeBusy(
            busyBlocks = listOf(
                BusyBlock(0L, 60 * 60 * 1000L),
                BusyBlock(2 * 60 * 60 * 1000L, 4 * 60 * 60 * 1000L),
            ),
            windowStartEpochMs = windowStart,
            windowEndEpochMs = windowEnd,
        )
        val friendCal = CalendarFreeBusy(
            busyBlocks = listOf(
                BusyBlock(0L, 75 * 60 * 1000L),
                BusyBlock(2 * 60 * 60 * 1000L + 15 * 60 * 1000L, 4 * 60 * 60 * 1000L),
            ),
            windowStartEpochMs = windowStart,
            windowEndEpochMs = windowEnd,
        )
        val overlaps = calculateAvailabilityOverlaps(userCal, friendCal, minGapMinutes = 45)
        assertTrue(overlaps.isEmpty())
    }
}
