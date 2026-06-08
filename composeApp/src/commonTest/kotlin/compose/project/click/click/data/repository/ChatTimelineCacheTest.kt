package compose.project.click.click.data.repository

import compose.project.click.click.data.models.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatTimelineCacheTest {

    private fun message(id: String, ts: Long) = Message(
        id = id,
        user_id = "u1",
        content = "hello-$id",
        timeCreated = ts,
    )

    @Test
    fun storeAndPeek_returnsTimelineForConnection() {
        val cache = ChatTimelineCache()
        val rows = listOf(message("a", 1L), message("b", 2L))
        cache.store("conn-1", rows)
        assertEquals(rows, cache.peek("conn-1"))
    }

    @Test
    fun mergeMessage_appendsAndSortsByTimeCreated() {
        val cache = ChatTimelineCache()
        cache.store("conn-1", listOf(message("a", 1L)))
        cache.mergeMessage("conn-1", message("b", 3L))
        cache.mergeMessage("conn-1", message("c", 2L))
        assertEquals(listOf("a", "c", "b"), cache.peek("conn-1")?.map { it.id })
    }

    @Test
    fun clear_removesAllTimelines() {
        val cache = ChatTimelineCache(maxConnections = 2)
        cache.store("c1", listOf(message("m1", 1L)))
        cache.store("c2", listOf(message("m2", 2L)))
        cache.clear()
        assertNull(cache.peek("c1"))
        assertNull(cache.peek("c2"))
    }

    @Test
    fun pruneToMax_keepsMostRecentlyActiveConnections() {
        val cache = ChatTimelineCache(maxConnections = 2)
        cache.store("old", listOf(message("old", 1L)))
        cache.store("mid", listOf(message("mid", 5L)))
        cache.store("new", listOf(message("new", 9L)))
        assertNull(cache.peek("old"))
        assertEquals("mid", cache.peek("mid")?.single()?.id)
        assertEquals("new", cache.peek("new")?.single()?.id)
    }
}
