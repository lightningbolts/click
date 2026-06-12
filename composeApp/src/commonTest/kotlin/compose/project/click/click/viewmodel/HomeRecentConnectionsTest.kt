package compose.project.click.click.viewmodel

import compose.project.click.click.data.models.Connection
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeRecentConnectionsTest {

    @Test
    fun recentConnectionsGrouping_limitsToFiveMostRecent() {
        val connections = (1..8).map { idx ->
            Connection(
                id = "c$idx",
                created = idx.toLong(),
                expiry = 9_999_999_999L,
                user_ids = listOf("me", "u$idx"),
            )
        }
        val grouped = connections
            .sortedByDescending { it.created }
            .take(5)
            .groupBy { it.semanticLocation ?: "Somewhere New" }
        assertEquals(5, grouped.values.sumOf { it.size })
        assertEquals(8L, grouped.values.flatten().maxOf { it.created })
    }
}
