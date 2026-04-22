package compose.project.click.click.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import compose.project.click.click.data.models.Chat
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.GeoLocation
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.MessageDeliveryState
import compose.project.click.click.data.models.User
import compose.project.click.click.data.repository.ChatMessageSubscription
import compose.project.click.click.data.repository.ChatRealtimeEvent
import compose.project.click.click.data.repository.MessageChangeEvent
import compose.project.click.click.data.repository.SupabaseRepository
import compose.project.click.click.data.storage.FakeTokenStorage
import compose.project.click.click.data.storage.initTokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * AndroidX runs [androidx.lifecycle.viewModelScope] on Main; Robolectric provides a Looper
 * and [InstantTaskExecutorRule] makes LiveData / ViewModel work synchronously in unit tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun runVmTest(testBody: suspend TestScope.() -> Unit) = runTest {
    val mainDispatcher = UnconfinedTestDispatcher()
    Dispatchers.setMain(mainDispatcher)
    try {
        testBody()
    } finally {
        Dispatchers.resetMain()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setupTokenStorage() {
        // AppDataManager eagerly constructs AuthRepository → createTokenStorage() on first class load.
        initTokenStorage(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun initialState_chatListIsLoading() = runVmTest {
        val vm = ChatViewModel(
            tokenStorage = FakeTokenStorage(),
            chatRepository = FakeChatRepository()
        )
        advanceUntilIdle()
        assertTrue(vm.chatListState.value is ChatListState.Loading)
    }

    @Test
    fun setCurrentUser_updatesCurrentUserIdFlow() = runVmTest {
        val vm = ChatViewModel(
            tokenStorage = FakeTokenStorage(),
            chatRepository = FakeChatRepository()
        )
        vm.setCurrentUser("user-abc")
        advanceUntilIdle()
        assertEquals("user-abc", vm.currentUserId.value)
    }

    @Test
    fun loadChats_repositoryReturnsEmpty_setsSuccessWithEmptyList() = runVmTest {
        val fake = FakeChatRepository(
            onFetchUserChatsWithDetails = { emptyList() }
        )
        val vm = ChatViewModel(
            tokenStorage = FakeTokenStorage(),
            chatRepository = fake,
            supabaseRepository = SupabaseRepository()
        )
        vm.setCurrentUser("user-1")
        advanceUntilIdle()
        vm.loadChats(isForced = true)
        advanceUntilIdle()
        val success = vm.chatListState.value
        assertIs<ChatListState.Success>(success)
        assertTrue(success.chats.isEmpty())
    }

    @Test
    fun updateMessageInput_updatesMessageInputFlow() = runVmTest {
        val vm = ChatViewModel(
            tokenStorage = FakeTokenStorage(),
            chatRepository = FakeChatRepository()
        )
        vm.updateMessageInput("hello")
        assertEquals("hello", vm.messageInput.value)
    }

    @Test
    fun sendMessage_appendsMessageWhenRepositoryReturnsMessage() = runVmTest {
        val selfId = "user-self"
        val otherId = "user-other"
        val connectionId = "conn-1"
        val apiChatId = "chat-api-1"
        val now = 1_700_000_000_000L

        val connection = Connection(
            id = connectionId,
            created = now,
            expiry = Long.MAX_VALUE,
            geo_location = GeoLocation(0.0, 0.0),
            user_ids = listOf(selfId, otherId),
            chat = Chat(id = apiChatId, connectionId = connectionId),
            has_begun = true,
            expiry_state = "active"
        )
        val details = ChatWithDetails(
            chat = connection.chat,
            connection = connection,
            otherUser = User(id = otherId, name = "Other"),
            lastMessage = null,
            unreadCount = 0
        )

        val sent = Message(
            id = "msg-new",
            user_id = selfId,
            content = "hi",
            timeCreated = now + 1,
            isRead = true
        )

        val fake = FakeChatRepository(
            onFetchChatWithDetails = { _, uid ->
                if (uid == selfId) details else null
            },
            onFetchMessagesForChat = { _, _ -> emptyList() },
            onFetchChatParticipants = {
                listOf(
                    User(id = selfId, name = "Me"),
                    User(id = otherId, name = "Other")
                )
            },
            onFetchReactionsForChat = { emptyList() },
            onSendMessage = { chatId, userId, content, _, _, localSentAtMs ->
                assertNotNull(localSentAtMs)
                if (chatId == apiChatId && userId == selfId && content == "hi") {
                    sent.copy(
                        localSentAt = localSentAtMs,
                        timeCreated = now + 1,
                    )
                } else {
                    null
                }
            },
            onGetUserById = { id ->
                when (id) {
                    selfId -> User(id = selfId, name = "Me", createdAt = 0L)
                    otherId -> User(id = otherId, name = "Other", createdAt = 0L)
                    else -> null
                }
            }
        )

        val vm = ChatViewModel(
            tokenStorage = FakeTokenStorage(),
            chatRepository = fake,
            supabaseRepository = SupabaseRepository()
        )
        vm.setCurrentUser(selfId)
        advanceUntilIdle()

        vm.loadChatMessages(connectionId)
        advanceUntilIdle()

        vm.updateMessageInput("hi")
        vm.sendMessage()
        advanceUntilIdle()

        val messagesState = vm.chatMessagesState.value
        assertIs<ChatMessagesState.Success>(messagesState)
        assertTrue(messagesState.messages.any { it.message.id == "msg-new" && it.message.content == "hi" })
        assertTrue(
            messagesState.messages.none {
                it.message.id.startsWith("temp-") && it.message.deliveryState == MessageDeliveryState.PENDING
            },
        )
    }

    @Test
    fun sendMessage_marksOptimisticRowErrorWhenRepositoryReturnsNull() = runVmTest {
        val selfId = "user-self"
        val otherId = "user-other"
        val connectionId = "conn-1"
        val apiChatId = "chat-api-1"
        val now = 1_700_000_000_000L

        val connection = Connection(
            id = connectionId,
            created = now,
            expiry = Long.MAX_VALUE,
            geo_location = GeoLocation(0.0, 0.0),
            user_ids = listOf(selfId, otherId),
            chat = Chat(id = apiChatId, connectionId = connectionId),
            has_begun = true,
            expiry_state = "active"
        )
        val details = ChatWithDetails(
            chat = connection.chat,
            connection = connection,
            otherUser = User(id = otherId, name = "Other"),
            lastMessage = null,
            unreadCount = 0
        )

        val fake = FakeChatRepository(
            onFetchChatWithDetails = { _, uid ->
                if (uid == selfId) details else null
            },
            onFetchMessagesForChat = { _, _ -> emptyList() },
            onFetchChatParticipants = {
                listOf(
                    User(id = selfId, name = "Me"),
                    User(id = otherId, name = "Other")
                )
            },
            onFetchReactionsForChat = { emptyList() },
            onSendMessage = { _, _, _, _, _, _ -> null },
            onGetUserById = { id ->
                when (id) {
                    selfId -> User(id = selfId, name = "Me", createdAt = 0L)
                    otherId -> User(id = otherId, name = "Other", createdAt = 0L)
                    else -> null
                }
            }
        )

        val vm = ChatViewModel(
            tokenStorage = FakeTokenStorage(),
            chatRepository = fake,
            supabaseRepository = SupabaseRepository()
        )
        vm.setCurrentUser(selfId)
        advanceUntilIdle()

        vm.loadChatMessages(connectionId)
        advanceUntilIdle()

        vm.updateMessageInput("hi")
        vm.sendMessage()
        advanceUntilIdle()

        val messagesState = vm.chatMessagesState.value
        assertIs<ChatMessagesState.Success>(messagesState)
        assertTrue(messagesState.messages.any { it.message.deliveryState == MessageDeliveryState.ERROR })
        assertEquals("hi", vm.messageInput.value)
    }

    @Test
    fun realtimeMessageInsert_appendsIncomingMessage() = runVmTest {
        val selfId = "user-self"
        val otherId = "user-other"
        val connectionId = "conn-1"
        val apiChatId = "chat-api-1"
        val now = 1_700_000_000_000L

        val connection = Connection(
            id = connectionId,
            created = now,
            expiry = Long.MAX_VALUE,
            geo_location = GeoLocation(0.0, 0.0),
            user_ids = listOf(selfId, otherId),
            chat = Chat(id = apiChatId, connectionId = connectionId),
            has_begun = true,
            expiry_state = "active"
        )
        val details = ChatWithDetails(
            chat = connection.chat,
            connection = connection,
            otherUser = User(id = otherId, name = "Other"),
            lastMessage = null,
            unreadCount = 0
        )

        val messageEvents = MutableSharedFlow<ChatRealtimeEvent>(extraBufferCapacity = 16)

        val fake = FakeChatRepository(
            onFetchChatWithDetails = { _, uid ->
                if (uid == selfId) details else null
            },
            onFetchMessagesForChat = { _, _ -> emptyList() },
            onFetchChatParticipants = {
                listOf(
                    User(id = selfId, name = "Me"),
                    User(id = otherId, name = "Other")
                )
            },
            onFetchReactionsForChat = { emptyList() },
            onSubscribeToMessages = { _, _ ->
                object : ChatMessageSubscription {
                    override suspend fun attach() {}
                    override suspend fun detach() {}
                } to messageEvents.asSharedFlow()
            }
        )

        val vm = ChatViewModel(
            tokenStorage = FakeTokenStorage(),
            chatRepository = fake,
            supabaseRepository = SupabaseRepository()
        )
        vm.setCurrentUser(selfId)
        advanceUntilIdle()

        vm.loadChatMessages(connectionId)
        advanceUntilIdle()

        val incoming = Message(
            id = "msg-rt",
            user_id = otherId,
            content = "hello from realtime",
            timeCreated = now + 50,
            isRead = false
        )
        messageEvents.emit(ChatRealtimeEvent.Message(MessageChangeEvent.Insert(incoming)))
        advanceUntilIdle()

        val messagesState = vm.chatMessagesState.value
        assertIs<ChatMessagesState.Success>(messagesState)
        assertTrue(messagesState.messages.any { it.message.id == "msg-rt" && it.message.content == "hello from realtime" })
    }

    @Test
    fun realtimeMessageUpdate_promotesReadReceiptWhenReadAtSet() = runVmTest {
        val selfId = "user-self"
        val otherId = "user-other"
        val connectionId = "conn-1"
        val apiChatId = "chat-api-1"
        val now = 1_700_000_000_000L

        val connection = Connection(
            id = connectionId,
            created = now,
            expiry = Long.MAX_VALUE,
            geo_location = GeoLocation(0.0, 0.0),
            user_ids = listOf(selfId, otherId),
            chat = Chat(id = apiChatId, connectionId = connectionId),
            has_begun = true,
            expiry_state = "active"
        )
        val details = ChatWithDetails(
            chat = connection.chat,
            connection = connection,
            otherUser = User(id = otherId, name = "Other"),
            lastMessage = null,
            unreadCount = 0
        )

        val seeded = Message(
            id = "msg-1",
            user_id = selfId,
            content = "sent",
            timeCreated = now,
            isRead = false,
            readAt = null,
            deliveredAt = null,
            deliveryState = MessageDeliveryState.SENT,
        )

        val messageEvents = MutableSharedFlow<ChatRealtimeEvent>(extraBufferCapacity = 16)

        val fake = FakeChatRepository(
            onFetchChatWithDetails = { _, uid ->
                if (uid == selfId) details else null
            },
            onFetchMessagesForChat = { _, _ -> listOf(seeded) },
            onFetchChatParticipants = {
                listOf(
                    User(id = selfId, name = "Me"),
                    User(id = otherId, name = "Other")
                )
            },
            onFetchReactionsForChat = { emptyList() },
            onSubscribeToMessages = { _, _ ->
                object : ChatMessageSubscription {
                    override suspend fun attach() {}
                    override suspend fun detach() {}
                } to messageEvents.asSharedFlow()
            }
        )

        val vm = ChatViewModel(
            tokenStorage = FakeTokenStorage(),
            chatRepository = fake,
            supabaseRepository = SupabaseRepository()
        )
        vm.setCurrentUser(selfId)
        advanceUntilIdle()

        vm.loadChatMessages(connectionId)
        advanceUntilIdle()

        val readStamp = now + 99
        val updated = seeded.copy(readAt = readStamp, isRead = true)
        messageEvents.emit(ChatRealtimeEvent.Message(MessageChangeEvent.Update(updated)))
        advanceUntilIdle()

        val messagesState = vm.chatMessagesState.value
        assertIs<ChatMessagesState.Success>(messagesState)
        val row = messagesState.messages.first { it.message.id == "msg-1" }.message
        assertEquals(MessageDeliveryState.READ, row.deliveryState)
        assertEquals(readStamp, row.readAt)
    }
}
