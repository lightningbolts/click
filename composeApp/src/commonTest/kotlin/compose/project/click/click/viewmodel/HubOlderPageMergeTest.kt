package compose.project.click.click.viewmodel

import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.MessageWithUser
import compose.project.click.click.data.models.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class HubOlderPageMergeTest {
    private fun row(
        id: String,
        at: Long,
    ) = MessageWithUser(
        message = Message(id = id, user_id = "u", content = id, timeCreated = at),
        user = User(id = "u"),
        isSent = false,
    )

    @Test
    fun olderPageGoesUnderLoadedRowsWithoutDuplicates() {
        val loaded = listOf(row("c", 300), row("d", 400))
        val older = listOf(row("a", 100), row("b", 200), row("c", 300))
        assertEquals(listOf("a", "b", "c", "d"), mergeOlderHubPage(loaded, older).map { it.message.id })
    }

    @Test
    fun emptyOrKnownPageKeepsLoadedList() {
        val loaded = listOf(row("c", 300))
        assertSame(loaded, mergeOlderHubPage(loaded, emptyList()))
        assertSame(loaded, mergeOlderHubPage(loaded, listOf(row("c", 300))))
    }
}
