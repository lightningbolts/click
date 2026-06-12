package compose.project.click.click.collaboration

import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClickDropRevealTest {

    @Test
    fun computeClickDropRevealTtlIso_is24HoursAfterNow() {
        val now = Clock.System.now()
        val revealMs = kotlinx.datetime.Instant.parse(computeClickDropRevealTtlIso(now)).toEpochMilliseconds()
        val delta = revealMs - now.toEpochMilliseconds()
        assertTrue(delta in (23L * 60 * 60_000L)..(25L * 60 * 60_000L))
    }

    @Test
    fun clickDropRevealDelayMs_matches24HourWindow() {
        val delay = clickDropRevealDelayMs()
        assertTrue(delay in (23L * 60 * 60_000L)..(25L * 60 * 60_000L))
    }
}
