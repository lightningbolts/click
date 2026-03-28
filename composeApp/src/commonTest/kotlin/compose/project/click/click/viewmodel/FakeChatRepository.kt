package compose.project.click.click.viewmodel

import compose.project.click.click.data.models.Chat
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.MessageReaction
import compose.project.click.click.data.models.User
import compose.project.click.click.data.repository.ChatMessageSubscription
import compose.project.click.click.data.repository.ChatReactionSubscription
import compose.project.click.click.data.repository.ChatRepository
import compose.project.click.click.data.repository.MessageChangeEvent
import compose.project.click.click.data.repository.MessageListInsertEvent
import compose.project.click.click.data.repository.ReactionChangeEvent
import compose.project.click.click.data.repository.TypingStatus
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.JsonElement

private object NoopMessageSubscription : ChatMessageSubscription {
    override suspend fun attach() {}
    override suspend fun detach() {}
}

private object NoopReactionSubscription : ChatReactionSubscription {
    override suspend fun attach() {}
    override suspend fun detach() {}
}

/**
 * Test double for [ChatRepository]. Defaults are inert; override lambdas to drive behavior.
 */
class FakeChatRepository(
    var onFetchUserChatsWithDetails: suspend (String) -> List<ChatWithDetails> = { emptyList() },
    var onFetchChatWithDetails: suspend (String, String) -> ChatWithDetails? = { _, _ -> null },
    var onFetchMessagesForChat: suspend (String) -> List<Message> = { emptyList() },
    var onFetchChatParticipants: suspend (String) -> List<User> = { emptyList() },
    var onFetchReactionsForChat: suspend (String) -> List<MessageReaction> = { emptyList() },
    var onSubscribeToMessages: suspend (String) -> Pair<ChatMessageSubscription, Flow<MessageChangeEvent>> = {
        NoopMessageSubscription to emptyFlow()
    },
    var onSubscribeToMessageInserts: suspend () -> Pair<ChatMessageSubscription, Flow<MessageListInsertEvent>> = {
        NoopMessageSubscription to emptyFlow()
    },
    var onSubscribeToReactions: (String) -> Pair<ChatReactionSubscription, Flow<ReactionChangeEvent>> = {
        NoopReactionSubscription to emptyFlow()
    },
    var onObserveTypingStatus: (String) -> Flow<TypingStatus> = {
        flow { awaitCancellation() }
    },
    var onObservePeerOnline: (String, String) -> Flow<Boolean> = { _, _ -> flowOf(false) },
    var onSendMessage: suspend (String, String, String, String, JsonElement?) -> Message? = { _, _, _, _, _ -> null },
    var onEnsureChatForConnection: suspend (String) -> Chat? = { null },
    var onGetUserById: suspend (String) -> User? = { null },
) : ChatRepository {

    override fun cacheEncryptionKeys(chatId: String, connectionId: String, userIds: List<String>) {}

    override suspend fun fetchUserChatsWithDetails(userId: String): List<ChatWithDetails> =
        onFetchUserChatsWithDetails(userId)

    override suspend fun fetchMessagesForChat(chatId: String): List<Message> =
        onFetchMessagesForChat(chatId)

    override suspend fun sendMessage(
        chatId: String,
        userId: String,
        content: String,
        messageType: String,
        metadata: JsonElement?,
    ): Message? =
        onSendMessage(chatId, userId, content, messageType, metadata)

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

    override suspend fun subscribeToMessages(chatId: String): Pair<ChatMessageSubscription, Flow<MessageChangeEvent>> =
        onSubscribeToMessages(chatId)

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

    override fun subscribeToReactions(chatId: String): Pair<ChatReactionSubscription, Flow<ReactionChangeEvent>> =
        onSubscribeToReactions(chatId)

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

    override suspend fun searchMessagesByConnectionId(connectionId: String, query: String): Pair<String?, List<Message>> =
        null to emptyList()
}
