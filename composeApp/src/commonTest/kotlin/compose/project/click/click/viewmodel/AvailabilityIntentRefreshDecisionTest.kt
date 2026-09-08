package compose.project.click.click.viewmodel

import compose.project.click.click.data.models.AvailabilityIntentRow // pragma: allowlist secret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AvailabilityIntentRefreshDecisionTest {
    @Test
    fun failedRefresh_preservesPreviouslyResolvedIntents() {
        val existing = listOf(AvailabilityIntentRow(id = "intent-1", intentTag = "Coffee"))

        val decision =
            availabilityIntentRefreshDecision(
                existing = existing,
                previouslyResolved = true,
                fetched = Result.failure(IllegalStateException("offline")),
            )

        assertSame(existing, decision.intents)
        assertTrue(decision.hasResolved)
        assertEquals(
            "Couldn't refresh availability. Showing your last saved status.",
            decision.feedback,
        )
    }

    @Test
    fun firstFailedRefresh_remainsUnresolvedAndRecoverable() {
        val decision =
            availabilityIntentRefreshDecision(
                existing = emptyList(),
                previouslyResolved = false,
                fetched = Result.failure(IllegalStateException("offline")),
            )

        assertTrue(decision.intents.isEmpty())
        assertFalse(decision.hasResolved)
        assertEquals(
            "Couldn't refresh availability. Try again when you're online.",
            decision.feedback,
        )
    }

    @Test
    fun successfulEmptyRefresh_isAResolvedEmptyState() {
        val decision =
            availabilityIntentRefreshDecision(
                existing = listOf(AvailabilityIntentRow(id = "stale")),
                previouslyResolved = true,
                fetched = Result.success(emptyList()),
            )

        assertTrue(decision.intents.isEmpty())
        assertTrue(decision.hasResolved)
        assertNull(decision.feedback)
    }
}
