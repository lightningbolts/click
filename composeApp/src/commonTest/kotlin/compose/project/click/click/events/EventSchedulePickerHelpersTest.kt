package compose.project.click.click.events

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class EventSchedulePickerHelpersTest {

    @Test
    fun eventClock12hFrom24h_midnightAndNoon() {
        assertEquals(EventClock12h(12, 0, isPm = false), eventClock12hFrom24h(0, 0))
        assertEquals(EventClock12h(12, 30, isPm = true), eventClock12hFrom24h(12, 30))
    }

    @Test
    fun eventClock12hFrom24h_morningAndAfternoon() {
        assertEquals(EventClock12h(1, 5, isPm = false), eventClock12hFrom24h(1, 5))
        assertEquals(EventClock12h(3, 45, isPm = true), eventClock12hFrom24h(15, 45))
        assertEquals(EventClock12h(11, 59, isPm = true), eventClock12hFrom24h(23, 59))
    }

    @Test
    fun eventClock12hTo24h_roundTrip() {
        for (hour24 in 0..23) {
            for (minute in listOf(0, 1, 30, 59)) {
                val clock = eventClock12hFrom24h(hour24, minute)
                assertEquals(hour24 to minute, eventClock12hTo24h(clock))
            }
        }
    }

    @Test
    fun formatEventClockLabel_matches12h() {
        assertEquals("12:00 AM", formatEventClockLabel(0, 0))
        assertEquals("12:05 PM", formatEventClockLabel(12, 5))
        assertEquals("9:07 AM", formatEventClockLabel(9, 7))
    }

    @Test
    fun coerceSameDayEventTimes_nudgesWhenInverted() {
        val editedStart = coerceSameDayEventTimes(
            editingStart = true,
            startHour = 15,
            startMinute = 0,
            endHour = 14,
            endMinute = 0,
        )
        assertEquals((13 to 59) to (14 to 0), editedStart)

        val editedEnd = coerceSameDayEventTimes(
            editingStart = false,
            startHour = 15,
            startMinute = 0,
            endHour = 14,
            endMinute = 0,
        )
        assertEquals((15 to 0) to (15 to 1), editedEnd)
    }

    @Test
    fun utcMidnightMillis_roundTrip() {
        val date = LocalDate(2026, 8, 1)
        val ms = localDateToUtcMidnightMillis(date)
        assertEquals(date, utcMidnightMillisToLocalDate(ms))
    }

    @Test
    fun mergeLocalDateWithClock_addsClockOffset() {
        val date = LocalDate(2026, 8, 1)
        val tz = TimeZone.of("UTC")
        val noon = mergeLocalDateWithClock(date, 12, 30, tz)
        assertEquals(
            localDateToUtcMidnightMillis(date) + (12 * 60L + 30) * 60_000L,
            noon,
        )
    }
}
