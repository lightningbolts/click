package compose.project.click.click.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.models.Chat
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.GeoLocation
import compose.project.click.click.data.models.HangoutPlan
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.PlanResponses
import compose.project.click.click.data.models.PlanRsvp
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
import kotlinx.serialization.json.JsonElement
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelPlansTest {
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
                otherUser = User(id = otherId, name = "Maya Lee"),
                lastMessage = null,
                unreadCount = 0,
            )
        return FakeChatRepository(
            onFetchChatWithDetails = { _, uid -> if (uid == selfId) details else null },
            onFetchChatParticipants = { listOf(User(id = selfId, name = "Me"), User(id = otherId, name = "Maya Lee")) },
            onGetUserById = { id -> User(id = id, name = if (id == selfId) "Me" else "Maya Lee") },
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

    private val start = Clock.System.now().toEpochMilliseconds() + 5 * 3_600_000L

    @Test
    fun sendPlanSendsSummaryWithPlanMetadataAndRsvpsGoing() =
        runVmTest {
            val fake = fakeRepository()
            var sentContent: String? = null
            var sentMetadata: JsonElement? = null
            fake.onSendMessage = { chatId, userId, content, type, metadata, localMs ->
                sentContent = content
                sentMetadata = metadata
                Message(
                    id = "plan-1",
                    user_id = userId,
                    content = content,
                    timeCreated = localMs ?: 0,
                    messageType = type,
                    metadata = metadata,
                )
            }
            val vm = openChat(fake)
            vm.openPlanner()
            val plan = HangoutPlan("Dinner", start, placeName = "Café Allegro")
            vm.sendPlan(plan)
            advanceUntilIdle()

            assertEquals(plan.summary(), sentContent)
            assertEquals(plan, HangoutPlan.parse(sentMetadata))
            assertFalse(vm.plannerOpen.value)
            assertEquals(listOf("+${HangoutPlan.GOING}@plan-1"), fake.reactionCalls)
            val state = assertIs<ChatMessagesState.Success>(vm.chatMessagesState.value)
            assertNotNull(state.messages.firstOrNull { it.message.id == "plan-1" })
            assertEquals(PlanRsvp.GOING, PlanResponses.from(vm.messageReactions.value["plan-1"].orEmpty()).rsvpOf(selfId))
        }

    @Test
    fun rsvpSwitchRemovesTheOppositeReaction() =
        runVmTest {
            val fake = fakeRepository()
            fake.onSendMessage = { _, userId, content, type, metadata, localMs ->
                Message(
                    id = "plan-2",
                    user_id = userId,
                    content = content,
                    timeCreated = localMs ?: 0,
                    messageType = type,
                    metadata = metadata,
                )
            }
            val vm = openChat(fake)
            vm.sendPlan(HangoutPlan("Walk", start))
            advanceUntilIdle()
            fake.reactionCalls.clear()

            vm.setPlanRsvp("plan-2", PlanRsvp.DECLINED)
            advanceUntilIdle()
            assertEquals(listOf("-${HangoutPlan.GOING}@plan-2", "+${HangoutPlan.DECLINED}@plan-2"), fake.reactionCalls)
            assertEquals(PlanRsvp.DECLINED, PlanResponses.from(vm.messageReactions.value["plan-2"].orEmpty()).rsvpOf(selfId))

            fake.reactionCalls.clear()
            vm.setPlanRsvp("plan-2", null)
            advanceUntilIdle()
            assertEquals(listOf("-${HangoutPlan.DECLINED}@plan-2"), fake.reactionCalls)
            assertEquals(null, PlanResponses.from(vm.messageReactions.value["plan-2"].orEmpty()).rsvpOf(selfId))
        }

    @Test
    fun plannerHandoffIsConsumedOnce() =
        runVmTest {
            val vm = openChat(fakeRepository())
            vm.requestPlannerOnOpen(connectionId)
            assertFalse(vm.consumePlannerRequest("someone-else"))
            assert(vm.consumePlannerRequest(connectionId))
            assertFalse(vm.consumePlannerRequest(connectionId))
        }
}
