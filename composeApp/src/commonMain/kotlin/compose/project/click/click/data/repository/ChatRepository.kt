package compose.project.click.click.data.repository

import compose.project.click.click.data.models.Chat
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.MessageReaction
import compose.project.click.click.data.models.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

/** Handle for attaching/detaching a Supabase realtime messages channel (testable without [RealtimeChannel]). */
interface ChatMessageSubscription {
    suspend fun attach()
    suspend fun detach()
}

/**
 * Coarse health signal for the shared-presence Realtime channel. Surfaced to the UI
 * as a lightweight "everyone looks offline" indicator without leaking transport errors.
 */
enum class PresenceHealth {
    /** No active presence subscription (signed out, paused, or never started). */
    Idle,

    /** `startGlobalPresence` is in flight; presence set may be empty but is not stale. */
    Connecting,

    /** Presence channel is subscribed and receiving joins/leaves. */
    Online,

    /**
     * Either the initial `subscribe()` failed, or the presence collector crashed after
     * being healthy. `_onlineUsers` is best-effort stale; UI may dim online dots.
     */
    Degraded,
}

/**
 * Abstraction for chat data and realtime subscriptions (implemented by [SupabaseChatRepository]).
 */
interface ChatRepository {
    /** User IDs currently present on the shared Realtime channel `room:presence`. */
    val onlineUsers: StateFlow<Set<String>>

    /** Health of the shared presence subscription; see [PresenceHealth]. */
    val presenceHealth: StateFlow<PresenceHealth>

    suspend fun startGlobalPresence(userId: String)

    suspend fun stopGlobalPresence()

    suspend fun cacheEncryptionKeys(chatId: String, connectionId: String, userIds: List<String>)

    /** Caches the 32-byte group master key for [chatId] after local unwrap or creation. */
    suspend fun cacheGroupMasterKey(chatId: String, masterKey: ByteArray)

    /**
     * Clears all in-memory crypto state and disposes any active ephemeral
     * channel / presence sessions for the current signed-in user. Call on
     * sign-out BEFORE [compose.project.click.click.data.storage.TokenStorage.clearSessionData]
     * so no derived keys or group masters leak across accounts on the same device.
     */
    suspend fun clearSessionCaches()

    suspend fun fetchUserChatsWithDetails(userId: String): List<ChatWithDetails>

    /** Direct 1:1 chats only (excludes group clique chats). */
    suspend fun fetchDirectUserChatsWithDetails(userId: String): List<ChatWithDetails>

    /** Group clique chats only. */
    suspend fun fetchGroupUserChatsWithDetails(userId: String): List<ChatWithDetails>

    suspend fun fetchArchivedUserChatsWithDetails(userId: String): List<ChatWithDetails>

    /**
     * Loads all messages for [chatId].
     * @return `null` if the request failed (network/RLS/decoding); empty list means the chat has no rows.
     */
    suspend fun fetchMessagesForChat(chatId: String, viewerUserId: String? = null): List<Message>?

    suspend fun sendMessage(
        chatId: String,
        userId: String,
        content: String,
        messageType: String = "text",
        metadata: JsonElement? = null,
    ): Message?

    suspend fun ensureChatForConnection(connectionId: String): Chat?

    suspend fun sendMessageForConnection(
        connectionId: String,
        userId: String,
        content: String,
        messageType: String = "text",
        metadata: JsonElement? = null,
    ): Message?

    suspend fun markMessagesAsRead(chatId: String, userId: String)

    /**
     * @param viewerUserId Required to unwrap group master keys from the database when not already cached.
     */
    suspend fun subscribeToMessages(chatId: String, viewerUserId: String): Pair<ChatMessageSubscription, Flow<ChatRealtimeEvent>>

    /**
     * Creates a verified clique server-side; returns the new **group** id on success.
     * Caller must supply [encryptedKeysByUserId] keyed by each member's user id (wire ciphertext strings).
     */
    suspend fun createVerifiedClique(
        memberUserIds: List<String>,
        encryptedKeysByUserId: Map<String, String>,
        initialGroupName: String = "Clique",
    ): Result<String>

    suspend fun leaveClique(groupId: String): Result<Unit>

    suspend fun deleteClique(groupId: String): Result<Unit>

    suspend fun renameClique(groupId: String, newName: String): Result<Unit>

    /**
     * True when every pair in [memberUserIds] (including caller) has an active/kept 1:1 connection.
     * Caller must appear in the list; uses server RPC so friend–friend edges are visible.
     */
    suspend fun verifiedCliqueEdgesExist(memberUserIds: List<String>): Boolean

    /** Clears short-lived junction caches so the next chat list fetch hits the network. */
    fun clearChatListLocalCaches()

    /**
     * Realtime INSERT on [messages] rows the current session may read. Emits [MessageListInsertEvent]
     * with [MessageListInsertEvent.connectionId] resolved from [chats].
     */
    suspend fun subscribeToMessageInserts(): Pair<ChatMessageSubscription, Flow<MessageListInsertEvent>>

    suspend fun fetchChatById(chatId: String): Chat?

    suspend fun fetchChatWithDetails(chatId: String, currentUserId: String): ChatWithDetails?

    suspend fun fetchChatParticipants(chatId: String): List<User>

    suspend fun getUserById(userId: String): User?

    suspend fun updateMessage(chatId: String, messageId: String, userId: String, content: String): Message?

    suspend fun deleteMessage(chatId: String, messageId: String, userId: String): Boolean

    suspend fun fetchReactionsForChat(chatId: String): List<MessageReaction>

    suspend fun addReaction(messageId: String, userId: String, reactionType: String): Boolean

    suspend fun removeReaction(messageId: String, userId: String, reactionType: String): Boolean

    suspend fun sendTypingStatus(chatId: String, userId: String, isTyping: Boolean)

    fun observeTypingStatus(chatId: String): Flow<TypingStatus>

    suspend fun getTypingUsers(chatId: String): List<String>

    /**
     * Joins the Realtime channel `chat:{chatId}` for Broadcast (typing) and Presence (peer online).
     * Idempotent for the same [chatId]; replaces any prior ephemeral session for another chat.
     */
    suspend fun joinChatEphemeralChannel(chatId: String, currentUserId: String, peerUserId: String)

    suspend fun leaveChatEphemeralChannel(chatId: String)

    /** Emits whether [peerUserId] is currently present on the chat channel (active in this chat). */
    fun observePeerOnline(chatId: String, peerUserId: String): Flow<Boolean>

    suspend fun updateMessageStatus(messageId: String, status: String): Boolean

    suspend fun forwardMessage(messageId: String, targetChatId: String, userId: String): Message?

    suspend fun searchMessages(chatId: String, query: String): List<Message>

    suspend fun resolveChatIdForConnection(connectionId: String): String?

    suspend fun resolveChatIdForGroupId(groupId: String): String?

    suspend fun searchMessagesByConnectionId(connectionId: String, query: String): Pair<String?, List<Message>>

    /** Uploads raw bytes to Supabase Storage (`chat-media` bucket) and returns a public URL, or null on failure. */
    suspend fun uploadChatMedia(bytes: ByteArray, objectPath: String, contentType: String): String?

    /** Downloads ciphertext from [mediaUrl] and decrypts for [chatId] as [viewerUserId]. */
    suspend fun downloadAndDecryptChatMedia(chatId: String, viewerUserId: String, mediaUrl: String): ByteArray?

    /**
     * Outcome of an encrypted attachment upload. The sender embeds [fileMasterKeyBase64] +
     * [sha256Base64] inside the `ccx:v1:` envelope, which is then E2EE-wrapped into the message
     * body. [path] is the canonical Storage object path (signed URLs minted on demand for readers).
     */
    data class EncryptedAttachmentUpload(
        val path: String,
        val fileMasterKeyBase64: String,
        val sha256Base64: String,
        val sizeBytes: Long,
        val mimeType: String,
        val fileName: String,
    )

    /**
     * Unified encrypted blob upload entry-point (Phase 2 — C4).
     *
     * Picks the target bucket based on [bucketName]:
     *   - `CHAT_MEDIA_BUCKET`       — images / voice (keyed via the chat's E2EE keys).
     *   - `CHAT_ATTACHMENTS_BUCKET` — arbitrary files with a fresh per-file AES-256 master key
     *                                 and a `ccx:v1:` envelope ready for embedding in a message.
     *
     * Returns `null` on validation failure, auth failure, or Storage rejection. Never throws;
     * caller is expected to surface a user-friendly error.
     */
    suspend fun uploadEncryptedBlob(
        bucketName: String,
        chatId: String,
        senderUserId: String,
        plainBytes: ByteArray,
        mimeType: String,
        fileName: String,
    ): EncryptedAttachmentUpload?
}
