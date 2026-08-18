package compose.project.click.click.data.repository // pragma: allowlist secret

import compose.project.click.click.data.api.ChatApiClient // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import kotlinx.coroutines.CancellationException

/**
 * Maps click-web `GET /api/chat/search` hits into [ConversationSearchHit].
 * Default [ChatRepository.searchConversationHits] uses this so production search
 * hits click-web without a [SupabaseChatRepository] override.
 */
internal suspend fun ChatRepository.fetchConversationSearchHits(
    query: String,
    apiClient: ChatApiClient = ChatApiClient(),
): List<ConversationSearchHit> {
    val q = query.trim()
    if (q.length < 2) return emptyList()
    return try {
        val token = ensureFreshAuthToken() ?: return emptyList()
        apiClient
            .searchConversations(q, token)
            .getOrElse { err ->
                println("ChatRepository: conversation search failed: ${err.redactedRestMessage()}")
                return emptyList()
            }.map { dto ->
                ConversationSearchHit(
                    messageId = dto.messageId,
                    chatId = dto.chatId,
                    conversationId = dto.conversationId,
                    connectionId = dto.connectionId,
                    senderId = dto.senderId,
                    timestamp = dto.timestamp,
                    snippet = dto.snippet,
                    chatName = dto.chatName,
                    isHub = dto.isHub,
                    hubId = dto.hubId,
                    hubRealtimeChannel = dto.hubRealtimeChannel,
                )
            }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("ChatRepository: searchConversationHits failed: ${e.redactedRestMessage()}")
        emptyList()
    }
}
