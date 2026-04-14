package compose.project.click.click.data.api

import compose.project.click.click.data.models.*
import compose.project.click.click.qr.CLICK_WEB_BASE_URL
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * API client for chat-related operations with the Flask backend
 */
class ChatApiClient(
    private val baseUrl: String = ApiConfig.BASE_URL,
    private val clickWebBaseUrl: String = CLICK_WEB_BASE_URL.trimEnd('/'),
    private val httpClient: HttpClient? = null
) {
    /**
     * Ktor already emits `form-data; name="<append key>"` for this part — only add `filename=` here.
     * A full `form-data; name=...` string duplicates `name` (Ktor merges header values with `; `) and
     * produces multipart that undici/Node cannot parse (`Failed to parse body as FormData`).
     */
    private fun encryptedUploadFileHeaders(): Headers = Headers.build {
        append(HttpHeaders.ContentDisposition, "filename=\"encrypted_media.bin\"")
        append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
    }

    private val client = httpClient ?: HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            })
        }
    }

    private fun bearerAuthHeader(rawToken: String): String {
        val t = rawToken.trim()
        return if (t.startsWith("Bearer ", ignoreCase = true)) t else "Bearer $t"
    }

    /** Public GET (e.g. Supabase Storage public URL for chat-media). */
    suspend fun downloadUrlBytes(url: String): Result<ByteArray> {
        return try {
            val response = client.get(url)
            if (response.status.value in 200..299) {
                Result.success(response.body<ByteArray>())
            } else {
                Result.failure(Exception("HTTP ${response.status} for media download"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Serializable
    private data class ClickWebMessageDto(
        val id: String,
        @SerialName("chat_id") val chat_id: String,
        @SerialName("user_id") val user_id: String,
        val content: String,
        @SerialName("time_created") val time_created: Long,
        @SerialName("time_edited") val time_edited: Long? = null,
        @SerialName("is_read") val is_read: Boolean = false,
        @SerialName("message_type") val message_type: String = "text",
        val metadata: JsonElement? = null,
    ) {
        fun toMessage(): Message = Message(
            id = id,
            user_id = user_id,
            content = content,
            timeCreated = time_created,
            timeEdited = time_edited,
            isRead = is_read,
            messageType = message_type,
            metadata = metadata,
        )
    }

    @Serializable
    private data class ClickWebMessageEnvelope(val message: ClickWebMessageDto)

    @Serializable
    private data class ClickWebSendMessageBody(
        @SerialName("chat_id") val chat_id: String,
        @SerialName("user_id") val user_id: String,
        val content: String,
        @SerialName("message_type") val message_type: String? = null,
        val metadata: JsonElement? = null,
    )

    @Serializable
    private data class ClickWebPatchMessageBody(
        @SerialName("message_id") val message_id: String,
        @SerialName("chat_id") val chat_id: String,
        val content: String,
    )

    @Serializable
    private data class ClickWebMarkChatReadBody(@SerialName("chat_id") val chat_id: String)

    @Serializable
    private data class ClickWebReactionEnvelope(
        val action: String,
        val reaction: ReactionApiModel? = null,
    )

    @Serializable
    private data class ClickWebReactionPostBody(
        val messageId: String,
        val reactionType: String,
    )

    @Serializable
    private data class ClickWebReactionDeleteBody(
        val messageId: String,
        val reactionType: String,
    )

    @Serializable
    private data class ChatMediaUploadPathResponse(val path: String)

    @Serializable
    private data class ChatMediaUploadUrlResponse(val url: String? = null, val path: String? = null)

    @Serializable
    private data class ChatMediaUploadJsonBody(
        @SerialName("chat_id") val chatId: String,
        @SerialName("mime_type") val mimeType: String,
        @SerialName("file_b64") val fileBase64: String,
    )

    @Serializable
    private data class ClickWebHubMessageEnvelope(val message: HubMessageApiDto)

    /** Row returned from POST /api/hub/messages (matches public.hub_messages). */
    @Serializable
    data class HubMessageApiDto(
        val id: String,
        @SerialName("hub_id") val hubId: String,
        @SerialName("user_id") val userId: String,
        val body: String,
        @SerialName("created_at") val createdAt: String,
        @SerialName("message_type") val messageType: String = "text",
        val metadata: JsonElement? = null,
    )

    @Serializable
    private data class ClickWebHubSendMessageBody(
        @SerialName("hub_id") val hubId: String,
        val body: String,
        @SerialName("user_lat") val userLat: Double,
        @SerialName("user_long") val userLong: Double,
        @SerialName("message_type") val messageType: String? = null,
        val metadata: JsonElement? = null,
    )

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
    data class SendMessageRequest(
        val user_id: String,
        val content: String,
        val message_type: String? = null,
        val metadata: JsonElement? = null,
    )

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

    @Serializable
    data class DisplayNamesRequest(val user_ids: List<String>)

    @Serializable
    data class DisplayNamesResponse(val names: Map<String, String>)

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
        val is_read: Boolean = false,
        val status: String? = null,
        val message_type: String? = null,
        val metadata: JsonElement? = null,
    )

    @Serializable
    data class UserApiModel(
        val id: String,
        val name: String? = null,
        val full_name: String? = null,
        val email: String? = null,
        val image: String? = null
    )

    @Serializable
    data class GeoLocationApi(
        val lat: Double,
        val lon: Double
    )

    @Serializable
    data class ConnectionApiModel(
        val id: String,
        val user_ids: List<String>,
        val geo_location: GeoLocationApi,
        val full_location: Map<String, String>? = null,
        val semantic_location: String? = null,
        @SerialName("connection_encounters")
        val connectionEncounters: List<ConnectionEncounter> = emptyList(),
        val created: Long,
        val expiry: Long,
        val should_continue: List<Boolean> = listOf(false, false),
        val has_begun: Boolean = false
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
     * Get a specific user by ID
     */
    suspend fun getUser(userId: String, authToken: String): Result<User> {
        return try {
            val response = client.get("$baseUrl/api/users/$userId") {
                header("Authorization", authToken)
            }

            if (response.status.value in 200..299) {
                val userApiModel = response.body<UserApiModel>()
                Result.success(userApiModel.toUser())
            } else {
                Result.failure(Exception("Failed to fetch user: ${response.status}"))
            }
        } catch (e: Exception) {
            println("Error fetching user: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Get a specific user by ID
     */
    suspend fun getChat(chatId: String, authToken: String): Result<Chat> {
        return try {
            val response = client.get("$baseUrl/api/chats/$chatId") {
                header("Authorization", authToken)
            }

            if (response.status.value in 200..299) {
                val chatResponse = response.body<ChatResponse>()
                Result.success(Chat(messages = emptyList())) // Chat structure simplified
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
     * Insert an encrypted (or plaintext) message row via [clickWebBaseUrl]/api/chat/messages (gatekeeper).
     */
    suspend fun sendMessage(
        chatId: String,
        userId: String,
        content: String,
        authToken: String,
        messageType: String? = null,
        metadata: JsonElement? = null,
    ): Result<Message> {
        return try {
            val response = client.post("$clickWebBaseUrl/api/chat/messages") {
                headers.append(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                contentType(ContentType.Application.Json)
                setBody(
                    ClickWebSendMessageBody(
                        chat_id = chatId,
                        user_id = userId,
                        content = content,
                        message_type = messageType,
                        metadata = metadata,
                    ),
                )
            }

            if (response.status.value in 200..299) {
                val envelope = response.body<ClickWebMessageEnvelope>()
                Result.success(envelope.message.toMessage())
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
     * Patch message content via Next.js gatekeeper (E2EE ciphertext).
     */
    suspend fun editMessage(
        chatId: String,
        messageId: String,
        userId: String,
        content: String,
        authToken: String,
    ): Result<Message> {
        return try {
            val response = client.patch("$clickWebBaseUrl/api/chat/messages") {
                headers.append(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                contentType(ContentType.Application.Json)
                setBody(
                    ClickWebPatchMessageBody(
                        message_id = messageId,
                        chat_id = chatId,
                        content = content,
                    ),
                )
            }

            if (response.status.value in 200..299) {
                val envelope = response.body<ClickWebMessageEnvelope>()
                Result.success(envelope.message.toMessage())
            } else {
                Result.failure(Exception("Failed to update message: ${response.status}"))
            }
        } catch (e: Exception) {
            println("Error updating message: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * @deprecated Use [editMessage]; retained for call sites that still reference [updateMessage].
     */
    suspend fun updateMessage(
        chatId: String,
        messageId: String,
        userId: String,
        content: String,
        authToken: String
    ): Result<Message> = editMessage(chatId, messageId, userId, content, authToken)

    /**
     * Marks messages from other participants as read for [chat_id] (JWT identifies the reader).
     */
    suspend fun markChatAsRead(chatId: String, authToken: String): Result<Unit> {
        if (chatId.isBlank()) return Result.failure(IllegalArgumentException("chatId is blank"))
        return try {
            val response = client.patch("$clickWebBaseUrl/api/chat/messages/read") {
                headers.append(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                contentType(ContentType.Application.Json)
                setBody(ClickWebMarkChatReadBody(chat_id = chatId))
            }
            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to mark chat as read: ${response.status}"))
            }
        } catch (e: Exception) {
            println("Error marking chat as read: ${e.message}")
            Result.failure(e)
        }
    }

    /** Upload ciphertext bytes to chat-media via gatekeeper; returns the public media URL. */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun uploadMedia(
        fileBytes: ByteArray,
        chatId: String,
        mimeType: String,
        authToken: String,
    ): Result<String> {
        if (fileBytes.isEmpty()) return Result.failure(IllegalArgumentException("Empty media"))
        return try {
            val encoded = Base64.encode(fileBytes)
            val response = client.post("$clickWebBaseUrl/api/chat/media") {
                headers.append(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                contentType(ContentType.Application.Json)
                setBody(
                    ChatMediaUploadJsonBody(
                        chatId = chatId,
                        mimeType = mimeType.ifBlank { "application/octet-stream" },
                        fileBase64 = encoded,
                    ),
                )
            }
            if (response.status.value in 200..299) {
                val payload = response.body<ChatMediaUploadUrlResponse>()
                val url = payload.url?.trim().orEmpty()
                if (url.isNotEmpty()) {
                    Result.success(url)
                } else {
                    Result.failure(Exception("Upload response missing media url"))
                }
            } else {
                Result.failure(Exception("Failed to upload media: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Insert a hub message via Next.js gatekeeper (JWT + geofence). Realtime still delivers rows to clients.
     */
    suspend fun sendHubMessage(
        hubId: String,
        body: String,
        userLat: Double,
        userLong: Double,
        authToken: String,
        messageType: String? = null,
        metadata: JsonElement? = null,
    ): Result<HubMessageApiDto> {
        return try {
            val response = client.post("$clickWebBaseUrl/api/hub/messages") {
                headers.append(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                contentType(ContentType.Application.Json)
                setBody(
                    ClickWebHubSendMessageBody(
                        hubId = hubId,
                        body = body,
                        userLat = userLat,
                        userLong = userLong,
                        messageType = messageType,
                        metadata = metadata,
                    ),
                )
            }
            if (response.status.value in 200..299) {
                Result.success(response.body<ClickWebHubMessageEnvelope>().message)
            } else {
                Result.failure(Exception("Failed to send hub message: ${response.status}"))
            }
        } catch (e: Exception) {
            println("Error sending hub message: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Upload hub ciphertext to chat-media; [objectPath] must be `{userId}/hub/{hubId}/...`.
     * Same rule as [uploadMedia]: never set request-level `multipart/form-data` without boundary.
     */
    suspend fun uploadHubMedia(
        fileBytes: ByteArray,
        hubId: String,
        mimeType: String,
        objectPath: String,
        authToken: String,
        userLat: Double,
        userLong: Double,
    ): Result<String> {
        if (fileBytes.isEmpty()) return Result.failure(IllegalArgumentException("Empty media"))
        return try {
            val response = client.post("$clickWebBaseUrl/api/hub/media") {
                headers.append(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("hub_id", hubId)
                            append("object_path", objectPath)
                            append("mime_type", mimeType.ifBlank { "application/octet-stream" })
                            append("user_lat", userLat.toString())
                            append("user_long", userLong.toString())
                            append("file", fileBytes, encryptedUploadFileHeaders())
                        },
                    ),
                )
            }
            if (response.status.value in 200..299) {
                Result.success(response.body<ChatMediaUploadPathResponse>().path)
            } else {
                Result.failure(Exception("Failed to upload hub media: ${response.status}"))
            }
        } catch (e: Exception) {
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
                Result.success(Chat(messages = emptyList())) // Chat structure simplified
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
     * Add a reaction via Next.js gatekeeper.
     */
    suspend fun sendReaction(messageId: String, userId: String, reactionType: String, authToken: String): Result<MessageReaction> {
        return try {
            val response = client.post("$clickWebBaseUrl/api/chat/reactions") {
                headers.append(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                contentType(ContentType.Application.Json)
                setBody(ClickWebReactionPostBody(messageId = messageId, reactionType = reactionType))
            }
            if (response.status.value in 200..299) {
                val env = response.body<ClickWebReactionEnvelope>()
                val row = env.reaction
                if (row != null) {
                    Result.success(row.toReaction())
                } else if (env.action == "exists") {
                    val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                    Result.success(
                        MessageReaction(
                            id = "dup-$messageId-$reactionType-$now",
                            messageId = messageId,
                            userId = userId,
                            reactionType = reactionType,
                            createdAt = now,
                        ),
                    )
                } else {
                    Result.failure(Exception("Reaction insert returned no row"))
                }
            } else {
                Result.failure(Exception("Failed to add reaction: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** @deprecated Use [sendReaction]. */
    suspend fun addReaction(messageId: String, userId: String, reactionType: String, authToken: String): Result<MessageReaction> =
        sendReaction(messageId, userId, reactionType, authToken)

    /**
     * Remove the caller's reaction via Next.js gatekeeper.
     */
    suspend fun removeReaction(messageId: String, userId: String, reactionType: String, authToken: String): Result<Boolean> {
        return try {
            val response = client.delete("$clickWebBaseUrl/api/chat/reactions") {
                headers.append(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                contentType(ContentType.Application.Json)
                setBody(ClickWebReactionDeleteBody(messageId, reactionType))
            }
            Result.success(response.status.value in 200..299)
        } catch (e: Exception) {
            Result.failure(e)
        }
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

    /**
     * Resolve display names for a batch of user IDs.
     * Uses backend service credentials so it still works when users table is protected by RLS.
     */
    suspend fun getDisplayNames(userIds: List<String>, authToken: String): Result<Map<String, String>> {
        return try {
            if (userIds.isEmpty()) return Result.success(emptyMap())

            val response = client.post("$baseUrl/api/users/display-names") {
                header("Authorization", authToken)
                contentType(ContentType.Application.Json)
                setBody(DisplayNamesRequest(user_ids = userIds))
            }

            if (response.status.value in 200..299) {
                val body = response.body<DisplayNamesResponse>()
                Result.success(body.names)
            } else {
                Result.failure(Exception("Failed to fetch display names: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Extension functions to convert API models to domain models
    private fun ChatApiModel.toChatWithDetails(): ChatWithDetails {
        return ChatWithDetails(
            chat = Chat(
                id = id,
                connectionId = connection_id,
                messages = emptyList()
            ),
            connection = connection?.toConnection() ?: Connection(
                id = connection_id,
                user_ids = emptyList(),
                geo_location = compose.project.click.click.data.models.GeoLocation(0.0, 0.0),
                full_location = null,
                semantic_location = null,
                connectionEncounters = emptyList(),
                created = created_at,
                expiry = created_at + 86400000,
                should_continue = listOf(false, false),
                has_begun = false
            ),
            otherUser = other_user?.toUser() ?: User(
                id = "",
                name = "Unknown",
                email = "",
                image = null,
                createdAt = 0,
                lastPolled = null,
                connections = emptyList(),
                paired_with = emptyList(),
                connection_today = -1,
                last_paired = null
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
            user_id = user_id,
            content = content,
            timeCreated = created_at,
            timeEdited = updated_at,
            isRead = is_read,
            messageType = message_type ?: "text",
            metadata = metadata,
        )
    }

    private fun UserApiModel.toUser(): User {
        val resolvedName = resolveDisplayName(
            firstName = null,
            lastName = null,
            fullName = full_name,
            name = name,
            email = email
        )

        return User(
            id = id,
            name = resolvedName,
            email = email,
            image = image,
            createdAt = 0,
            lastPolled = null,
            firstName = null,
            lastName = null,
            birthday = null,
            connections = emptyList(),
            paired_with = emptyList(),
            connection_today = -1,
            last_paired = null
        )
    }

    private fun ConnectionApiModel.toConnection(): Connection {
        return Connection(
            id = id,
            user_ids = user_ids,
            geo_location = compose.project.click.click.data.models.GeoLocation(
                lat = geo_location.lat,
                lon = geo_location.lon
            ),
            full_location = full_location,
            semantic_location = semantic_location,
            connectionEncounters = connectionEncounters,
            created = created,
            expiry = expiry,
            should_continue = should_continue,
            has_begun = has_begun
        )
    }

    @Serializable
    data class ReactionResponse(val reaction: ReactionApiModel)
}
