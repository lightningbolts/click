package compose.project.click.click.viewmodel

import compose.project.click.click.data.models.Chat
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.MessageReaction
import compose.project.click.click.data.models.User
import compose.project.click.click.data.repository.ChatMessageSubscription
import compose.project.click.click.data.repository.ChatRealtimeEvent
import compose.project.click.click.data.repository.ChatRepository
import compose.project.click.click.data.repository.PresenceHealth
import compose.project.click.click.data.repository.UnifiedSearchSupplement
import compose.project.click.click.data.repository.MessageChangeEvent
import compose.project.click.click.data.repository.MessageListInsertEvent
import compose.project.click.click.data.repository.TypingStatus
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.JsonElement

private object NoopMessageSubscription : ChatMessageSubscription {
    override suspend fun attach() {}
    override suspend fun detach() {}
}

/**
 * Test double for [ChatRepository]. Defaults are inert; override lambdas to drive behavior.
 */
class FakeChatRepository(
    var onFetchUserChatsWithDetails: suspend (String) -> List<ChatWithDetails> = { emptyList() },
    var onFetchDirectUserChatsWithDetails: suspend (String) -> List<ChatWithDetails> = { emptyList() },
    var onFetchGroupUserChatsWithDetails: suspend (String) -> List<ChatWithDetails> = { emptyList() },
    var onDecryptGroupChatPreview: suspend (String, String) -> Message? = { _, _ -> null },
    var onFetchArchivedUserChatsWithDetails: suspend (String) -> List<ChatWithDetails> = { emptyList() },
    var onFetchChatWithDetails: suspend (String, String) -> ChatWithDetails? = { _, _ -> null },
    var onFetchMessagesForChat: suspend (String, String?) -> List<Message>? = { _, _ -> emptyList() },
    var onFetchChatParticipants: suspend (String) -> List<User> = { emptyList() },
    var onFetchReactionsForChat: suspend (String) -> List<MessageReaction> = { emptyList() },
    var onSubscribeToMessages: suspend (String, String) -> Pair<ChatMessageSubscription, Flow<ChatRealtimeEvent>> = { _, _ ->
        NoopMessageSubscription to emptyFlow()
    },
    var onSubscribeToMessageInserts: suspend () -> Pair<ChatMessageSubscription, Flow<MessageListInsertEvent>> = {
        NoopMessageSubscription to emptyFlow()
    },
    var onObserveTypingStatus: (String) -> Flow<TypingStatus> = {
        flow { awaitCancellation() }
    },
    var onObservePeerOnline: (String, String) -> Flow<Boolean> = { _, _ -> flowOf(false) },
    var onStartGlobalPresence: suspend (String) -> Unit = { },
    var onStopGlobalPresence: suspend () -> Unit = { },
    var onSendMessage: suspend (String, String, String, String, JsonElement?, Long?) -> Message? =
        { _, _, _, _, _, _ -> null },
    var onEnsureChatForConnection: suspend (String) -> Chat? = { null },
    var onGetUserById: suspend (String) -> User? = { null },
    var onUnifiedSearchSupplement: suspend (viewerUserId: String, peerUserIds: List<String>) -> UnifiedSearchSupplement =
        { _, _ -> UnifiedSearchSupplement.EMPTY },
    var onSearchMessagesByConnectionId: suspend (connectionId: String, query: String) -> Pair<String?, List<Message>> =
        { _, _ -> null to emptyList() },
) : ChatRepository {

    private val _onlineUsers = MutableStateFlow<Set<String>>(emptySet())
    override val onlineUsers: StateFlow<Set<String>> = _onlineUsers.asStateFlow()
    private val _presenceHealth = MutableStateFlow(PresenceHealth.Idle)
    override val presenceHealth: StateFlow<PresenceHealth> = _presenceHealth.asStateFlow()

    override suspend fun startGlobalPresence(userId: String) = onStartGlobalPresence(userId)

    override suspend fun stopGlobalPresence() = onStopGlobalPresence()

    override suspend fun cacheEncryptionKeys(chatId: String, connectionId: String, userIds: List<String>) {}

    override suspend fun cacheGroupMasterKey(chatId: String, masterKey: ByteArray) {}

    override suspend fun clearSessionCaches() {}

    override suspend fun fetchUserChatsWithDetails(userId: String): List<ChatWithDetails> =
        onFetchUserChatsWithDetails(userId)

    override suspend fun fetchDirectUserChatsWithDetails(userId: String): List<ChatWithDetails> =
        onFetchDirectUserChatsWithDetails(userId)

    override suspend fun fetchGroupUserChatsWithDetails(userId: String): List<ChatWithDetails> =
        onFetchGroupUserChatsWithDetails(userId)

    override suspend fun decryptGroupChatPreview(chatId: String, viewerUserId: String): Message? =
        onDecryptGroupChatPreview(chatId, viewerUserId)

    override suspend fun fetchArchivedUserChatsWithDetails(userId: String): List<ChatWithDetails> =
        onFetchArchivedUserChatsWithDetails(userId)

    override suspend fun fetchMessagesForChat(chatId: String, viewerUserId: String?): List<Message>? =
        onFetchMessagesForChat(chatId, viewerUserId)

    override suspend fun sendMessage(
        chatId: String,
        userId: String,
        content: String,
        messageType: String,
        metadata: JsonElement?,
        clientLocalSentAtMs: Long?,
    ): Message? =
        onSendMessage(chatId, userId, content, messageType, metadata, clientLocalSentAtMs)

    override suspend fun ensureChatForConnection(connectionId: String): Chat? =
        onEnsureChatForConnection(connectionId)

    override suspend fun sendMessageForConnection(
        connectionId: String,
        userId: String,
        content: String,
        messageType: String,
        metadata: JsonElement?,
    ): Message? {
        val chat = ensureChatForConnection(connectionId) ?: return null
        val id = chat.id ?: return null
        return sendMessage(id, userId, content, messageType, metadata)
    }

    override suspend fun markMessagesAsRead(chatId: String, userId: String) {}

    override suspend fun markMessagesDelivered(chatId: String, messageIds: List<String>) {}

    override suspend fun subscribeToMessages(
        chatId: String,
        viewerUserId: String,
    ): Pair<ChatMessageSubscription, Flow<ChatRealtimeEvent>> =
        onSubscribeToMessages(chatId, viewerUserId)

    override suspend fun subscribeToMessageInserts(): Pair<ChatMessageSubscription, Flow<MessageListInsertEvent>> =
        onSubscribeToMessageInserts()

    override suspend fun fetchChatById(chatId: String): Chat? = null

    override suspend fun fetchChatWithDetails(chatId: String, currentUserId: String): ChatWithDetails? =
        onFetchChatWithDetails(chatId, currentUserId)

    override suspend fun fetchChatParticipants(chatId: String): List<User> =
        onFetchChatParticipants(chatId)

    override suspend fun getUserById(userId: String): User? = onGetUserById(userId)

    override suspend fun updateMessage(chatId: String, messageId: String, userId: String, content: String): Message? =
        null

    override suspend fun deleteMessage(chatId: String, messageId: String, userId: String): Boolean = false

    override suspend fun fetchReactionsForChat(chatId: String): List<MessageReaction> =
        onFetchReactionsForChat(chatId)

    override suspend fun addReaction(messageId: String, userId: String, reactionType: String): Boolean = false

    override suspend fun removeReaction(messageId: String, userId: String, reactionType: String): Boolean = false

    override suspend fun sendTypingStatus(chatId: String, userId: String, isTyping: Boolean) {}

    override fun observeTypingStatus(chatId: String): Flow<TypingStatus> = onObserveTypingStatus(chatId)

    override suspend fun getTypingUsers(chatId: String): List<String> = emptyList()

    override suspend fun joinChatEphemeralChannel(chatId: String, currentUserId: String, peerUserId: String) {}

    override suspend fun leaveChatEphemeralChannel(chatId: String) {}

    override fun observePeerOnline(chatId: String, peerUserId: String): Flow<Boolean> =
        onObservePeerOnline(chatId, peerUserId)

    override suspend fun updateMessageStatus(messageId: String, status: String): Boolean = false

    override suspend fun forwardMessage(messageId: String, targetChatId: String, userId: String): Message? = null

    override suspend fun searchMessages(chatId: String, query: String): List<Message> = emptyList()

    override suspend fun resolveChatIdForConnection(connectionId: String): String? = null

    override suspend fun resolveChatIdForGroupId(groupId: String): String? = null

    override suspend fun createVerifiedClique(
        memberUserIds: List<String>,
        encryptedKeysByUserId: Map<String, String>,
        initialGroupName: String,
    ): Result<String> = Result.failure(UnsupportedOperationException())

    override suspend fun leaveClique(groupId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    override suspend fun deleteClique(groupId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    override suspend fun renameClique(groupId: String, newName: String): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    override suspend fun verifiedCliqueEdgesExist(memberUserIds: List<String>): Boolean = true

    override fun clearChatListLocalCaches() {}

    override suspend fun searchMessagesByConnectionId(connectionId: String, query: String): Pair<String?, List<Message>> =
        onSearchMessagesByConnectionId(connectionId, query)

    override suspend fun unifiedSearchSupplement(
        viewerUserId: String,
        peerUserIds: List<String>,
    ): UnifiedSearchSupplement = onUnifiedSearchSupplement(viewerUserId, peerUserIds)

    override suspend fun uploadChatMedia(bytes: ByteArray, objectPath: String, contentType: String): String? = null

    override suspend fun downloadAndDecryptChatMedia(
        chatId: String,
        viewerUserId: String,
        mediaUrl: String,
    ): ByteArray? = null

    override suspend fun uploadEncryptedBlob(
        bucketName: String,
        chatId: String,
        senderUserId: String,
        plainBytes: ByteArray,
        mimeType: String,
        fileName: String,
    ): ChatRepository.EncryptedAttachmentUpload? = null

    override suspend fun downloadAttachmentPlaintext(
        path: String,
        fileMasterKeyBase64: String,
        expectedSha256Base64: String,
    ): ByteArray? = null
}
