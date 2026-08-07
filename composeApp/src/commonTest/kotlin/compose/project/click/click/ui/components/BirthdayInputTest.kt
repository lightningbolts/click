package compose.project.click.click.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BirthdayInputTest {
    @Test
    fun formatsDigitRunsWithDashes() {
        assertEquals("", formatBirthdayDigitsInput(""))
        assertEquals("2000", formatBirthdayDigitsInput("2000"))
        assertEquals("2000-04", formatBirthdayDigitsInput("200004"))
        assertEquals("2000-04-17", formatBirthdayDigitsInput("20000417"))
        assertEquals("2000-04-17", formatBirthdayDigitsInput("2000/04/17"))
        assertEquals("2000-04-17", formatBirthdayDigitsInput("2000-04-17T00:00:00Z"))
    }

    @Test
    fun parsesCompleteIsoOnly() {
        assertEquals("2000-04-17", parseBirthdayIsoLocalDate("20000417")?.toString())
        assertNull(parseBirthdayIsoLocalDate("200004"))
    }
}
