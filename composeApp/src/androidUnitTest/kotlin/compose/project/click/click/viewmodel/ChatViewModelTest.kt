package compose.project.click.click.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import compose.project.click.click.data.AppDataManager
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
import compose.project.click.click.network.ConnectivityMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
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

private fun testChatViewModel(
    tokenStorage: FakeTokenStorage = FakeTokenStorage(),
    chatRepository: FakeChatRepository = FakeChatRepository(),
    supabaseRepository: SupabaseRepository = SupabaseRepository(),
    connectivityMonitor: ConnectivityMonitor = FakeConnectivityMonitor(initialOnline = true),
): ChatViewModel = ChatViewModel(
    tokenStorage = tokenStorage,
    chatRepository = chatRepository,
    supabaseRepository = supabaseRepository,
    connectivityMonitor = connectivityMonitor,
)

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
        runBlocking { AppDataManager.clearData() }
    }

    @Test
    fun initialState_chatListIsLoading() = runVmTest {
        val vm = testChatViewModel(
            chatRepository = FakeChatRepository()
        )
        advanceUntilIdle()
        assertTrue(vm.chatListState.value is ChatListState.Loading)
    }

    @Test
    fun setCurrentUser_updatesCurrentUserIdFlow() = runVmTest {
        val vm = testChatViewModel(
            chatRepository = FakeChatRepository()
        )
        vm.setCurrentUser("user-abc")
        advanceUntilIdle()
        assertEquals("user-abc", vm.currentUserId.value)
    }

    @Test
    fun loadChats_repositoryReturnsEmpty_setsSuccessWithEmptyList() = runVmTest {
        val fake = FakeChatRepository(
            onFetchDirectUserChatsWithDetails = { emptyList() },
            onFetchArchivedUserChatsWithDetails = { emptyList() },
            onFetchGroupUserChatsWithDetails = { emptyList() },
        )
        val vm = testChatViewModel(
            chatRepository = fake,
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
        val vm = testChatViewModel(
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

        val vm = testChatViewModel(
            chatRepository = fake,
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

        val vm = testChatViewModel(
            chatRepository = fake,
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

        val vm = testChatViewModel(
            chatRepository = fake,
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

        val vm = testChatViewModel(
            chatRepository = fake,
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

    @Test
    fun setCurrentUser_retriesPendingChatLoad() = runVmTest {
        val selfId = "user-self"
        val connectionId = "conn-1"
        val apiChatId = "chat-api-1"
        val now = 1_700_000_000_000L
        val connection = Connection(
            id = connectionId,
            created = now,
            expiry = Long.MAX_VALUE,
            geo_location = GeoLocation(0.0, 0.0),
            user_ids = listOf(selfId, "other"),
            chat = Chat(id = apiChatId, connectionId = connectionId),
            has_begun = true,
            expiry_state = "active",
        )
        val details = ChatWithDetails(
            chat = connection.chat,
            connection = connection,
            otherUser = User(id = "other", name = "Other"),
            lastMessage = null,
            unreadCount = 0,
        )
        val fake = FakeChatRepository(
            onFetchChatWithDetails = { _, uid -> if (uid == selfId) details else null },
            onFetchMessagesForChat = { _, _ -> emptyList() },
            onFetchChatParticipants = { listOf(User(id = selfId, name = "Me")) },
            onFetchReactionsForChat = { emptyList() },
        )
        val vm = testChatViewModel(chatRepository = fake)
        vm.loadChatMessages(connectionId)
        assertTrue(vm.chatMessagesState.value is ChatMessagesState.Loading)
        vm.setCurrentUser(selfId)
        advanceUntilIdle()
        assertIs<ChatMessagesState.Success>(vm.chatMessagesState.value)
    }

    @Test
    fun pushInboxBridge_updatesDecryptedPreviewOnList() = runVmTest {
        val selfId = "user-self"
        val connectionId = "conn-1"
        val now = 1_700_000_000_000L
        val connection = Connection(
            id = connectionId,
            created = now,
            expiry = Long.MAX_VALUE,
            geo_location = GeoLocation(0.0, 0.0),
            user_ids = listOf(selfId, "other"),
            chat = Chat(id = "chat-api-1", connectionId = connectionId),
            has_begun = true,
            expiry_state = "active",
        )
        val row = ChatWithDetails(
            chat = connection.chat,
            connection = connection,
            otherUser = User(id = "other", name = "Other"),
            lastMessage = null,
            unreadCount = 0,
        )
        val fake = FakeChatRepository(
            onFetchDirectUserChatsWithDetails = { listOf(row) },
            onFetchArchivedUserChatsWithDetails = { emptyList() },
            onFetchGroupUserChatsWithDetails = { emptyList() },
        )
        val vm = testChatViewModel(chatRepository = fake)
        vm.setCurrentUser(selfId)
        advanceUntilIdle()
        vm.loadChats(isForced = true)
        advanceUntilIdle()

        compose.project.click.click.notifications.ChatPushInboxBridge.applyChatMessagePush(
            chatId = "chat-api-1",
            connectionId = connectionId,
            senderUserId = "other",
            previewText = "Ping from push",
        )
        advanceUntilIdle()

        assertEquals("Ping from push", vm.decryptedPreviews.value[connectionId])
        val list = vm.chatListState.value as ChatListState.Success
        assertEquals("Ping from push", list.chats.first().lastMessage?.content)
    }

    @Test
    fun loadChats_forcedRefresh_preservesFresherRealtimePreviewOverStaleApiRow() = runVmTest {
        val selfId = "user-self"
        val connectionId = "conn-1"
        val oldTs = 1_700_000_000_000L
        val newTs = oldTs + 60_000L
        val connection = Connection(
            id = connectionId,
            created = oldTs,
            expiry = Long.MAX_VALUE,
            geo_location = GeoLocation(0.0, 0.0),
            user_ids = listOf(selfId, "other"),
            chat = Chat(id = "chat-api-1", connectionId = connectionId),
            has_begun = true,
            expiry_state = "active",
        )
        val staleMessage = Message(
            id = "msg-old",
            user_id = "other",
            content = "Old preview",
            timeCreated = oldTs,
        )
        val initialRow = ChatWithDetails(
            chat = connection.chat,
            connection = connection.copy(last_message_at = oldTs),
            otherUser = User(id = "other", name = "Other"),
            lastMessage = staleMessage,
            unreadCount = 0,
        )
        val fake = FakeChatRepository(
            onFetchDirectUserChatsWithDetails = { listOf(initialRow) },
            onFetchArchivedUserChatsWithDetails = { emptyList() },
            onFetchGroupUserChatsWithDetails = { emptyList() },
        )
        val vm = testChatViewModel(chatRepository = fake)
        vm.setCurrentUser(selfId)
        advanceUntilIdle()
        vm.loadChats(isForced = true)
        advanceUntilIdle()

        compose.project.click.click.notifications.ChatPushInboxBridge.applyChatMessagePush(
            chatId = "chat-api-1",
            connectionId = connectionId,
            senderUserId = "other",
            previewText = "Fresh realtime preview",
            timeCreated = newTs,
        )
        advanceUntilIdle()

        vm.loadChats(isForced = true)
        advanceUntilIdle()

        val list = vm.chatListState.value as ChatListState.Success
        assertEquals("Fresh realtime preview", list.chats.first().lastMessage?.content)
        assertEquals(newTs, list.chats.first().lastMessage?.timeCreated)
    }

    @Test
    fun pushInboxBridge_incrementsUnreadWhenNotViewingThread() = runVmTest {
        val selfId = "user-self"
        val connectionId = "conn-1"
        val oldTs = 1_700_000_000_000L
        val newTs = oldTs + 60_000L
        val connection = Connection(
            id = connectionId,
            created = oldTs,
            expiry = Long.MAX_VALUE,
            geo_location = GeoLocation(0.0, 0.0),
            user_ids = listOf(selfId, "other"),
            chat = Chat(id = "chat-api-1", connectionId = connectionId),
            has_begun = true,
            expiry_state = "active",
        )
        val row = ChatWithDetails(
            chat = connection.chat,
            connection = connection,
            otherUser = User(id = "other", name = "Other"),
            lastMessage = null,
            unreadCount = 0,
        )
        val fake = FakeChatRepository(
            onFetchDirectUserChatsWithDetails = { listOf(row) },
            onFetchArchivedUserChatsWithDetails = { emptyList() },
            onFetchGroupUserChatsWithDetails = { emptyList() },
        )
        val vm = testChatViewModel(chatRepository = fake)
        vm.setCurrentUser(selfId)
        advanceUntilIdle()
        vm.loadChats(isForced = true)
        advanceUntilIdle()

        compose.project.click.click.notifications.ChatPushInboxBridge.applyChatMessagePush(
            chatId = "chat-api-1",
            connectionId = connectionId,
            senderUserId = "other",
            previewText = "New inbound",
            timeCreated = newTs,
        )
        advanceUntilIdle()

        val list = vm.chatListState.value as ChatListState.Success
        assertEquals(1, list.chats.first().unreadCount)
    }

    @Test
    fun markConversationUnread_bumpsInboxBadgeLocally() = runVmTest {
        val selfId = "user-self"
        val connectionId = "conn-1"
        val now = 1_700_000_000_000L
        val connection = Connection(
            id = connectionId,
            created = now,
            expiry = Long.MAX_VALUE,
            geo_location = GeoLocation(0.0, 0.0),
            user_ids = listOf(selfId, "other"),
            chat = Chat(id = "chat-api-1", connectionId = connectionId),
            has_begun = true,
            expiry_state = "active",
            last_message_at = now,
        )
        val row = ChatWithDetails(
            chat = connection.chat,
            connection = connection,
            otherUser = User(id = "other", name = "Other"),
            lastMessage = Message(
                id = "msg-1",
                user_id = "other",
                content = "Hello",
                timeCreated = now,
                isRead = true,
            ),
            unreadCount = 0,
        )
        val fake = FakeChatRepository(
            onFetchDirectUserChatsWithDetails = { listOf(row) },
            onFetchArchivedUserChatsWithDetails = { emptyList() },
            onFetchGroupUserChatsWithDetails = { emptyList() },
        )
        val vm = testChatViewModel(chatRepository = fake)
        vm.setCurrentUser(selfId)
        advanceUntilIdle()
        vm.loadChats(isForced = true)
        advanceUntilIdle()

        vm.markConversationUnread(connectionId)
        advanceUntilIdle()

        val list = vm.chatListState.value as ChatListState.Success
        assertEquals(1, list.chats.first().unreadCount)
        assertEquals(false, list.chats.first().lastMessage?.isRead)
    }

    @Test
    fun realtimePreview_survivesConnectionSnapshotWithAheadLastMessageAt() = runVmTest {
        val selfId = "user-self"
        val connectionId = "conn-1"
        val msgTs = 1_700_000_000_000L
        val triggerAheadTs = msgTs + 5_000L
        val connection = Connection(
            id = connectionId,
            created = msgTs,
            expiry = Long.MAX_VALUE,
            geo_location = GeoLocation(0.0, 0.0),
            user_ids = listOf(selfId, "other"),
            chat = Chat(id = "chat-api-1", connectionId = connectionId),
            has_begun = true,
            expiry_state = "active",
        )
        val row = ChatWithDetails(
            chat = connection.chat,
            connection = connection,
            otherUser = User(id = "other", name = "Other"),
            lastMessage = null,
            unreadCount = 0,
        )
        val fake = FakeChatRepository(
            onFetchDirectUserChatsWithDetails = { listOf(row) },
            onFetchArchivedUserChatsWithDetails = { emptyList() },
            onFetchGroupUserChatsWithDetails = { emptyList() },
        )
        val vm = testChatViewModel(chatRepository = fake)
        vm.setCurrentUser(selfId)
        advanceUntilIdle()
        vm.loadChats(isForced = true)
        advanceUntilIdle()

        compose.project.click.click.notifications.ChatPushInboxBridge.applyChatMessagePush(
            chatId = "chat-api-1",
            connectionId = connectionId,
            senderUserId = "other",
            previewText = "Live preview text",
            timeCreated = msgTs,
        )
        advanceUntilIdle()

        AppDataManager.updateConnectionChatActivity(connectionId, triggerAheadTs, null)
        advanceUntilIdle()

        val list = vm.chatListState.value as ChatListState.Success
        assertEquals("Live preview text", list.chats.first().lastMessage?.content)
        assertEquals(msgTs, list.chats.first().lastMessage?.timeCreated)
    }
}
