package compose.project.click.click.viewmodel

import compose.project.click.click.data.repository.PairConnectionRow
import compose.project.click.click.data.repository.bestPairConnectionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** G0-15: where a group Click's tags go (iOS `taggableConnectionIDs` / `pairConnectionIDs`). */
class GroupTagTargetsTest {
    @Test
    fun targetsAreDirectIdsThenPairIdsDeduplicated() {
        assertEquals(listOf("g1", "p-a", "p-b"), groupTagTargetIds(listOf("g1", "g1"), listOf("p-a", "g1", "p-b", " ")))
    }

    @Test
    fun pairLookupIgnoresGroupRowsAndPrefersActiveOrKept() {
        val rows =
            listOf(
                PairConnectionRow("group", listOf("me", "a", "b"), "active"),
                PairConnectionRow("pending", listOf("me", "a"), "pending"),
                PairConnectionRow("kept", listOf("a", "me"), "kept"),
            )
        assertEquals("kept", bestPairConnectionId(rows, "me", "a"))
    }

    @Test
    fun pairLookupFallsBackToAnyPairAndNeverAGroup() {
        assertEquals("pending", bestPairConnectionId(listOf(PairConnectionRow("pending", listOf("me", "a"), "pending")), "me", "a"))
        assertNull(bestPairConnectionId(listOf(PairConnectionRow("group", listOf("me", "a", "b"), "active")), "me", "a"))
    }

    @Test
    fun onlyNoActiveEncounterFailuresAreSkippable() {
        assertTrue(isNoActiveEncounterFailure(Exception("No active encounter row for this save")))
        assertTrue(isNoActiveEncounterFailure(Exception("No encounter row for connection")))
        assertFalse(isNoActiveEncounterFailure(Exception("JWT expired")))
        assertFalse(isNoActiveEncounterFailure(null))
    }
}
