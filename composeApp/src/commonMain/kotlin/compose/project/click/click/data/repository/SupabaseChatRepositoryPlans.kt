package compose.project.click.click.data.repository

import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.UpcomingPlan
import compose.project.click.click.data.models.upcomingPlansFrom
import compose.project.click.click.util.isPersistedApiChatId
import compose.project.click.click.util.redactedRestMessage
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator

/**
 * Upcoming plans for a chat, read straight from `messages.metadata.plan` (plaintext by the shared
 * wire format, so no decryption is needed) plus the ✅ reactions for going counts. Android has no
 * on-device message store, so this replaces iOS reading `LocalStore`.
 */
internal suspend fun SupabaseChatRepository.fetchUpcomingPlansImpl(
    chatId: String,
    nowEpochMs: Long,
): List<UpcomingPlan> {
    if (!isPersistedApiChatId(chatId) || ensureFreshJwtForChat().isNullOrBlank()) return emptyList()
    return try {
        val rows =
            supabase
                .from("messages")
                .select {
                    filter {
                        eq("chat_id", chatId)
                        filterNot("metadata->plan", FilterOperator.IS, "null")
                    }
                    order("time_created", Order.DESCENDING)
                    limit(100)
                }.decodeList<Message>()
        val candidates = upcomingPlansFrom(chatId, rows, emptyMap(), nowEpochMs)
        if (candidates.isEmpty()) return emptyList()
        val reactions = fetchReactionsForChat(chatId, candidates.map { it.messageId }).groupBy { it.messageId }
        upcomingPlansFrom(chatId, rows, reactions, nowEpochMs)
    } catch (e: Exception) {
        println("ChatRepository: upcoming plans failed: ${e.redactedRestMessage()}")
        emptyList()
    }
}
