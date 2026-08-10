package compose.project.click.click.util

private val PERSISTED_CHAT_UUID = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
)

/**
 * True for a server `chats.id` UUID. Rejects optimistic message ids (`temp-…`) and other
 * client-local placeholders that must never be sent as `chat_id`.
 */
fun isPersistedApiChatId(id: String?): Boolean {
    val trimmed = id?.trim().orEmpty()
    if (trimmed.isEmpty()) return false
    if (trimmed.startsWith("temp-", ignoreCase = true)) return false
    if (trimmed.startsWith("temp_", ignoreCase = true)) return false
    if (trimmed.startsWith("client-opt:", ignoreCase = true)) return false
    if (trimmed.startsWith("optimistic:", ignoreCase = true)) return false
    return PERSISTED_CHAT_UUID.matches(trimmed)
}
