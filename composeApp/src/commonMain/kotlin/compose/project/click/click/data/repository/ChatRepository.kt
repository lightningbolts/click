package compose.project.click.click.data.repository

import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.api.ChatApiClient
import compose.project.click.click.data.models.*
import compose.project.click.click.data.storage.TokenStorage
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.decodeRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
            result.getOrElse {
                println("Error fetching messages: ${it.message}")
                emptyList()
            }
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
                .firstOrNull { it.chat.id == chatId }
        } catch (e: Exception) {
            println("Error fetching chat with details: ${e.message}")
            null
        }
    }

    // Get user by ID - helper method for getting user details
    suspend fun getUserById(userId: String): User? {
        return try {
            val authToken = tokenStorage.getJwt() ?: return null
            val result = apiClient.getChatParticipants("", authToken) // This needs improvement
            result.getOrElse { emptyList() }
                .firstOrNull { it.id == userId }
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
}

