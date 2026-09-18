package compose.project.click.click.viewmodel // pragma: allowlist secret

import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.api.ChatApiClient // pragma: allowlist secret
import compose.project.click.click.data.auth.EnsureFreshAccessToken // pragma: allowlist secret
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.MessageReaction // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.random.Random

internal const val HUB_CHAT_DRAFT_MAX_LENGTH = 1000

@Serializable
internal data class HubDetailsRow(
    val name: String? = null,
    val category: String? = null,
    @SerialName("creator_id") val creatorId: String,
    @SerialName("event_beacon_id") val eventBeaconId: String? = null,
)

data class HubDetailsState(
    val name: String,
    val category: String,
    val isCreator: Boolean,
)

sealed interface HubChatNavigationEvent {
    data object PopBackToConnections : HubChatNavigationEvent
}

sealed interface HubRealtimeState {
    data object Loading : HubRealtimeState

    data object Ready : HubRealtimeState

    data class Error(
        val message: String,
    ) : HubRealtimeState
}

interface HubLifecycleGateway {
    suspend fun updateHub(
        hubId: String,
        name: String,
        category: String,
        authToken: String,
    ): Result<Unit>

    suspend fun deleteHub(
        hubId: String,
        authToken: String,
    ): Result<Unit>

    suspend fun leaveHub(
        hubId: String,
        authToken: String,
    ): Result<Unit>
}

internal class ChatApiHubLifecycleGateway(
    private val chatApi: ChatApiClient,
) : HubLifecycleGateway {
    override suspend fun updateHub(
        hubId: String,
        name: String,
        category: String,
        authToken: String,
    ): Result<Unit> =
        chatApi.updateHub(
            hubId = hubId,
            name = name,
            category = category,
            authToken = authToken,
        )

    override suspend fun deleteHub(
        hubId: String,
        authToken: String,
    ): Result<Unit> = chatApi.deleteHub(hubId = hubId, authToken = authToken)

    override suspend fun leaveHub(
        hubId: String,
        authToken: String,
    ): Result<Unit> = chatApi.leaveHub(hubId = hubId, authToken = authToken)
}

interface ActiveHubCache {
    fun removeActiveHub(hubId: String)
}

internal object AppDataManagerActiveHubCache : ActiveHubCache {
    override fun removeActiveHub(hubId: String) {
        AppDataManager.removeActiveHub(hubId)
    }
}

@Serializable
internal data class HubMessageRow(
    val id: String,
    @SerialName("hub_id") val hubId: String,
    @SerialName("user_id") val userId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("edited_at") val editedAt: String? = null,
    @SerialName("message_type") val messageType: String = ChatMessageType.TEXT,
    val metadata: JsonElement? = null,
)

@Serializable
internal data class HubReactionRow(
    val id: String,
    @SerialName("hub_message_id") val messageId: String,
    @SerialName("hub_id") val hubId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("reaction_type") val reactionType: String,
    @SerialName("created_at") val createdAt: String,
)

/** Extract the `id` column out of a realtime `oldRecord` JsonObject (DELETE payloads carry PKs only). */
internal fun JsonObject.hubMessageRowId(): String? = (this["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.hubReactionRowId(): String? = (this["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.hubReactionMessageId(): String? =
    (this["hub_message_id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.hubReactionHubId(): String? = (this["hub_id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.hubReactionUserId(): String? = (this["user_id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.hubReactionType(): String? =
    (this["reaction_type"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

internal data class HubReactionMutationKey(
    val messageId: String,
    val reactionType: String,
)

internal data class HubReactionMutationState(
    var desiredEnabled: Boolean,
    var acknowledgedEnabled: Boolean,
    var generation: Long = 0L,
    var canonicalReaction: MessageReaction? = null,
    var workerRunning: Boolean = false,
) {
    fun toggleIntent() {
        desiredEnabled = !desiredEnabled
        generation += 1L
    }

    fun observeAcknowledged(
        enabled: Boolean,
        canonical: MessageReaction? = null,
    ): Boolean {
        acknowledgedEnabled = enabled
        canonicalReaction = if (enabled) canonical ?: canonicalReaction else null
        return desiredEnabled == acknowledgedEnabled
    }
}

internal sealed interface HubReactionRealtimeEvent {
    data class Upsert(
        val reaction: MessageReaction,
    ) : HubReactionRealtimeEvent

    data class Delete(
        val reactionId: String,
        val messageId: String? = null,
        val userId: String? = null,
        val reactionType: String? = null,
    ) : HubReactionRealtimeEvent
}

internal data class PendingHubMessageDelete(
    val removed: MessageWithUser,
    val index: Int,
    val latestRealtime: MessageWithUser? = null,
)

internal fun PendingHubMessageDelete.rollbackMessage(): MessageWithUser = latestRealtime ?: removed

internal fun applyHubReactionRealtimeEvent(
    current: Map<String, List<MessageReaction>>,
    event: HubReactionRealtimeEvent,
): Map<String, List<MessageReaction>> {
    val next = current.toMutableMap()
    when (event) {
        is HubReactionRealtimeEvent.Upsert -> {
            val reaction = event.reaction
            val rows =
                next[reaction.messageId]
                    .orEmpty()
                    .filterNot {
                        it.id == reaction.id ||
                            (it.userId == reaction.userId && it.reactionType == reaction.reactionType)
                    } + reaction
            next[reaction.messageId] = rows
        }
        is HubReactionRealtimeEvent.Delete -> {
            val messageId =
                event.messageId
                    ?: next.entries
                        .firstOrNull { (_, rows) ->
                            rows.any { it.id == event.reactionId }
                        }?.key
                    ?: return current
            val rows = next[messageId].orEmpty().filterNot { it.id == event.reactionId }
            if (rows.isEmpty()) next.remove(messageId) else next[messageId] = rows
        }
    }
    return next
}

internal fun hubReactionDeleteEvent(oldRecord: JsonObject): HubReactionRealtimeEvent.Delete? {
    val reactionId = oldRecord.hubReactionRowId() ?: return null
    return HubReactionRealtimeEvent.Delete(
        reactionId = reactionId,
        messageId = oldRecord.hubReactionMessageId(),
        userId = oldRecord.hubReactionUserId(),
        reactionType = oldRecord.hubReactionType(),
    )
}

internal fun rollbackHubReactionMutation(
    current: List<MessageReaction>,
    optimisticId: String?,
    removedExisting: MessageReaction?,
    restoreRemovedExisting: Boolean = true,
): List<MessageReaction> =
    when {
        removedExisting != null &&
            restoreRemovedExisting &&
            current.none {
                it.userId == removedExisting.userId &&
                    it.reactionType == removedExisting.reactionType
            } -> current + removedExisting
        removedExisting != null -> current
        optimisticId != null -> current.filterNot { it.id == optimisticId }
        else -> current
    }

internal fun replayHubReactionEvents(
    snapshot: List<MessageReaction>,
    events: List<HubReactionRealtimeEvent>,
): Map<String, List<MessageReaction>> =
    events.fold(snapshot.groupBy { it.messageId }) { state, event ->
        applyHubReactionRealtimeEvent(state, event)
    }

internal fun hubCreatedAtToEpoch(iso: String): Long {
    val t = iso.trim().replace(" ", "T")
    return runCatching { Instant.parse(t) }.getOrNull()?.toEpochMilliseconds()
        ?: Clock.System.now().toEpochMilliseconds()
}

internal fun randomHubMediaLeaf(): String =
    buildString(20) {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        repeat(20) { append(alphabet[Random.nextInt(alphabet.length)]) }
    }

internal const val HUB_INITIAL_MESSAGE_LIMIT = 120L

internal suspend fun TokenStorage.requireFreshHubJwt(forceRefresh: Boolean = false): String {
    val fresh =
        runCatching { EnsureFreshAccessToken.get(this, forceRefresh = forceRefresh) }
            .getOrNull()
            ?.trim()
            .orEmpty()
    if (fresh.isNotEmpty()) return fresh
    throw IllegalStateException("Please sign in again.")
}
