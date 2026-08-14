package compose.project.click.click.viewmodel // pragma: allowlist secret

import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeRecentConnectionsTest {
    @Test
    fun recentConnectionsGrouping_limitsToTenMostRecent() {
        val connections =
            (1..12).map { idx ->
                Connection(
                    id = "c$idx",
                    created = idx.toLong(),
                    expiry = 9_999_999_999L,
                    user_ids = listOf("me", "u$idx"),
                )
            }
        val grouped =
            connections
                .sortedByDescending { it.created }
                .take(10)
                .groupBy { it.semanticLocation ?: "Somewhere New" }
        assertEquals(10, grouped.values.sumOf { it.size })
        assertEquals(12L, grouped.values.flatten().maxOf { it.created })
    }
}
