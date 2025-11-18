package compose.project.click.click.data.models

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String,
    val image: String? = null,
    val shareKey: Long = 0,
    val connections: List<String> = emptyList(),
    val createdAt: Long
)

@Serializable
data class Message(
    val id: String,
    val chatId: String,
    val userId: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long? = null,
    val isRead: Boolean = false,
    val status: String = "sent",
    val reactions: List<MessageReaction> = emptyList()
)

@Serializable
data class MessageReaction(
    val id: String,
    val messageId: String,
    val userId: String,
    val reactionType: String,
    val createdAt: Long
)

@Serializable
data class Connection(
    val id: String,
    val user1Id: String,
    val user2Id: String,
    val chatId: String,
    val location: String? = null,
    val created: Long,
    val expiry: Long? = null,
    val shouldContinue: Boolean = false
)

@Serializable
data class Chat(
    val id: String,
    val connectionId: String,
    val createdAt: Long,
    val updatedAt: Long
)

// UI models for chat functionality
data class ChatWithDetails(
    val chat: Chat,
    val connection: Connection,
    val otherUser: User,
    val lastMessage: Message?,
    val unreadCount: Int
)

data class MessageWithUser(
    val message: Message,
    val user: User,
    val isSent: Boolean
)
