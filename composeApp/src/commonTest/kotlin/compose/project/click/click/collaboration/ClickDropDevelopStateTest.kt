package compose.project.click.click.collaboration

import compose.project.click.click.data.flags.FeatureFlagsSnapshot
import compose.project.click.click.data.flags.parseFeatureFlagsResponse
import compose.project.click.click.ui.drops.clickDropCountdownLabel
import compose.project.click.click.ui.drops.sharedDropDisclosure
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Fixtures from iOS `ClickDropDevelopTests` and click-web `lib/drops/developState.ts`. */
class ClickDropDevelopStateTest {
    private val reveal = Instant.fromEpochSeconds(1_000)

    private fun flags(config: String) =
        FeatureFlagsSnapshot(
            userId = "u1",
            enabled =
                parseFeatureFlagsResponse(
                    Json.parseToJsonElement("""{"features":{"shared_drops":{"enabled":true,"config":$config}}}"""),
                ).orEmpty(),
        )

    @Test
    fun pendingBeforeRevealReadyAfterDevelopedOnceDeveloped() {
        assertEquals(ClickDropDevelopState.Pending(reveal), ClickDropDevelopState.resolve(reveal, null, reveal - 1.seconds))
        // Developed never shows before reveal.
        assertEquals(ClickDropDevelopState.Pending(reveal), ClickDropDevelopState.resolve(reveal, reveal, reveal - 1.seconds))
        assertEquals(ClickDropDevelopState.Ready, ClickDropDevelopState.resolve(reveal, null, reveal))
        assertEquals(ClickDropDevelopState.Developed, ClickDropDevelopState.resolve(reveal, reveal, reveal + 5.seconds))
        assertTrue(ClickDropDevelopState.resolve(null, null).isPending)
    }

    @Test
    fun instantsParseLenientlyAndBadOnesAreUnknown() {
        assertEquals(Instant.parse("2026-10-01T10:00:00Z"), parseInstantOrNull(" 2026-10-01T10:00:00Z "))
        assertNull(parseInstantOrNull("yesterday"))
        assertNull(parseInstantOrNull(""))
        assertNull(parseInstantOrNull(null))
    }

    @Test
    fun developHoursComeFromTheSharedDropsConfig() {
        assertEquals(1, sharedDropDevelopHours(FeatureFlagsSnapshot.OFF))
        assertEquals(1, sharedDropDevelopHours(flags("{}")))
        assertEquals(3, sharedDropDevelopHours(flags("""{"develop_hours":3}""")))
        assertEquals(6, sharedDropDevelopHours(flags("""{"develop_hours":"6"}""")))
        // Clamped like the server (1..72).
        assertEquals(72, sharedDropDevelopHours(flags("""{"develop_hours":500}""")))
        assertEquals(1, sharedDropDevelopHours(flags("""{"develop_hours":0}""")))
    }

    @Test
    fun disclosureCopyFollowsDevelopHours() {
        assertTrue(sharedDropDisclosure(1).startsWith("It develops for them in an hour, like a story."))
        assertTrue(sharedDropDisclosure(3).startsWith("It develops for them in 3 hours,"))
        assertTrue(sharedDropDisclosure(1).contains("aren't end-to-end encrypted"))
    }

    @Test
    fun countdownLabelRoundsUpToTheMinute() {
        val now = Instant.parse("2026-10-01T10:00:00Z")
        assertEquals("Develops in 1h 1m", clickDropCountdownLabel(ClickDropDevelopState.Pending(now + 60.minutes + 1.seconds), now))
        assertEquals("Develops in 5m", clickDropCountdownLabel(ClickDropDevelopState.Pending(now + 5.minutes), now))
        assertEquals("Develops in a moment", clickDropCountdownLabel(ClickDropDevelopState.Pending(now), now))
        assertEquals("Developing", clickDropCountdownLabel(ClickDropDevelopState.Pending(null), now))
        assertNull(clickDropCountdownLabel(ClickDropDevelopState.Ready, now))
    }
}
