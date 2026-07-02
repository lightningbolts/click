package compose.project.click.click.data.repository

import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.MessageDeliveryState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ChatSessionCachesTest {
    @Test
    fun routingSeedIsSharedAcrossRepositoryInstances() {
        runBlocking { ChatSessionCaches.clearAll() }

        val repoA = SupabaseChatRepository(tokenStorage = compose.project.click.click.data.storage.FakeTokenStorage())
        val repoB = SupabaseChatRepository(tokenStorage = compose.project.click.click.data.storage.FakeTokenStorage())

        runBlocking {
            ChatSessionCaches.seedConnectionRouting(chatId = "chat-1", connectionId = "conn-1")
        }

        val message = Message(
            id = "m1",
            user_id = "user-a",
            content = "hello",
            timeCreated = 1_700_000_000_000L,
            deliveryState = MessageDeliveryState.SENT,
        )
        repoA.mergeCachedTimelineMessage("conn-1", message)

        assertEquals("conn-1", runBlocking { ChatSessionCaches.peekListKeyForChat("chat-1") })
        assertNotNull(repoB.peekCachedMessageTimeline("conn-1"))
        assertEquals("hello", repoB.peekCachedMessageTimeline("conn-1")?.last()?.content)
    }
}
