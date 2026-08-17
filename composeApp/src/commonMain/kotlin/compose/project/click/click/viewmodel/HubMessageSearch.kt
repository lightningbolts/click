package compose.project.click.click.viewmodel // pragma: allowlist secret

import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

private const val HUB_MESSAGE_SEARCH_LIMIT = 80L

@Serializable
private data class HubSearchMessageRow(
    val id: String,
    @SerialName("hub_id") val hubId: String,
    @SerialName("user_id") val userId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("message_type") val messageType: String = ChatMessageType.TEXT,
    val metadata: JsonElement? = null,
)

internal suspend fun searchHubMessagesByQuery(
    hubId: String,
    query: String,
): List<Message> {
    val needle = query.trim()
    if (hubId.isBlank() || needle.length < 2) return emptyList()
    return try {
        val rows =
            SupabaseConfig.client
                .from("hub_messages")
                .select {
                    filter { eq("hub_id", hubId) }
                    order("created_at", Order.DESCENDING)
                    limit(HUB_MESSAGE_SEARCH_LIMIT)
                }.decodeList<HubSearchMessageRow>()
        rows
            .filter { it.body.contains(needle, ignoreCase = true) }
            .map { row ->
                Message(
                    id = row.id,
                    user_id = row.userId,
                    content = row.body,
                    timeCreated = hubSearchCreatedAtToEpoch(row.createdAt),
                    messageType = row.messageType,
                    metadata = row.metadata,
                )
            }
    } catch (e: Exception) {
        println("HubMessageSearch: $hubId failed: ${e.redactedRestMessage()}")
        emptyList()
    }
}

private fun hubSearchCreatedAtToEpoch(iso: String): Long {
    val t = iso.trim().replace(" ", "T")
    return runCatching { Instant.parse(t) }.getOrNull()?.toEpochMilliseconds()
        ?: Clock.System.now().toEpochMilliseconds()
}
