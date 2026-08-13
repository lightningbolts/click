package compose.project.click.click.data.models

import kotlin.test.Test
import kotlin.test.assertEquals

class VisibleMapConnectionsTest {
    private fun connection(
        id: String,
        users: List<String> = listOf("me", id),
    ): Connection =
        Connection(
            id = id,
            created = 1L,
            expiry = 2L,
            user_ids = users,
        )

    @Test
    fun visibleMapConnections_keepsNonCorePinsWhenMemoryMapWouldHaveBeenOff() {
        val core = connection("core")
        val other = connection("other")
        val hidden = connection("hidden")
        val visible =
            visibleMapConnections(
                connections = listOf(core, other, hidden),
                hiddenIds = setOf("hidden"),
                viewerId = "me",
            )
        assertEquals(listOf("core", "other"), visible.map { it.id })
    }

    @Test
    fun visibleMapConnections_doesNotChangeWithoutInputChange() {
        val connections = listOf(connection("a"), connection("b"))
        val first = visibleMapConnections(connections, hiddenIds = emptySet(), viewerId = "me")
        val second = visibleMapConnections(connections, hiddenIds = emptySet(), viewerId = "me")
        assertEquals(first.map { it.id }, second.map { it.id })
    }
}
