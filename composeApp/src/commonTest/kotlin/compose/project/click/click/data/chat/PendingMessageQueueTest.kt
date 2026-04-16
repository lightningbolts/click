package compose.project.click.click.data.chat

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PendingMessageQueueTest {

    private fun newMessage(
        clientId: String = "client-1",
        chatId: String = "chat-A",
        senderId: String = "me",
        plaintext: String? = "hi",
    ): PendingMessage = PendingMessage(
        clientId = clientId,
        chatId = chatId,
        senderId = senderId,
        plaintext = plaintext,
    )

    @Test
    fun enqueue_storesMessage_andExposesItInSnapshot() = runTest {
        val queue = PendingMessageQueue()
        val msg = newMessage()

        queue.enqueue(msg)

        val snapshot = queue.snapshot()
        assertEquals(1, snapshot.size)
        assertEquals("client-1", snapshot.first().clientId)
        assertEquals(PendingMessageStatus.QUEUED, snapshot.first().status)
    }

    @Test
    fun enqueue_isIdempotentPerClientId() = runTest {
        val queue = PendingMessageQueue()
        val first = newMessage()
        val second = newMessage(plaintext = "mutated duplicate")

        queue.enqueue(first)
        val returned = queue.enqueue(second)

        assertEquals(1, queue.snapshot().size)
        assertEquals("hi", returned.plaintext, "second enqueue should return the original entry unchanged")
    }

    @Test
    fun markInFlight_updatesStatusAndStampsAttempt() = runTest {
        val queue = PendingMessageQueue()
        queue.enqueue(newMessage())

        queue.markInFlight("chat-A", "client-1")

        val entry = queue.snapshot().first()
        assertEquals(PendingMessageStatus.IN_FLIGHT, entry.status)
        assertNotNull(entry.lastAttemptAtEpochMs)
    }

    @Test
    fun markFailed_bumpsRetryCount_andRecordsErrorKind() = runTest {
        val queue = PendingMessageQueue()
        queue.enqueue(newMessage())

        queue.markFailed("chat-A", "client-1", errorKind = "network")
        queue.markFailed("chat-A", "client-1", errorKind = "network")

        val entry = queue.snapshot().first()
        assertEquals(PendingMessageStatus.FAILED, entry.status)
        assertEquals(2, entry.retryCount)
        assertEquals("network", entry.lastErrorKind)
    }

    @Test
    fun markSent_recordsServerId_andKeepsEntryUntilRemoved() = runTest {
        val queue = PendingMessageQueue()
        queue.enqueue(newMessage())

        queue.markSent("chat-A", "client-1", serverMessageId = "srv-xyz")

        val entry = queue.snapshot().first()
        assertEquals(PendingMessageStatus.SENT, entry.status)
        assertEquals("srv-xyz", entry.serverMessageId)

        queue.remove("chat-A", "client-1")
        assertTrue(queue.snapshot().isEmpty())
    }

    @Test
    fun observeFor_returnsOnlyThatChatsEntries() = runTest {
        val queue = PendingMessageQueue()
        queue.enqueue(newMessage(clientId = "a-1", chatId = "chat-A"))
        queue.enqueue(newMessage(clientId = "b-1", chatId = "chat-B"))

        val chatAItems = queue.observeFor("chat-A").first()
        assertEquals(1, chatAItems.size)
        assertEquals("a-1", chatAItems.first().clientId)

        val chatCItems = queue.observeFor("chat-C").first()
        assertTrue(chatCItems.isEmpty())
    }

    @Test
    fun clear_emptiesAllEntries() = runTest {
        val queue = PendingMessageQueue()
        queue.enqueue(newMessage(clientId = "a-1", chatId = "chat-A"))
        queue.enqueue(newMessage(clientId = "b-1", chatId = "chat-B"))

        queue.clear()

        assertTrue(queue.snapshot().isEmpty())
    }

    @Test
    fun updatesForMissingClientId_areIgnored() = runTest {
        val queue = PendingMessageQueue()
        queue.enqueue(newMessage())

        queue.markInFlight("chat-A", "bogus")
        queue.markSent("chat-A", "bogus", serverMessageId = "srv-x")
        queue.markFailed("chat-A", "bogus", "network")

        val entry = queue.snapshot().first()
        assertEquals(PendingMessageStatus.QUEUED, entry.status, "mutations on missing ids must not alter siblings")
        assertNull(entry.serverMessageId)
        assertEquals(0, entry.retryCount)
    }
}
