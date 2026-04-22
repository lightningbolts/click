package compose.project.click.click.util

import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.ConnectionEncounter
import compose.project.click.click.data.models.GeoLocation
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionSearchHaystackTest {

    @Test
    fun connectionContextHaystack_includesSemanticLocation() {
        val c = baseConnection().copy(semantic_location = "Terry Hall")
        val hay = connectionContextHaystack(c)
        assertTrue(hay.contains("terry hall"))
    }

    @Test
    fun connectionMatchesMemoryOrTimeQuery_matchesEncounterLocation() {
        val enc = ConnectionEncounter(
            id = "e1",
            connectionId = "c1",
            encounteredAt = "2026-01-01T15:00:00Z",
            locationName = "Memorial Union",
        )
        val c = baseConnection().copy(connectionEncounters = listOf(enc))
        assertTrue(connectionMatchesMemoryOrTimeQuery(c, "memorial"))
    }

    @Test
    fun connectionMatchesMemoryOrTimeQuery_matchesFridayInTimeOfDayText() {
        val c = baseConnection().copy(timeOfDayUtc = "Friday evening")
        assertTrue(connectionMatchesMemoryOrTimeQuery(c, "friday"))
    }

    @Test
    fun connectionMatchesMemoryOrTimeQuery_noFalsePositiveForRandomQuery() {
        val c = baseConnection().copy(semantic_location = "Library")
        assertFalse(connectionMatchesMemoryOrTimeQuery(c, "zzzz-not-present-zzzz"))
    }

    private fun baseConnection(): Connection = Connection(
        id = "c1",
        created = 1L,
        expiry = Long.MAX_VALUE,
        geo_location = GeoLocation(0.0, 0.0),
        user_ids = listOf("u1", "u2"),
        status = "kept",
    )
}
