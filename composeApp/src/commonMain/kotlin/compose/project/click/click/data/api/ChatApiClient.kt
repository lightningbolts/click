@file:Suppress(
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.data.api // pragma: allowlist secret

import compose.project.click.click.data.models.* // pragma: allowlist secret
import compose.project.click.click.crypto.MessageCryptoV2
import compose.project.click.click.data.repository.AuthRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.qr.CLICK_WEB_BASE_URL // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * API client for chat-related operations via the Next.js companion (`click-web`).
 */
class ChatApiClient(
    internal val clickWebBaseUrl: String = CLICK_WEB_BASE_URL.trimEnd('/'),
    internal val tokenStorage: TokenStorage = createTokenStorage(),
    internal val httpClient: HttpClient? = null,
) {
    /**
     * Ktor already emits `form-data; name="<append key>"` for this part — only add `filename=` here.
     * A full `form-data; name=...` string duplicates `name` (Ktor merges header values with `; `) and
     * produces multipart that undici/Node cannot parse (`Failed to parse body as FormData`).
     */
    internal fun encryptedUploadFileHeaders(): Headers =
        Headers.build {
            append(HttpHeaders.ContentDisposition, "filename=\"encrypted_media.bin\"")
            append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
        }

    internal val client =
        httpClient ?: HttpClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        prettyPrint = true
                    },
                )
            }
            installClickWebBearerAuth(tokenStorage)
            installClickWeb403RetryInterceptor(tokenStorage)
        }

    internal fun bearerAuthHeader(rawToken: String): String {
        val t = rawToken.trim()
        return if (t.startsWith("Bearer ", ignoreCase = true)) t else "Bearer $t"
    }

    /** Public GET (e.g. Supabase Storage public URL for chat-media). */
    suspend fun downloadUrlBytes(url: String): Result<ByteArray> =
        try {
            val response = client.get(url)
            if (response.status.value in 200..299) {
                Result.success(response.body<ByteArray>())
            } else {
                Result.failure(Exception("HTTP ${response.status} for media download"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    @Serializable
    data class HubDetailsDto(
        val id: String,
        val name: String,
        val category: String = "general",
        @SerialName("creator_id") val creatorId: String? = null,
        @SerialName("event_beacon_id") val eventBeaconId: String? = null,
        @SerialName("expires_at") val expiresAt: String? = null,
    )

    /** Canonical outcome of a successful encrypted attachment upload. */
    data class UploadedAttachment(
        val path: String,
        /** Short-lived signed URL. May be null if the server couldn't mint one. */
        val initialSignedUrl: String?,
    )

    /** Media upload result retaining the canonical private storage path for future re-signing. */
    data class UploadedMedia(
        val url: String,
        val path: String?,
    )

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
    data class ConversationSearchHitDto(
        val messageId: String,
        val chatId: String,
        val conversationId: String,
        val connectionId: String,
        val senderId: String,
        val timestamp: Long = 0L,
        val snippet: String = "",
        val chatName: String = "",
        val isHub: Boolean = false,
        val hubId: String? = null,
        val hubRealtimeChannel: String? = null,
    )

    @Serializable
    data class HubThreadResponse(
        val messages: List<HubMessageApiDto> = emptyList(),
        @SerialName("participant_ids") val participantIds: List<String> = emptyList(),
        @SerialName("occupant_count") val occupantCount: Int = 1,
        val channel: String? = null,
        val error: String? = null,
    )

    // Response wrapper classes
    @Serializable
    data class ChatsResponse(
        val chats: List<ChatApiModel>,
    )

    @Serializable
    data class ChatResponse(
        val chat: ChatApiModel,
    )

    @Serializable
    data class MessagesResponse(
        val messages: List<MessageApiModel>,
    )

    @Serializable
    data class MessageResponse(
        val message: MessageApiModel,
    )

    @Serializable
    data class ParticipantsResponse(
        val participants: List<UserApiModel>,
    )

    @Serializable
    data class SendMessageRequest(
        val user_id: String,
        val content: String,
        val message_type: String? = null,
        val metadata: JsonElement? = null,
    )

    @Serializable
    data class MarkReadRequest(
        val user_id: String,
    )

    @Serializable
    data class UpdateMessageRequest(
        val user_id: String,
        val content: String,
    )

    @Serializable
    data class DeleteMessageRequest(
        val user_id: String,
    )

    @Serializable
    data class ReactionsResponse(
        val reactions: List<ReactionApiModel>,
    )

    @Serializable
    data class ReactionApiModel(
        val id: String,
        val message_id: String,
        val user_id: String,
        val reaction_type: String,
        val created_at: Long,
    )

    @Serializable
    data class AddReactionRequest(
        val user_id: String,
        val reaction_type: String,
    )

    @Serializable
    data class RemoveReactionRequest(
        val user_id: String,
        val reaction_type: String,
    )

    @Serializable
    data class TypingRequest(
        val user_id: String,
    )

    @Serializable
    data class StatusUpdateRequest(
        val status: String,
    )

    @Serializable
    data class ForwardMessageRequest(
        val target_chat_id: String,
        val user_id: String,
    )

    @Serializable
    data class SearchMessagesResponse(
        val messages: List<MessageApiModel>,
    )

    @Serializable
    data class DisplayNamesRequest(
        val user_ids: List<String>,
    )

    @Serializable
    data class DisplayNamesResponse(
        val names: Map<String, String>,
    )

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
        val unread_count: Int = 0,
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
        val image: String? = null,
    )

    @Serializable
    data class GeoLocationApi(
        val lat: Double,
        val lon: Double,
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
        val has_begun: Boolean = false,
    )

    /**
     * Get all chats for a user with details.
     * Legacy Flask path — superseded by Supabase / click-web inbox; kept as an explicit failure.
     */
    suspend fun getUserChats(
        userId: String,
        authToken: String,
    ): Result<List<ChatWithDetails>> = Result.failure(Exception("getUserChats is no longer served; use Supabase chat list"))

    /**
     * Get a specific user by ID.
     * Legacy Flask path — use click-web `GET /api/users/{id}/profile` via [ApiClient] instead.
     */
    suspend fun getUser(
        userId: String,
        authToken: String,
    ): Result<User> = Result.failure(Exception("getUser is no longer served; use ApiClient.getUserProfile"))

    /**
     * Get a specific chat by ID.
     * Legacy Flask path — superseded by Supabase chats table.
     */
    suspend fun getChat(
        chatId: String,
        authToken: String,
    ): Result<Chat> = Result.failure(Exception("getChat is no longer served; use Supabase chats"))

    /**
     * Get all messages for a specific chat.
     * Legacy Flask path — use click-web `GET /api/chat/messages` or Supabase Realtime.
     */
    suspend fun getChatMessages(
        chatId: String,
        authToken: String,
    ): Result<List<Message>> = Result.failure(Exception("getChatMessages is no longer served; use click-web /api/chat/messages"))

    /** Register the public half of the locally persisted v2 device identity. */
    internal suspend fun registerE2eeV2Device(
        deviceId: String,
        identityPublicKey: String,
        authToken: String,
    ): Result<ClickWebChatDeviceDto> =
        try {
            require(deviceId.isNotBlank() && identityPublicKey.isNotBlank()) { "device identity is required" }
            val response =
                client.post("$clickWebBaseUrl/api/chat/devices") {
                    header(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                    contentType(ContentType.Application.Json)
                    setBody(ClickWebRegisterChatDeviceBody(deviceId, identityPublicKey))
                }
            if (response.status.value in 200..299) {
                Result.success(response.body<ClickWebChatDeviceEnvelope>().device)
            } else {
                Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    /** Discover active v2 device public identities for a persisted chat. */
    internal suspend fun discoverE2eeV2Devices(
        chatId: String,
        authToken: String,
    ): Result<List<ClickWebChatDeviceDto>> =
        try {
            require(chatId.isNotBlank()) { "chatId is required" }
            val response =
                client.get("$clickWebBaseUrl/api/chat/devices") {
                    header(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                    parameter("chat_id", chatId)
                }
            if (response.status.value in 200..299) {
                Result.success(response.body<ClickWebChatDevicesEnvelope>().devices.filter {
                    it.keyAlgorithm == "X25519" && it.cryptoVersion == MessageCryptoV2.CRYPTO_VERSION
                })
            } else {
                Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    /** Retrieve v2 epoch envelopes for a persisted chat and registered device row. */
    internal suspend fun getE2eeV2State(
        chatId: String,
        authToken: String,
        deviceId: String,
    ): Result<ClickWebChatE2eeV2StateEnvelope> =
        try {
            require(chatId.isNotBlank() && deviceId.isNotBlank()) { "chatId and deviceId are required" }
            val response =
                client.get("$clickWebBaseUrl/api/chat/epochs") {
                    header(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                    parameter("chat_id", chatId)
                    parameter("device_id", deviceId)
                }
            if (response.status.value in 200..299) {
                Result.success(response.body<ClickWebChatE2eeV2StateEnvelope>())
            } else {
                Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    /** Atomically initialize or rotate the server-side epoch and its complete recipient set. */
    internal suspend fun createE2eeV2Epoch(
        chatId: String,
        epoch: Int,
        senderDeviceId: String,
        membershipFingerprint: String,
        envelopes: List<ClickWebChatEpochWriteEnvelope>,
        authToken: String,
    ): Result<Unit> =
        try {
            require(chatId.isNotBlank() && senderDeviceId.isNotBlank() && membershipFingerprint.isNotBlank()) {
                "chat epoch metadata is required"
            }
            require(epoch > 0 && envelopes.isNotEmpty()) { "chat epoch payload is invalid" }
            val response =
                client.post("$clickWebBaseUrl/api/chat/epochs") {
                    header(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                    contentType(ContentType.Application.Json)
                    setBody(
                        ClickWebChatEpochWriteBody(
                            chatId = chatId,
                            epoch = epoch,
                            senderDeviceId = senderDeviceId,
                            membershipFingerprint = membershipFingerprint,
                            envelopes = envelopes,
                        ),
                    )
                }
            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    /** Discover active v2 device identities for the members of an event/community hub. */
    internal suspend fun discoverHubE2eeV2Devices(
        hubId: String,
        authToken: String,
    ): Result<List<ClickWebChatDeviceDto>> =
        try {
            require(hubId.isNotBlank()) { "hubId is required" }
            val response =
                client.get("$clickWebBaseUrl/api/hub/devices") {
                    header(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                    parameter("hub_id", hubId)
                }
            if (response.status.value in 200..299) {
                Result.success(response.body<ClickWebChatDevicesEnvelope>().devices.filter {
                    it.keyAlgorithm == "X25519" && it.cryptoVersion == MessageCryptoV2.CRYPTO_VERSION
                })
            } else {
                Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    /** Retrieve the current hub epoch and this device's opaque key envelopes. */
    internal suspend fun getHubE2eeV2State(
        hubId: String,
        authToken: String,
        deviceId: String,
    ): Result<ClickWebHubE2eeV2StateEnvelope> =
        try {
            require(hubId.isNotBlank() && deviceId.isNotBlank()) { "hubId and deviceId are required" }
            val response =
                client.get("$clickWebBaseUrl/api/hub/epochs") {
                    header(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                    parameter("hub_id", hubId)
                    parameter("device_id", deviceId)
                }
            if (response.status.value in 200..299) {
                Result.success(response.body<ClickWebHubE2eeV2StateEnvelope>())
            } else {
                Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    /** Atomically initialize or rotate the hub epoch and its complete recipient set. */
    internal suspend fun createHubE2eeV2Epoch(
        hubId: String,
        epoch: Int,
        senderDeviceId: String,
        membershipFingerprint: String,
        envelopes: List<ClickWebHubEpochWriteEnvelope>,
        authToken: String,
    ): Result<Unit> =
        try {
            require(hubId.isNotBlank() && senderDeviceId.isNotBlank() && membershipFingerprint.isNotBlank()) {
                "hub epoch metadata is required"
            }
            require(epoch > 0 && envelopes.isNotEmpty()) { "hub epoch payload is invalid" }
            val response =
                client.post("$clickWebBaseUrl/api/hub/epochs") {
                    header(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                    contentType(ContentType.Application.Json)
                    setBody(
                        ClickWebHubEpochWriteBody(
                            hubId = hubId,
                            epoch = epoch,
                            senderDeviceId = senderDeviceId,
                            membershipFingerprint = membershipFingerprint,
                            envelopes = envelopes,
                        ),
                    )
                }
            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
        localSentAtMs: Long? = null,
        connectionId: String? = null,
    ): Result<Message> {
        return try {
            suspend fun postOnce(bearer: String): Result<Message> {
                val response =
                    client.post("$clickWebBaseUrl/api/chat/messages") {
                        header(HttpHeaders.Authorization, clickWebBearerHeader(bearer))
                        contentType(ContentType.Application.Json)
                        setBody(
                            ClickWebSendMessageBody(
                                chat_id =
                                    chatId.takeIf {
                                        compose.project.click.click.util // pragma: allowlist secret
                                            .isPersistedApiChatId(it)
                                    },
                                connection_id = connectionId?.takeIf { it.isNotBlank() },
                                user_id = userId,
                                content = content,
                                message_type = messageType,
                                metadata = metadata,
                                local_sent_at = localSentAtMs,
                            ),
                        )
                    }
                return if (response.status.value in 200..299) {
                    val envelope = response.body<ClickWebMessageEnvelope>()
                    Result.success(envelope.message.toMessage())
                } else {
                    Result.failure(Exception(readClickWebErrorMessage(response)))
                }
            }

            val token =
                resolveClickWebAccessToken(tokenStorage)
                    ?: authToken.trim().takeIf { it.isNotEmpty() }
            if (token.isNullOrBlank()) {
                return Result.failure(Exception("Session expired. Sign in again."))
            }
            val first = postOnce(token)
            if (first.isFailure) {
                val msg = first.exceptionOrNull()?.redactedRestMessage()?.lowercase()
                if (msg?.contains("unauthorized") == true || msg?.contains("401") == true) {
                    AuthRepository(tokenStorage).refreshSession(forceRefresh = true)
                    val retry = resolveClickWebAccessToken(tokenStorage, forceRefresh = true)
                    if (!retry.isNullOrBlank()) {
                        return postOnce(retry)
                    }
                }
            }
            return first
        } catch (e: Exception) {
            println("Error sending message: ${e.redactedRestMessage()}")
            Result.failure(e)
        }
    }

    internal suspend fun readClickWebErrorMessage(response: HttpResponse): String {
        val status = response.status.value
        val fromJson =
            runCatching { response.body<ErrorResponse>() }
                .getOrNull()
                ?.error
                ?.trim()
                .orEmpty()
        if (fromJson.isNotEmpty()) return fromJson.take(200)
        val raw = runCatching { response.bodyAsText() }.getOrNull()?.trim().orEmpty()
        if (raw.contains("<!DOCTYPE", ignoreCase = true) || raw.contains("<html", ignoreCase = true)) {
            return when (status) {
                401, 403 -> "Session expired. Sign in again."
                in 500..599 -> "Server error ($status). Try again later."
                else -> "Request failed ($status)."
            }
        }
        return raw.take(200).ifEmpty { "Failed to send message: $status" }
    }

    /**
     * Legacy Flask mark-read — superseded by [markChatAsRead] (click-web).
     */
    suspend fun markMessagesAsRead(
        chatId: String,
        userId: String,
        authToken: String,
    ): Result<Boolean> = Result.failure(Exception("markMessagesAsRead is no longer served; use markChatAsRead"))

    /**
     * Patch message content via Next.js gatekeeper (E2EE ciphertext).
     */
    suspend fun editMessage(
        chatId: String,
        messageId: String,
        userId: String,
        content: String,
        authToken: String,
    ): Result<Message> =
        try {
            val response =
                client.patch("$clickWebBaseUrl/api/chat/messages") {
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
            println("Error updating message: ${e.redactedRestMessage()}")
            Result.failure(e)
        }

    /**
     * @deprecated Use [editMessage]; retained for call sites that still reference [updateMessage].
     */
    suspend fun updateMessage(
        chatId: String,
        messageId: String,
        userId: String,
        content: String,
        authToken: String,
    ): Result<Message> = editMessage(chatId, messageId, userId, content, authToken)

    /**
     * Marks messages from other participants as read for [chat_id] (JWT identifies the reader).
     */
    suspend fun markChatAsRead(
        chatId: String,
        authToken: String,
    ): Result<Unit> {
        if (chatId.isBlank()) return Result.failure(IllegalArgumentException("chatId is blank"))
        return try {
            val response =
                client.patch("$clickWebBaseUrl/api/chat/messages/read") {
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
            println("Error marking chat as read: ${e.redactedRestMessage()}")
            Result.failure(e)
        }
    }

    /**
     * Marks the latest peer-authored message in [chatId] as unread (JWT identifies the reader).
     */
    suspend fun markChatAsUnread(
        chatId: String,
        authToken: String,
    ): Result<Unit> {
        if (chatId.isBlank()) return Result.failure(IllegalArgumentException("chatId is blank"))
        return try {
            val response =
                client.patch("$clickWebBaseUrl/api/chat/messages/unread") {
                    headers.append(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                    contentType(ContentType.Application.Json)
                    setBody(ClickWebMarkChatUnreadBody(chat_id = chatId))
                }
            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to mark chat as unread: ${response.status}"))
            }
        } catch (e: Exception) {
            println("Error marking chat as unread: ${e.redactedRestMessage()}")
            Result.failure(e)
        }
    }

    /**
     * Recipient ack: marks peer-authored [message_ids] in [chatId] with [delivered_at] (gatekeeper).
     */
    suspend fun markMessagesDelivered(
        chatId: String,
        messageIds: List<String>,
        authToken: String,
    ): Result<Unit> {
        if (chatId.isBlank() || messageIds.isEmpty()) {
            return Result.failure(IllegalArgumentException("chatId and messageIds required"))
        }
        return try {
            val chunks = messageIds.distinct().chunked(100)
            for (chunk in chunks) {
                val response =
                    client.patch("$clickWebBaseUrl/api/chat/messages/delivered") {
                        headers.append(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                        contentType(ContentType.Application.Json)
                        setBody(ClickWebMarkDeliveredBody(chat_id = chatId, message_ids = chunk))
                    }
                if (response.status.value !in 200..299) {
                    return Result.failure(Exception("markMessagesDelivered failed: ${response.status}"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            println("Error marking messages delivered: ${e.redactedRestMessage()}")
            Result.failure(e)
        }
    }

    suspend fun uploadMedia(
        fileBytes: ByteArray,
        chatId: String,
        mimeType: String,
        authToken: String,
        v2: E2eeV2MediaUploadRequest? = null,
    ): Result<String> =
        uploadMediaImpl(fileBytes = fileBytes, chatId = chatId, mimeType = mimeType, authToken = authToken, v2 = v2)
            .map { it.url }

    suspend fun uploadMediaWithPath(
        fileBytes: ByteArray,
        chatId: String,
        mimeType: String,
        authToken: String,
        v2: E2eeV2MediaUploadRequest? = null,
    ): Result<UploadedMedia> =
        uploadMediaImpl(fileBytes = fileBytes, chatId = chatId, mimeType = mimeType, authToken = authToken, v2 = v2)

    suspend fun uploadAttachment(
        fileBytes: ByteArray,
        chatId: String,
        mimeType: String,
        fileName: String,
        authToken: String,
        v2: E2eeV2MediaUploadRequest? = null,
    ): Result<UploadedAttachment> =
        uploadAttachmentImpl(fileBytes = fileBytes, chatId = chatId, mimeType = mimeType, fileName = fileName, authToken = authToken, v2 = v2)

    suspend fun signAttachmentUrl(
        path: String,
        authToken: String,
    ): Result<String> = signAttachmentUrlImpl(path = path, authToken = authToken)

    suspend fun downloadAttachmentBytes(signedUrl: String): Result<ByteArray> = downloadAttachmentBytesImpl(signedUrl = signedUrl)

    suspend fun sendHubMessage(
        hubId: String,
        body: String,
        userLat: Double,
        userLong: Double,
        authToken: String,
        messageType: String? = null,
        metadata: JsonElement? = null,
    ): Result<HubMessageApiDto> =
        sendHubMessageImpl(
            hubId = hubId,
            body = body,
            userLat = userLat,
            userLong = userLong,
            authToken = authToken,
            messageType = messageType,
            metadata = metadata,
        )

    suspend fun uploadHubMedia(
        fileBytes: ByteArray,
        hubId: String,
        mimeType: String,
        objectPath: String,
        authToken: String,
        userLat: Double,
        userLong: Double,
        v2: E2eeV2MediaUploadRequest? = null,
    ): Result<String> =
        uploadHubMediaImpl(
            fileBytes = fileBytes,
            hubId = hubId,
            mimeType = mimeType,
            objectPath = objectPath,
            authToken = authToken,
            userLat = userLat,
            userLong = userLong,
            v2 = v2,
        )

    /** Gets a short-lived, authorized URL for a private hub-media object. */
    suspend fun resolveHubMediaUrl(
        hubId: String,
        path: String,
        authToken: String,
    ): Result<String> = resolveHubMediaUrlImpl(hubId = hubId, path = path, authToken = authToken)

    suspend fun addCliqueMember(
        groupId: String,
        newMemberUserId: String,
        authToken: String,
    ): Result<Unit> = addCliqueMemberImpl(groupId = groupId, newMemberUserId = newMemberUserId, authToken = authToken)

    suspend fun removeCliqueMember(
        groupId: String,
        memberUserId: String,
        authToken: String,
    ): Result<Unit> = removeCliqueMemberImpl(groupId = groupId, memberUserId = memberUserId, authToken = authToken)

    suspend fun updateHub(
        hubId: String,
        name: String?,
        category: String?,
        authToken: String,
    ): Result<Unit> = updateHubImpl(hubId = hubId, name = name, category = category, authToken = authToken)

    suspend fun getHubDetails(
        hubId: String,
        authToken: String,
    ): Result<HubDetailsDto> = getHubDetailsImpl(hubId = hubId, authToken = authToken)

    suspend fun deleteHub(
        hubId: String,
        authToken: String,
    ): Result<Unit> = deleteHubImpl(hubId = hubId, authToken = authToken)

    suspend fun leaveHub(
        hubId: String,
        authToken: String,
    ): Result<Unit> = leaveHubImpl(hubId = hubId, authToken = authToken)

    /**
     * Delete a message via click-web gatekeeper (`DELETE /api/chat/messages?messageId=`).
     */
    suspend fun deleteMessage(
        chatId: String,
        messageId: String,
        userId: String,
        authToken: String,
    ): Result<Boolean> {
        return try {
            suspend fun deleteOnce(bearer: String): Result<Boolean> {
                val response =
                    client.delete("$clickWebBaseUrl/api/chat/messages") {
                        header(HttpHeaders.Authorization, clickWebBearerHeader(bearer))
                        parameter("messageId", messageId)
                    }
                return if (response.status.value in 200..299) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(readClickWebErrorMessage(response)))
                }
            }

            val token =
                resolveClickWebAccessToken(tokenStorage)
                    ?: authToken.trim().takeIf { it.isNotEmpty() }
            if (token.isNullOrBlank()) {
                return Result.failure(Exception("Session expired. Sign in again."))
            }
            val first = deleteOnce(token)
            if (first.isFailure) {
                val msg = first.exceptionOrNull()?.redactedRestMessage()?.lowercase()
                if (msg?.contains("unauthorized") == true || msg?.contains("401") == true) {
                    AuthRepository(tokenStorage).refreshSession(forceRefresh = true)
                    val retry = resolveClickWebAccessToken(tokenStorage, forceRefresh = true)
                    if (!retry.isNullOrBlank()) {
                        return deleteOnce(retry)
                    }
                }
            }
            first
        } catch (e: Exception) {
            println("Error deleting message: ${e.redactedRestMessage()}")
            Result.failure(e)
        }
    }

    /**
     * Get chat for a connection — legacy Flask; use Supabase `chats` by connection_id.
     */
    suspend fun getChatForConnection(
        connectionId: String,
        authToken: String,
    ): Result<Chat> = Result.failure(Exception("getChatForConnection is no longer served; use Supabase chats"))

    /**
     * Get participants in a chat — legacy Flask; use Supabase connection/group members.
     */
    suspend fun getChatParticipants(
        chatId: String,
        authToken: String,
    ): Result<List<User>> = Result.failure(Exception("getChatParticipants is no longer served; use Supabase members"))

    /**
     * Get reactions for a message — legacy Flask; reactions arrive with message payloads / Realtime.
     */
    suspend fun getMessageReactions(
        messageId: String,
        authToken: String,
    ): Result<List<MessageReaction>> = Result.failure(Exception("getMessageReactions is no longer served; use message payload reactions"))

    /**
     * Add a reaction via Next.js gatekeeper.
     */
    suspend fun sendReaction(
        messageId: String,
        userId: String,
        reactionType: String,
        authToken: String,
    ): Result<MessageReaction> =
        try {
            val response =
                client.post("$clickWebBaseUrl/api/chat/reactions") {
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
                    val now =
                        kotlinx.datetime.Clock.System
                            .now()
                            .toEpochMilliseconds()
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

    /** @deprecated Use [sendReaction]. */
    suspend fun addReaction(
        messageId: String,
        userId: String,
        reactionType: String,
        authToken: String,
    ): Result<MessageReaction> = sendReaction(messageId, userId, reactionType, authToken)

    /**
     * Remove the caller's reaction via Next.js gatekeeper.
     */
    suspend fun removeReaction(
        messageId: String,
        userId: String,
        reactionType: String,
        authToken: String,
    ): Result<Boolean> =
        try {
            val response =
                client.delete("$clickWebBaseUrl/api/chat/reactions") {
                    headers.append(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                    contentType(ContentType.Application.Json)
                    setBody(ClickWebReactionDeleteBody(messageId, reactionType))
                }
            Result.success(response.status.value in 200..299)
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * Set typing status — legacy Flask; typing is local / Realtime only now.
     */
    suspend fun setTyping(
        chatId: String,
        userId: String,
        authToken: String,
    ): Result<Boolean> = Result.failure(Exception("setTyping is no longer served"))

    /**
     * Get list of users currently typing — legacy Flask.
     */
    suspend fun getTypingUsers(
        chatId: String,
        authToken: String,
    ): Result<List<String>> = Result.failure(Exception("getTypingUsers is no longer served"))

    /**
     * Update message delivery/read status — legacy Flask; use click-web delivered/read patches.
     */
    suspend fun updateMessageStatus(
        messageId: String,
        status: String,
        authToken: String,
    ): Result<Boolean> = Result.failure(Exception("updateMessageStatus is no longer served; use markMessagesDelivered / markChatAsRead"))

    /**
     * Forward a message — not yet implemented on click-web; do not call a dead Flask host.
     */
    suspend fun forwardMessage(
        messageId: String,
        targetChatId: String,
        userId: String,
        authToken: String,
    ): Result<Message> = Result.failure(Exception("forwardMessage is not available on click-web yet"))

    suspend fun searchConversations(
        query: String,
        authToken: String,
    ): Result<List<ConversationSearchHitDto>> = searchConversationsImpl(query = query, authToken = authToken)

    suspend fun fetchHubThread(
        hubId: String,
        authToken: String,
        aroundMessageId: String? = null,
        limit: Int = 120,
    ): Result<HubThreadResponse> =
        fetchHubThreadImpl(hubId = hubId, authToken = authToken, aroundMessageId = aroundMessageId, limit = limit)

    /**
     * Search messages — use [searchConversations] (`GET /api/chat/search`) or local/repository scan.
     */
    suspend fun searchMessages(
        chatId: String,
        query: String,
        authToken: String,
    ): Result<List<Message>> = Result.failure(Exception("searchMessages is no longer served; use GET /api/chat/search"))

    /**
     * Resolve display names — legacy Flask; use profile BFF / Supabase users.
     */
    suspend fun getDisplayNames(
        userIds: List<String>,
        authToken: String,
    ): Result<Map<String, String>> = Result.failure(Exception("getDisplayNames is no longer served; use profile BFF"))

    // Extension functions to convert API models to domain models
    internal fun ChatApiModel.toChatWithDetails(): ChatWithDetails =
        ChatWithDetails(
            chat =
                Chat(
                    id = id,
                    connectionId = connection_id,
                    messages = emptyList(),
                ),
            connection =
                connection?.toConnection() ?: Connection(
                    id = connection_id,
                    user_ids = emptyList(),
                    geo_location =
                        compose.project.click.click.data.models // pragma: allowlist secret
                            .GeoLocation(0.0, 0.0),
                    full_location = null,
                    semantic_location = null,
                    connectionEncounters = emptyList(),
                    created = created_at,
                    expiry = created_at + 86400000,
                    should_continue = listOf(false, false),
                    has_begun = false,
                ),
            otherUser =
                other_user?.toUser() ?: User(
                    id = "",
                    name = "Unknown",
                    email = "",
                    image = null,
                    createdAt = 0,
                    lastPolled = null,
                    connections = emptyList(),
                    paired_with = emptyList(),
                    connection_today = -1,
                    last_paired = null,
                ),
            lastMessage = last_message?.toMessage(),
            unreadCount = unread_count,
        )

    internal fun ReactionApiModel.toReaction(): MessageReaction =
        MessageReaction(
            id = id,
            messageId = message_id,
            userId = user_id,
            reactionType = reaction_type,
            createdAt = created_at,
        )

    internal fun MessageApiModel.toMessage(): Message =
        Message(
            id = id,
            user_id = user_id,
            content = content,
            timeCreated = created_at,
            timeEdited = updated_at,
            isRead = is_read,
            messageType = message_type ?: "text",
            metadata = metadata,
        )

    internal fun UserApiModel.toUser(): User {
        val resolvedName =
            resolveDisplayName(
                firstName = null,
                lastName = null,
                fullName = full_name,
                name = name,
                email = email,
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
            last_paired = null,
        )
    }

    internal fun ConnectionApiModel.toConnection(): Connection =
        Connection(
            id = id,
            user_ids = user_ids,
            geo_location =
                compose.project.click.click.data.models.GeoLocation( // pragma: allowlist secret
                    lat = geo_location.lat,
                    lon = geo_location.lon,
                ),
            full_location = full_location,
            semantic_location = semantic_location,
            connectionEncounters = connectionEncounters,
            created = created,
            expiry = expiry,
            should_continue = should_continue,
            has_begun = has_begun,
        )

    @Serializable
    data class ReactionResponse(
        val reaction: ReactionApiModel,
    )
}
