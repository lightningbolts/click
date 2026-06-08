package compose.project.click.click.viewmodel

import compose.project.click.click.data.models.Message
import compose.project.click.click.data.repository.ChatTimelineCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Lightweight multiplatform tests for chat state shapes (no Android Main / ViewModel runtime).
 * Full [ChatViewModel] integration tests live in `androidUnitTest` with Robolectric.
 */
class ChatViewModelStateTest {

    @Test
    fun chatListState_error_exposesMessage() {
        val state = ChatListState.Error("offline")
        assertEquals("offline", state.message)
    }

    @Test
    fun chatMessagesState_loading_isDistinctBranch() {
        val state: ChatMessagesState = ChatMessagesState.Loading
        assertIs<ChatMessagesState.Loading>(state)
    }

    @Test
    fun chatTimelineCache_retainsTimelineAcrossSimulatedNavigation() {
        val cache = ChatTimelineCache()
        val connectionId = "conn-alpha"
        val timeline = listOf(
            Message(id = "m1", user_id = "u1", content = "older", timeCreated = 50L),
            Message(id = "m2", user_id = "u2", content = "latest", timeCreated = 100L),
        )
        cache.store(connectionId, timeline)

        // Simulate leaving the chat (cache is not cleared on back-navigation).
        val afterLeave = cache.peek(connectionId)
        assertNotNull(afterLeave)
        assertEquals(2, afterLeave.size)
        assertEquals("m2", afterLeave.last().id)

        // Simulate re-entry: hot cache still available before network refresh.
        cache.mergeMessage(
            connectionId,
            Message(id = "m3", user_id = "u1", content = "newest", timeCreated = 200L),
        )
        val afterReentry = cache.peek(connectionId)
        assertNotNull(afterReentry)
        assertEquals("m3", afterReentry.last().id)
    }

    @Test
    fun fakeChatRepository_exposesSharedTimelineCache() {
        val repo = FakeChatRepository()
        val message = Message(id = "x", user_id = "u", content = "hi", timeCreated = 1L)
        repo.storeCachedMessageTimeline("conn", listOf(message))
        assertEquals(message, repo.peekCachedMessageTimeline("conn")?.single())
        repo.mergeCachedTimelineMessage("conn", message.copy(content = "updated"))
        assertEquals("updated", repo.peekCachedMessageTimeline("conn")?.single()?.content)
    }
}
