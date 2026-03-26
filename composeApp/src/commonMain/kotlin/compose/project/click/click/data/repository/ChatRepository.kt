package compose.project.click.click.data.repository

import compose.project.click.click.data.models.Chat
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.MessageReaction
import compose.project.click.click.data.models.User
import kotlinx.coroutines.flow.Flow

/** Handle for attaching/detaching a Supabase realtime messages channel (testable without [RealtimeChannel]). */
interface ChatMessageSubscription {
    suspend fun attach()
    suspend fun detach()
}

/** Handle for attaching/detaching a Supabase realtime reactions channel. */
interface ChatReactionSubscription {
    suspend fun attach()
    suspend fun detach()
}

/**
 * Abstraction for chat data and realtime subscriptions (implemented by [SupabaseChatRepository]).
 */
interface ChatRepository {
    fun cacheEncryptionKeys(chatId: String, connectionId: String, userIds: List<String>)

    suspend fun fetchUserChatsWithDetails(userId: String): List<ChatWithDetails>

    suspend fun fetchMessagesForChat(chatId: String): List<Message>

    suspend fun sendMessage(chatId: String, userId: String, content: String): Message?

    suspend fun ensureChatForConnection(connectionId: String): Chat?

    suspend fun sendMessageForConnection(connectionId: String, userId: String, content: String): Message?

    suspend fun markMessagesAsRead(chatId: String, userId: String)

    suspend fun subscribeToMessages(chatId: String): Pair<ChatMessageSubscription, Flow<MessageChangeEvent>>

    suspend fun fetchChatById(chatId: String): Chat?

    suspend fun fetchChatWithDetails(chatId: String, currentUserId: String): ChatWithDetails?

    suspend fun fetchChatParticipants(chatId: String): List<User>

    suspend fun getUserById(userId: String): User?

    suspend fun updateMessage(chatId: String, messageId: String, userId: String, content: String): Message?

    suspend fun deleteMessage(chatId: String, messageId: String, userId: String): Boolean

    suspend fun fetchReactionsForChat(chatId: String): List<MessageReaction>

    suspend fun addReaction(messageId: String, userId: String, reactionType: String): Boolean

    suspend fun removeReaction(messageId: String, userId: String, reactionType: String): Boolean

    fun subscribeToReactions(chatId: String): Pair<ChatReactionSubscription, Flow<ReactionChangeEvent>>

    suspend fun sendTypingStatus(chatId: String, userId: String, isTyping: Boolean)

    fun observeTypingStatus(chatId: String): Flow<TypingStatus>

    suspend fun getTypingUsers(chatId: String): List<String>

    suspend fun updateMessageStatus(messageId: String, status: String): Boolean

    suspend fun forwardMessage(messageId: String, targetChatId: String, userId: String): Message?

    suspend fun searchMessages(chatId: String, query: String): List<Message>

    suspend fun resolveChatIdForConnection(connectionId: String): String?

    suspend fun searchMessagesByConnectionId(connectionId: String, query: String): Pair<String?, List<Message>>
}
