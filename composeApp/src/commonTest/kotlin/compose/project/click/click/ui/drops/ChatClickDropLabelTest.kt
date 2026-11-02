package compose.project.click.click.ui.drops

import compose.project.click.click.collaboration.ClickDropDevelopState
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class ChatClickDropLabelTest {
    private val now = Instant.parse("2026-10-01T12:00:00Z")

    @Test
    fun pendingShowsTheCountdown() {
        val state = ClickDropDevelopState.Pending(now + 192.minutes)
        assertEquals("Click Drop · develops in 3h 12m", chatClickDropLabel(state, developing = false, now = now))
        assertEquals("Click Drop", chatClickDropLabel(ClickDropDevelopState.Pending(null), developing = false, now = now))
    }

    @Test
    fun readyAsksForATapUntilItIsDeveloping() {
        assertEquals("Tap to develop", chatClickDropLabel(ClickDropDevelopState.Ready, developing = false, now = now))
        assertEquals("Developing…", chatClickDropLabel(ClickDropDevelopState.Ready, developing = true, now = now))
        assertEquals("Developing…", chatClickDropLabel(ClickDropDevelopState.Developed, developing = false, now = now))
    }
}
