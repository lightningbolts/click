package compose.project.click.click.viewmodel // pragma: allowlist secret

import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.api.ChatApiClient // pragma: allowlist secret
import compose.project.click.click.data.auth.EnsureFreshAccessToken // pragma: allowlist secret
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
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
    @SerialName("message_type") val messageType: String = ChatMessageType.TEXT,
    val metadata: JsonElement? = null,
)

/** Extract the `id` column out of a realtime `oldRecord` JsonObject (DELETE payloads carry PKs only). */
internal fun JsonObject.hubMessageRowId(): String? = (this["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

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
    val stored = getJwt()?.trim().orEmpty()
    if (stored.isNotEmpty()) return stored
    throw IllegalStateException("Please sign in again.")
}
