package compose.project.click.click.data.repository

import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.api.ChatApiClient
import compose.project.click.click.data.models.*
import compose.project.click.click.data.storage.TokenStorage
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.broadcast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.decodeFromJsonElement
import io.github.jan.supabase.realtime.RealtimeChannel

/**
 * Repository for chat operations
 * Uses the Python API for CRUD operations and Supabase Realtime for instant message updates
 */
class ChatRepository(
    private val apiClient: ChatApiClient = ChatApiClient(),
    private val tokenStorage: TokenStorage
) {
    private val supabase = SupabaseConfig.client

    // Fetch all chats for a user with details via API
    suspend fun fetchUserChatsWithDetails(userId: String): List<ChatWithDetails> {
        return try {
            val authToken = tokenStorage.getJwt() ?: return emptyList()
            val result = apiClient.getUserChats(userId, authToken)
            result.getOrElse {
                println("Error fetching user chats: ${it.message}")
                emptyList()
            }
        } catch (e: Exception) {
            println("Error fetching user chats: ${e.message}")
            emptyList()
        }
    }

    // Fetch messages for a specific chat via API
    suspend fun fetchMessagesForChat(chatId: String): List<Message> {
        return try {
            val authToken = tokenStorage.getJwt() ?: return emptyList()
            val result = apiClient.getChatMessages(chatId, authToken)
            val baseMessages = result.getOrElse {
                println("Error fetching messages: ${it.message}")
                emptyList()
            }
            // Return messages as-is (reactions will be fetched separately if needed)
            baseMessages
        } catch (e: Exception) {
            println("Error fetching messages: ${e.message}")
            emptyList()
        }
    }

    // Send a new message via API
    suspend fun sendMessage(chatId: String, userId: String, content: String): Message? {
        return try {
            val authToken = tokenStorage.getJwt() ?: return null
            val result = apiClient.sendMessage(chatId, userId, content, authToken)
            result.getOrElse {
                println("Error sending message: ${it.message}")
                null
            }
        } catch (e: Exception) {
            println("Error sending message: ${e.message}")
            null
        }
    }

    // Mark messages as read via API
    suspend fun markMessagesAsRead(chatId: String, userId: String) {
        try {
            val authToken = tokenStorage.getJwt() ?: return
            apiClient.markMessagesAsRead(chatId, userId, authToken)
        } catch (e: Exception) {
            println("Error marking messages as read: ${e.message}")
        }
    }

    // Subscribe to new messages in a chat using Supabase Realtime
    // This remains direct to Supabase for best real-time performance
    fun subscribeToMessages(chatId: String): Flow<Message> {
        val channel = supabase.channel("messages:$chatId")

        val messageFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
        }.map { action ->
            when (action) {
                is PostgresAction.Insert -> action.decodeRecord<Message>()
                is PostgresAction.Update -> action.decodeRecord<Message>()
                else -> null
            }
        }.map { it ?: throw IllegalStateException("Unknown message action") }

        return messageFlow
    }

    // Fetch a specific chat by ID via API
    suspend fun fetchChatById(chatId: String): Chat? {
        return try {
            val authToken = tokenStorage.getJwt() ?: return null
            val result = apiClient.getChat(chatId, authToken)
            result.getOrElse {
                println("Error fetching chat: ${it.message}")
                null
            }
        } catch (e: Exception) {
            println("Error fetching chat: ${e.message}")
            null
        }
    }

    // Fetch chat with details by chat ID via API
    suspend fun fetchChatWithDetails(chatId: String, currentUserId: String): ChatWithDetails? {
        return try {
            val authToken = tokenStorage.getJwt() ?: return null
            val result = apiClient.getUserChats(currentUserId, authToken)
            result.getOrElse { emptyList() }
                .firstOrNull { it.connection.id == chatId }
        } catch (e: Exception) {
            println("Error fetching chat with details: ${e.message}")
            null
        }
    }

    // Get participants for a chat via API
    suspend fun fetchChatParticipants(chatId: String): List<User> {
        return try {
            val authToken = tokenStorage.getJwt() ?: return emptyList()
            val result = apiClient.getChatParticipants(chatId, authToken)
            result.getOrElse {
                println("Error fetching participants: ${it.message}")
                emptyList()
            }
        } catch (e: Exception) {
            println("Error fetching participants: ${e.message}")
            emptyList()
        }
    }

    // Get user by ID - helper method for getting user details
    suspend fun getUserById(userId: String): User? {
        // This is still needed for single lookups, but fetchChatParticipants should be used for the list
        return try {
            val authToken = tokenStorage.getJwt() ?: return null
            // We use a dummy chatId or improve the API to support single user lookup
            val result = apiClient.getUser(userId, authToken)
            result.getOrNull()
        } catch (e: Exception) {
            println("Error fetching user: ${e.message}")
            null
        }
    }

    // Update a message via API
    suspend fun updateMessage(chatId: String, messageId: String, userId: String, content: String): Message? {
        return try {
            val authToken = tokenStorage.getJwt() ?: return null
            val result = apiClient.updateMessage(chatId, messageId, userId, content, authToken)
            result.getOrElse {
                println("Error updating message: ${it.message}")
                null
            }
        } catch (e: Exception) {
            println("Error updating message: ${e.message}")
            null
        }
    }

    // Delete a message via API
    suspend fun deleteMessage(chatId: String, messageId: String, userId: String): Boolean {
        return try {
            val authToken = tokenStorage.getJwt() ?: return false
            val result = apiClient.deleteMessage(chatId, messageId, userId, authToken)
            result.getOrElse {
                println("Error deleting message: ${it.message}")
                false
            }
        } catch (e: Exception) {
            println("Error deleting message: ${e.message}")
            false
        }
    }

    suspend fun addReaction(messageId: String, userId: String, reactionType: String): Boolean {
        return try {
            val authToken = tokenStorage.getJwt() ?: return false
            apiClient.addReaction(messageId, userId, reactionType, authToken).isSuccess
        } catch (e: Exception) { println("Error adding reaction: ${e.message}"); false }
    }

    suspend fun removeReaction(messageId: String, userId: String, reactionType: String): Boolean {
        return try {
            val authToken = tokenStorage.getJwt() ?: return false
            apiClient.removeReaction(messageId, userId, reactionType, authToken).getOrElse { false }
        } catch (e: Exception) { println("Error removing reaction: ${e.message}"); false }
    }

    private val typingChannels = mutableMapOf<String, RealtimeChannel>()

    suspend fun sendTypingStatus(chatId: String, userId: String, isTyping: Boolean) {
        try {
            var channel = typingChannels[chatId]
            if (channel == null) {
                channel = supabase.channel("typing:$chatId")
                channel.subscribe()
                typingChannels[chatId] = channel
            }
            channel.broadcast(
                event = "typing",
                message = buildJsonObject {
                    put("userId", userId)
                    put("isTyping", isTyping)
                }
            )
        } catch (e: Exception) {
            println("Error sending typing status: ${e.message}")
        }
    }

    fun observeTypingStatus(chatId: String): Flow<TypingStatus> {
        val channel = typingChannels.getOrPut(chatId) {
            supabase.channel("typing:$chatId")
        }
        return channel.broadcastFlow<TypingStatus>("typing")
    }

    @Serializable
    data class TypingStatus(val userId: String, val isTyping: Boolean)

    suspend fun getTypingUsers(chatId: String): List<String> {
        // This can be kept as a fallback or removed if using observeTypingStatus exclusively
        return try {
            val authToken = tokenStorage.getJwt() ?: return emptyList()
            apiClient.getTypingUsers(chatId, authToken).getOrElse { emptyList() }
        } catch (e: Exception) { println("Error getting typing users: ${e.message}"); emptyList() }
    }

    suspend fun updateMessageStatus(messageId: String, status: String): Boolean {
        return try {
            val authToken = tokenStorage.getJwt() ?: return false
            apiClient.updateMessageStatus(messageId, status, authToken).getOrElse { false }
        } catch (e: Exception) { println("Error updating status: ${e.message}"); false }
    }

    suspend fun forwardMessage(messageId: String, targetChatId: String, userId: String): Message? {
        return try {
            val authToken = tokenStorage.getJwt() ?: return null
            apiClient.forwardMessage(messageId, targetChatId, userId, authToken).getOrElse { null }
        } catch (e: Exception) { println("Error forwarding message: ${e.message}"); null }
    }

    suspend fun searchMessages(chatId: String, query: String): List<Message> {
        return try {
            val authToken = tokenStorage.getJwt() ?: return emptyList()
            apiClient.searchMessages(chatId, query, authToken).getOrElse { emptyList() }
        } catch (e: Exception) { println("Error searching messages: ${e.message}"); emptyList() }
    }
}
