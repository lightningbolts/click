@file:Suppress(
    "ktlint:standard:backing-property-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.viewmodel

import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.decodeRecordOrNull
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
internal data class ConnectionRealtimeRow(
    val id: String,
    @SerialName("user_ids") val userIds: List<String>? = null,
)

internal fun JsonObject.stringField(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

/**
 * Realtime payloads for [PostgresAction.Update] are often partial; [decodeRecordOrNull] plus raw
 * [JsonObject] fields avoid missing refreshes when only [Connection.last_message_at] changes.
 */
@Serializable
internal data class ConnectionJunctionRealtimeRow(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("connection_id") val connectionId: String? = null,
)

internal fun connectionRowRelevantToUser(
    action: PostgresAction,
    userId: String,
): Boolean {
    val knownIds =
        AppDataManager.connections.value
            .map { it.id }
            .toSet()
    return when (action) {
        is PostgresAction.Insert -> {
            val row = action.decodeRecordOrNull<ConnectionRealtimeRow>()
            row?.userIds?.contains(userId) == true
        }
        is PostgresAction.Update -> {
            val row = action.decodeRecordOrNull<ConnectionRealtimeRow>()
            val id = row?.id ?: action.record.stringField("id")
            row?.userIds?.contains(userId) == true || (id != null && id in knownIds)
        }
        is PostgresAction.Delete -> {
            val id = action.oldRecord.stringField("id") ?: return false
            id in knownIds
        }
        else -> false
    }
}

private sealed class ConnectionsRealtimeEvent {
    data class MainTable(
        val action: PostgresAction,
    ) : ConnectionsRealtimeEvent()

    data class ArchiveJunction(
        val action: PostgresAction,
    ) : ConnectionsRealtimeEvent()

    data class HiddenJunction(
        val action: PostgresAction,
    ) : ConnectionsRealtimeEvent()
}

sealed class ChatListState {
    data object Loading : ChatListState()

    data class Success(
        val chats: List<ChatWithDetails>,
    ) : ChatListState()

    data class Error(
        val message: String,
    ) : ChatListState()
}

sealed class ChatMessagesState {
    data object Loading : ChatMessagesState()

    data class Success(
        val messages: List<MessageWithUser>,
        val chatDetails: ChatWithDetails,
        val isLoadingMessages: Boolean = false,
    ) : ChatMessagesState()

    data class Error(
        val message: String,
    ) : ChatMessagesState()
}

internal data class CombinedInboxState(
    val chats: List<ChatWithDetails>,
    val directLoaded: Boolean,
    val groupLoaded: Boolean,
)

const val CHAT_STAGED_MEDIA_MAX = 10

data class StagedChatImage(
    val id: String,
    val bytes: ByteArray,
    val mimeType: String,
)

internal fun formatAddCliqueMemberError(raw: String?): String {
    val trimmed = raw?.trim().orEmpty()
    val message =
        runCatching {
            kotlinx.serialization.json
                .Json { ignoreUnknownKeys = true }
                .parseToJsonElement(trimmed)
                .let { it as? JsonObject }
                ?.get("error")
                ?.let { el -> (el as? JsonPrimitive)?.contentOrNull }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: trimmed

    return when {
        message.isBlank() -> "Could not add member"
        message.contains("verified connection", ignoreCase = true) ||
            message.contains("missing verified", ignoreCase = true) ->
            "They need a verified connection with every member of this click"
        message.contains("already a member", ignoreCase = true) ->
            "They're already in this click"
        message.contains("must be a group member", ignoreCase = true) ->
            "You must be in this click to add someone"
        message.contains("encryption", ignoreCase = true) ->
            "Couldn't access click encryption — try reopening the chat"
        message.contains("not authenticated", ignoreCase = true) ->
            "Sign in again to add members"
        else -> message.take(160)
    }
}

internal const val CHAT_MESSAGE_INPUT_MAX_LENGTH = 1000
internal const val MESSAGE_SUBSCRIPTION_MAX_ATTEMPTS = 3
internal const val MESSAGE_SUBSCRIPTION_RETRY_DELAY_MS = 750L

// Polling is a degraded-mode safety net behind the merged message+reaction Realtime
// channel, not the primary delivery path. 800ms refetched the full thread ~75x/min
// per open chat; 12s keeps reaction recovery snappy without hammering the API.
internal const val ACTIVE_CHAT_SYNC_INTERVAL_MS = 12_000L
internal const val CONNECTIONS_PAGE_SIZE = 50
internal const val CONNECTIONS_LIST_DEBOUNCE_MS = 450L
internal const val APP_DATA_STARTUP_WAIT_MS = 20_000L
internal const val CHAT_THREAD_CACHE_FRESH_MS = 120_000L
internal const val CHAT_OPEN_PREFETCH_CONCURRENCY = 4
internal const val INITIAL_CHAT_MESSAGE_FETCH_LIMIT = 80
internal const val OLDER_MESSAGES_PAGE_SIZE = 40
internal const val TARGET_MESSAGE_MAX_PAGES = 16
internal const val SECURE_CHAT_IMAGE_NETWORK_CONCURRENCY = 4
internal const val SECURE_CHAT_DISK_HYDRATE_VISIBLE_BATCH = 12
