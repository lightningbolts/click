package compose.project.click.click.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.api.ScheduledMessageGoneException
import compose.project.click.click.data.models.Chat
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.GeoLocation
import compose.project.click.click.data.models.ScheduledMessage
import compose.project.click.click.data.models.User
import compose.project.click.click.data.storage.FakeTokenStorage
import compose.project.click.click.data.storage.initTokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelScheduleTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val selfId = "user-self"
    private val otherId = "user-other"
    private val connectionId = "conn-1"
    private val apiChatId = "11111111-1111-4111-8111-111111111111"

    @Before
    fun setup() {
        initTokenStorage(ApplicationProvider.getApplicationContext())
        runBlocking { AppDataManager.clearData() }
    }

    private fun runVmTest(body: suspend TestScope.() -> Unit) =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            try {
                body()
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun fakeRepository(): FakeChatRepository {
        val connection =
            Connection(
                id = connectionId,
                created = 1L,
                expiry = Long.MAX_VALUE,
                geo_location = GeoLocation(0.0, 0.0),
                user_ids = listOf(selfId, otherId),
                chat = Chat(id = apiChatId, connectionId = connectionId),
                has_begun = true,
                expiry_state = "active",
            )
        val details =
            ChatWithDetails(
                chat = connection.chat,
                connection = connection,
                otherUser = User(id = otherId, name = "Other"),
                lastMessage = null,
                unreadCount = 0,
            )
        return FakeChatRepository(
            onFetchChatWithDetails = { _, uid -> if (uid == selfId) details else null },
            onFetchChatParticipants = { listOf(User(id = selfId, name = "Me"), User(id = otherId, name = "Other")) },
        )
    }

    private fun TestScope.openChat(fake: FakeChatRepository): ChatViewModel {
        val vm =
            ChatViewModel(
                tokenStorage = FakeTokenStorage(),
                chatRepository = fake,
                connectivityMonitor = FakeConnectivityMonitor(initialOnline = true),
            )
        vm.setCurrentUser(selfId)
        advanceUntilIdle()
        vm.loadChatMessages(connectionId)
        advanceUntilIdle()
        return vm
    }

    private fun later(minutes: Long) = Clock.System.now().toEpochMilliseconds() + minutes * 60_000L

    @Test
    fun openingAChatLoadsPendingScheduledMessages() =
        runVmTest {
            val fake = fakeRepository()
            val pending = ScheduledMessage("s1", "see you", later(60))
            fake.onFetchScheduledMessages = { chatId -> Result.success(if (chatId == apiChatId) listOf(pending) else emptyList()) }
            val vm = openChat(fake)
            assertEquals(listOf(pending), vm.scheduledMessages.value)
        }

    @Test
    fun schedulingAddsTheRowClearsTheComposerAndOnlySendsTheReplyId() =
        runVmTest {
            val fake = fakeRepository()
            var sentContent: String? = null
            var sentReplyId: String? = "unset"
            fake.onScheduleMessage = { chatId, content, replyToId, sendAt ->
                assertEquals(apiChatId, chatId)
                sentContent = content
                sentReplyId = replyToId
                Result.success(ScheduledMessage("s2", content, sendAt))
            }
            val vm = openChat(fake)
            vm.updateMessageInput("  dinner at 7  ")
            val sendAt = later(30)
            val ok = vm.scheduleMessage("dinner at 7", sendAt)
            advanceUntilIdle()

            assertTrue(ok)
            assertEquals("dinner at 7", sentContent)
            assertNull(sentReplyId)
            assertEquals(listOf("s2"), vm.scheduledMessages.value.map { it.id })
            assertEquals("", vm.messageInput.value)
            assertTrue(
                vm.chatNotice.value
                    .orEmpty()
                    .startsWith("Scheduled for "),
            )
        }

    @Test
    fun composerIsKeptWhenTheTextChangedWhileScheduling() =
        runVmTest {
            val fake = fakeRepository()
            val vm = openChat(fake)
            fake.onScheduleMessage = { _, content, _, sendAt ->
                vm.updateMessageInput("something new")
                Result.success(ScheduledMessage("s3", content, sendAt))
            }
            vm.updateMessageInput("original")
            assertTrue(vm.scheduleMessage("original", later(20)))
            assertEquals("something new", vm.messageInput.value)
        }

    @Test
    fun failureKeepsTheTextAndReportsTheError() =
        runVmTest {
            val fake = fakeRepository()
            fake.onScheduleMessage = { _, _, _, _ -> Result.failure(Exception("send_at must be a future time within a year")) }
            val vm = openChat(fake)
            vm.updateMessageInput("hello")
            assertFalse(vm.scheduleMessage("hello", later(20)))
            assertEquals("hello", vm.messageInput.value)
            assertTrue(vm.scheduledMessages.value.isEmpty())
            assertEquals("send_at must be a future time within a year", vm.messageSendError.value)
        }

    @Test
    fun outOfRangeTimesAreRejectedLocally() =
        runVmTest {
            val fake = fakeRepository()
            var called = false
            fake.onScheduleMessage = { _, _, _, _ ->
                called = true
                Result.failure(Exception())
            }
            val vm = openChat(fake)
            assertFalse(vm.scheduleMessage("hello", Clock.System.now().toEpochMilliseconds() + 5_000))
            assertFalse(called)
        }

    @Test
    fun cancelRemovesTheRowAndReloadsWhenAlreadyDelivered() =
        runVmTest {
            val fake = fakeRepository()
            val a = ScheduledMessage("a", "one", later(60))
            val b = ScheduledMessage("b", "two", later(120))
            var fetches = 0
            fake.onFetchScheduledMessages = {
                fetches++
                Result.success(if (fetches == 1) listOf(a, b) else listOf(b))
            }
            fake.onCancelScheduledMessage = { Result.failure(ScheduledMessageGoneException()) }
            val vm = openChat(fake)
            assertEquals(2, vm.scheduledMessages.value.size)

            vm.cancelScheduledMessage("a")
            advanceUntilIdle()
            assertEquals(listOf("b"), vm.scheduledMessages.value.map { it.id })
            assertEquals(2, fetches)
            // A 404 means "already sent": not an error worth showing.
            assertNull(vm.messageSendError.value)
        }
}
