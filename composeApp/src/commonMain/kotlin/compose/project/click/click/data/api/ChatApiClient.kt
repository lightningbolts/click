package compose.project.click.click.data.api

import compose.project.click.click.data.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * API client for chat-related operations with the Flask backend
 */
class ChatApiClient(
    private val baseUrl: String = ApiConfig.BASE_URL,
    private val httpClient: HttpClient? = null
) {
    private val client = httpClient ?: HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            })
        }
    }

    // Response wrapper classes
    @Serializable
    data class ChatsResponse(val chats: List<ChatApiModel>)

    @Serializable
    data class ChatResponse(val chat: ChatApiModel)

    @Serializable
    data class MessagesResponse(val messages: List<MessageApiModel>)

    @Serializable
    data class MessageResponse(val message: MessageApiModel)

    @Serializable
    data class ParticipantsResponse(val participants: List<UserApiModel>)

    @Serializable
    data class SendMessageRequest(val user_id: String, val content: String)

    @Serializable
    data class MarkReadRequest(val user_id: String)

    @Serializable
    data class UpdateMessageRequest(val user_id: String, val content: String)

    @Serializable
    data class DeleteMessageRequest(val user_id: String)

    @Serializable
    data class ReactionsResponse(val reactions: List<ReactionApiModel>)

    @Serializable
    data class ReactionApiModel(
        val id: String,
        val message_id: String,
        val user_id: String,
        val reaction_type: String,
        val created_at: Long
    )

    @Serializable
    data class AddReactionRequest(val user_id: String, val reaction_type: String)

    @Serializable
    data class RemoveReactionRequest(val user_id: String, val reaction_type: String)

    @Serializable
    data class TypingRequest(val user_id: String)

    @Serializable
    data class StatusUpdateRequest(val status: String)

    @Serializable
    data class ForwardMessageRequest(val target_chat_id: String, val user_id: String)

    @Serializable
    data class SearchMessagesResponse(val messages: List<MessageApiModel>)

    // API Models (snake_case to match Python API)
    @Serializable
    data class ChatApiModel(
        val id: String,
        val connection_id: String,
        val created_at: Long,
        val updated_at: Long,
        val connection: ConnectionApiModel? = null,
        val other_user: UserApiModel? = null,
        val last_message: MessageApiModel? = null,
        val unread_count: Int = 0
    )

    @Serializable
    data class MessageApiModel(
        val id: String,
        val chat_id: String,
        val user_id: String,
        val content: String,
        val created_at: Long,
        val updated_at: Long? = null,
        val is_read: Boolean,
        val status: String? = null
    )

    @Serializable
    data class UserApiModel(
        val id: String,
        val name: String,
        val email: String,
        val image: String? = null
    )

    @Serializable
    data class ConnectionApiModel(
        val id: String,
        val user1_id: String,
        val user2_id: String,
        val chat_id: String? = null,
        val location: String? = null,
        val created: Long,
        val expiry: Long,
        val should_continue: Boolean = false
    )

    /**
     * Get all chats for a user with details
     */
    suspend fun getUserChats(userId: String, authToken: String): Result<List<ChatWithDetails>> {
        return try {
            val response = client.get("$baseUrl/api/chats/user/$userId") {
                header("Authorization", authToken)
            }

            if (response.status.value in 200..299) {
                val chatsResponse = response.body<ChatsResponse>()
                val chats = chatsResponse.chats.map { it.toChatWithDetails() }
                Result.success(chats)
            } else {
                Result.failure(Exception("Failed to fetch chats: ${response.status}"))
            }
        } catch (e: Exception) {
            println("Error fetching user chats: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get a specific chat by ID
     */
    suspend fun getChat(chatId: String, authToken: String): Result<Chat> {
        return try {
            val response = client.get("$baseUrl/api/chats/$chatId") {
                header("Authorization", authToken)
            }

            if (response.status.value in 200..299) {
                val chatResponse = response.body<ChatResponse>()
                Result.success(chatResponse.chat.toChat())
            } else {
                Result.failure(Exception("Failed to fetch chat: ${response.status}"))
            }
        } catch (e: Exception) {
            println("Error fetching chat: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get all messages for a specific chat
     */
    suspend fun getChatMessages(chatId: String, authToken: String): Result<List<Message>> {
        return try {
            val response = client.get("$baseUrl/api/chats/$chatId/messages") {
                header("Authorization", authToken)
            }

            if (response.status.value in 200..299) {
                val messagesResponse = response.body<MessagesResponse>()
                val messages = messagesResponse.messages.map { it.toMessage() }
                Result.success(messages)
            } else {
                Result.failure(Exception("Failed to fetch messages: ${response.status}"))
            }
        } catch (e: Exception) {
            println("Error fetching messages: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Send a new message in a chat
     */
    suspend fun sendMessage(
        chatId: String,
        userId: String,
        content: String,
        authToken: String
    ): Result<Message> {
        return try {
            val response = client.post("$baseUrl/api/chats/$chatId/messages") {
                header("Authorization", authToken)
                contentType(ContentType.Application.Json)
                setBody(SendMessageRequest(userId, content))
            }

            if (response.status.value in 200..299) {
                val messageResponse = response.body<MessageResponse>()
                Result.success(messageResponse.message.toMessage())
            } else {
                Result.failure(Exception("Failed to send message: ${response.status}"))
            }
        } catch (e: Exception) {
            println("Error sending message: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Mark messages as read for a user
     */
    suspend fun markMessagesAsRead(
        chatId: String,
        userId: String,
        authToken: String
    ): Result<Boolean> {
        return try {
            val response = client.post("$baseUrl/api/chats/$chatId/mark_read") {
                header("Authorization", authToken)
                contentType(ContentType.Application.Json)
                setBody(MarkReadRequest(userId))
            }

            Result.success(response.status.value in 200..299)
        } catch (e: Exception) {
            println("Error marking messages as read: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Update a message's content
     */
    suspend fun updateMessage(
        chatId: String,
        messageId: String,
        userId: String,
        content: String,
        authToken: String
    ): Result<Message> {
        return try {
            val response = client.put("$baseUrl/api/chats/$chatId/messages/$messageId") {
                header("Authorization", authToken)
                contentType(ContentType.Application.Json)
                setBody(UpdateMessageRequest(userId, content))
            }

            if (response.status.value in 200..299) {
                val messageResponse = response.body<MessageResponse>()
                Result.success(messageResponse.message.toMessage())
            } else {
                Result.failure(Exception("Failed to update message: ${response.status}"))
            }
        } catch (e: Exception) {
            println("Error updating message: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Delete a message
     */
    suspend fun deleteMessage(
        chatId: String,
        messageId: String,
        userId: String,
        authToken: String
    ): Result<Boolean> {
        return try {
            val response = client.delete("$baseUrl/api/chats/$chatId/messages/$messageId") {
                header("Authorization", authToken)
                contentType(ContentType.Application.Json)
                setBody(DeleteMessageRequest(userId))
            }

            Result.success(response.status.value in 200..299)
        } catch (e: Exception) {
            println("Error deleting message: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get chat for a connection
     */
    suspend fun getChatForConnection(
        connectionId: String,
        authToken: String
    ): Result<Chat> {
        return try {
            val response = client.get("$baseUrl/api/chats/connection/$connectionId") {
                header("Authorization", authToken)
            }

            if (response.status.value in 200..299) {
                val chatResponse = response.body<ChatResponse>()
                Result.success(chatResponse.chat.toChat())
            } else {
                Result.failure(Exception("Failed to fetch chat for connection: ${response.status}"))
            }
        } catch (e: Exception) {
            println("Error fetching chat for connection: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get participants in a chat
     */
    suspend fun getChatParticipants(
        chatId: String,
        authToken: String
    ): Result<List<User>> {
        return try {
            val response = client.get("$baseUrl/api/chats/$chatId/participants") {
                header("Authorization", authToken)
            }

            if (response.status.value in 200..299) {
                val participantsResponse = response.body<ParticipantsResponse>()
                val participants = participantsResponse.participants.map { it.toUser() }
                Result.success(participants)
            } else {
                Result.failure(Exception("Failed to fetch participants: ${response.status}"))
            }
        } catch (e: Exception) {
            println("Error fetching participants: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get reactions for a message
     */
    suspend fun getMessageReactions(messageId: String, authToken: String): Result<List<MessageReaction>> {
        return try {
            val response = client.get("$baseUrl/api/messages/$messageId/reactions") {
                header("Authorization", authToken)
            }
            if (response.status.value in 200..299) {
                val reactionsResponse = response.body<ReactionsResponse>()
                Result.success(reactionsResponse.reactions.map { it.toReaction() })
            } else Result.failure(Exception("Failed to fetch reactions: ${response.status}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Add a reaction to a message
     */
    suspend fun addReaction(messageId: String, userId: String, reactionType: String, authToken: String): Result<MessageReaction> {
        return try {
            val response = client.post("$baseUrl/api/messages/$messageId/reactions") {
                header("Authorization", authToken)
                contentType(ContentType.Application.Json)
                setBody(AddReactionRequest(user_id = userId, reaction_type = reactionType))
            }
            if (response.status.value in 200..299) {
                val reactionWrapper = response.body<ReactionResponse>()
                Result.success(reactionWrapper.reaction.toReaction())
            } else Result.failure(Exception("Failed to add reaction: ${response.status}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    /**
     * Remove a reaction from a message
     */
    suspend fun removeReaction(messageId: String, userId: String, reactionType: String, authToken: String): Result<Boolean> {
        return try {
            val response = client.delete("$baseUrl/api/messages/$messageId/reactions") {
                header("Authorization", authToken)
                contentType(ContentType.Application.Json)
                setBody(RemoveReactionRequest(user_id = userId, reaction_type = reactionType))
            }
            Result.success(response.status.value in 200..299)
        } catch (e: Exception) { Result.failure(e) }
    }

    /**
     * Set typing status for a chat
     */
    suspend fun setTyping(chatId: String, userId: String, authToken: String): Result<Boolean> {
        return try {
            val response = client.post("$baseUrl/api/chats/$chatId/typing") {
                header("Authorization", authToken)
                contentType(ContentType.Application.Json)
                setBody(TypingRequest(user_id = userId))
            }
            Result.success(response.status.value in 200..299)
        } catch (e: Exception) { Result.failure(e) }
    }

    @Serializable
    data class TypingUsersResponse(val user_ids: List<String>)

    /**
     * Get list of users currently typing in a chat
     */
    suspend fun getTypingUsers(chatId: String, authToken: String): Result<List<String>> {
        return try {
            val response = client.get("$baseUrl/api/chats/$chatId/typing") { header("Authorization", authToken) }
            if (response.status.value in 200..299) {
                val typingResponse = response.body<TypingUsersResponse>()
                Result.success(typingResponse.user_ids)
            } else Result.failure(Exception("Failed to fetch typing users: ${response.status}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    /**
     * Update the status of a message
     */
    suspend fun updateMessageStatus(messageId: String, status: String, authToken: String): Result<Boolean> {
        return try {
            val response = client.post("$baseUrl/api/messages/$messageId/status") {
                header("Authorization", authToken)
                contentType(ContentType.Application.Json)
                setBody(StatusUpdateRequest(status = status))
            }
            Result.success(response.status.value in 200..299)
        } catch (e: Exception) { Result.failure(e) }
    }

    /**
     * Forward a message to another chat
     */
    suspend fun forwardMessage(messageId: String, targetChatId: String, userId: String, authToken: String): Result<Message> {
        return try {
            val response = client.post("$baseUrl/api/messages/$messageId/forward") {
                header("Authorization", authToken)
                contentType(ContentType.Application.Json)
                setBody(ForwardMessageRequest(target_chat_id = targetChatId, user_id = userId))
            }
            if (response.status.value in 200..299) {
                val msgResponse = response.body<MessageResponse>()
                Result.success(msgResponse.message.toMessage())
            } else Result.failure(Exception("Failed to forward message: ${response.status}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    /**
     * Search messages in a chat
     */
    suspend fun searchMessages(chatId: String, query: String, authToken: String): Result<List<Message>> {
        return try {
            val response = client.get("$baseUrl/api/chats/$chatId/search") {
                header("Authorization", authToken)
                url { parameters.append("q", query) }
            }
            if (response.status.value in 200..299) {
                val searchResponse = response.body<SearchMessagesResponse>()
                Result.success(searchResponse.messages.map { it.toMessage() })
            } else Result.failure(Exception("Failed to search messages: ${response.status}"))
        } catch (e: Exception) { Result.failure(e) }
    }

    // Extension functions to convert API models to domain models
    private fun ChatApiModel.toChat(): Chat {
        return Chat(
            id = id,
            connectionId = connection_id,
            createdAt = created_at,
            updatedAt = updated_at
        )
    }

    private fun ChatApiModel.toChatWithDetails(): ChatWithDetails {
        return ChatWithDetails(
            chat = toChat(),
            connection = connection?.toConnection() ?: Connection(
                id = connection_id,
                user1Id = "",
                user2Id = "",
                chatId = id,
                location = null,
                created = created_at,
                expiry = created_at + 86400000,
                shouldContinue = false
            ),
            otherUser = other_user?.toUser() ?: User(
                id = "",
                name = "Unknown",
                email = "",
                image = null,
                shareKey = 0,
                connections = emptyList(),
                createdAt = 0
            ),
            lastMessage = last_message?.toMessage(),
            unreadCount = unread_count
        )
    }

    private fun ReactionApiModel.toReaction(): MessageReaction = MessageReaction(
        id = id,
        messageId = message_id,
        userId = user_id,
        reactionType = reaction_type,
        createdAt = created_at
    )

    private fun MessageApiModel.toMessage(): Message {
        return Message(
            id = id,
            chatId = chat_id,
            userId = user_id,
            content = content,
            createdAt = created_at,
            updatedAt = updated_at,
            isRead = is_read,
            status = status ?: "sent"
        )
    }

    private fun UserApiModel.toUser(): User {
        return User(
            id = id,
            name = name,
            email = email,
            image = image,
            shareKey = 0,
            connections = emptyList(),
            createdAt = 0
        )
    }

    private fun ConnectionApiModel.toConnection(): Connection {
        return Connection(
            id = id,
            user1Id = user1_id,
            user2Id = user2_id,
            chatId = chat_id ?: "",
            location = location,
            created = created,
            expiry = expiry,
            shouldContinue = should_continue
        )
    }

    @Serializable
    data class ReactionResponse(val reaction: ReactionApiModel)
}
