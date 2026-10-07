package compose.project.click.click.data.chat // pragma: allowlist secret

import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret

/** A decrypted message row kept on this device (05 §D6). */
data class StoredChatMessage(
    val id: String,
    /** Server chat id. */
    val chatId: String,
    /** The key the app opens the chat by (connection id, or group id). */
    val threadKey: String,
    val senderId: String,
    val text: String,
    val createdMs: Long,
    val messageType: String,
)

/**
 * Per-user on-device message history with full-text search (iOS `LocalStore`): decrypted text of
 * messages the app has already shown, so search works offline and beyond the loaded window. One
 * database per signed-in user in no-backup storage; never leaves the device, never logged, and
 * wiped at sign-out.
 */
expect object LocalMessageStore {
    suspend fun upsert(
        userId: String,
        messages: List<StoredChatMessage>,
    )

    suspend fun remove(
        userId: String,
        messageIds: Collection<String>,
    )

    /** Newest-first matches for every word in [query] (prefix match), across all chats. */
    suspend fun search(
        userId: String,
        query: String,
        limit: Int = 60,
    ): List<StoredChatMessage>

    /** Newest [limit] rows of one chat, oldest first (for painting a chat before the network answers). */
    suspend fun latest(
        userId: String,
        chatId: String,
        limit: Int,
    ): List<StoredChatMessage>

    /** Sign-out: deletes every user's database on this device. */
    suspend fun wipeAll()
}

/** What gets indexed: readable text only (never wire ciphertext or attachment envelopes). */
fun storableText(message: Message): String? {
    val type = message.messageType.ifBlank { ChatMessageType.TEXT }.lowercase()
    val text = message.content.trim()
    if (text.isEmpty()) return null
    if (text.startsWith("e2e") || text.startsWith("ccx:")) return null
    return when (type) {
        ChatMessageType.TEXT -> text
        // Photo / voice captions are searchable; bare placeholders (" ") were dropped above.
        ChatMessageType.IMAGE, ChatMessageType.AUDIO -> text
        else -> null
    }
}

fun Message.toStored(
    chatId: String,
    threadKey: String,
): StoredChatMessage? {
    if (id.startsWith("temp-")) return null
    val text = storableText(this) ?: return null
    return StoredChatMessage(
        id = id,
        chatId = chatId,
        threadKey = threadKey,
        senderId = user_id,
        text = text,
        createdMs = timeCreated,
        messageType = messageType.ifBlank { ChatMessageType.TEXT },
    )
}

/** FTS query for a user's words: each becomes a quoted prefix term (`"cof"*`), quotes stripped. */
fun ftsQuery(raw: String): String? {
    val words =
        raw
            .lowercase()
            .split(Regex("""\s+"""))
            .map { word -> word.filter { it.isLetterOrDigit() || it == '\'' || it == '-' } }
            .filter { it.isNotEmpty() }
    if (words.isEmpty()) return null
    return words.joinToString(" ") { "\"${it.replace("\"", "")}\"*" }
}
