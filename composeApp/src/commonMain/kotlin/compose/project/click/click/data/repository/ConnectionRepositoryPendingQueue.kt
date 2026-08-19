package compose.project.click.click.data.repository

import compose.project.click.click.collaboration.CollaborationSession
import compose.project.click.click.data.api.ClickWebRequestException
import compose.project.click.click.data.api.CollaborationSessionPostResponse
import compose.project.click.click.data.api.ProximityBindOkResponseDto
import compose.project.click.click.data.api.ProximityHandshakePostBody
import compose.project.click.click.data.api.ProximityHandshakePostResult
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.OpenMeteoWeatherService
import compose.project.click.click.data.WeatherService
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.calibrateBarometricElevationMeters
import compose.project.click.click.data.models.Chat
import compose.project.click.click.data.models.ConnectionInsert
import compose.project.click.click.data.models.ConnectionRequest
import compose.project.click.click.data.models.ContextTag
import compose.project.click.click.data.models.PendingConnectionDraft
import compose.project.click.click.data.models.PendingHandshake
import compose.project.click.click.data.models.ProximityHandshakeLocationSnapshot
import compose.project.click.click.data.models.newPendingHandshakeId
import compose.project.click.click.data.models.GeoLocation
import compose.project.click.click.data.models.HeightCategory
import compose.project.click.click.data.models.ConnectionEncounter
import compose.project.click.click.data.ContextTagTaxonomy
import compose.project.click.click.data.models.WeatherSnapshot
import compose.project.click.click.data.models.NoiseLevelCategory
import compose.project.click.click.data.models.newPendingConnectionId
import compose.project.click.click.data.models.PollPairSuggestion
import compose.project.click.click.data.models.ReconnectHelper
import compose.project.click.click.data.models.ConnectionActivityStatus
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.User
import compose.project.click.click.data.models.mergeRichestEncounterEvents
import compose.project.click.click.qr.CLICK_WEB_BASE_URL
import compose.project.click.click.sensors.HardwareVibeSnapshot
import compose.project.click.click.encounter.PendingEncounterQueue
import compose.project.click.click.proximity.PROXIMITY_NO_NEARBY_DEVICES_MESSAGE
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.data.storage.createTokenStorage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import compose.project.click.click.util.redactedRestMessage
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.*

internal suspend fun ConnectionRepository.syncPendingConnectionsImpl(): Int {
    val queue = loadPendingConnectionQueue()
    if (queue.isEmpty()) {
        AppDataManager.setPendingConnectionsCount(0)
        return 0
    }

    val remaining = mutableListOf<PendingConnectionDraft>()
    var syncedCount = 0
    var needsRefresh = false

    queue.forEach { draft ->
        val result = runCatching {
            withTimeout(ConnectionRepository.CONNECTION_TIMEOUT_MS) {
                createConnectionOnline(draft.request)
            }
        }.getOrElse { Result.failure(it) }

        if (result.isSuccess) {
            val connection = result.getOrNull()?.connection ?: return@forEach
            val otherUser = getUserById(draft.request.userId2).getOrNull()
            AppDataManager.replaceLocalConnection(draft.localId, connection, otherUser)
            syncedCount++
            needsRefresh = true
        } else {
            val error = result.exceptionOrNull()
            if (shouldDropQueuedDraft(error)) {
                AppDataManager.removeConnection(draft.localId)
                needsRefresh = true
            } else {
                remaining += draft
            }
        }
    }

    savePendingConnectionQueue(remaining)
    AppDataManager.setPendingConnectionsCount(remaining.size)

    if (needsRefresh) {
        AppDataManager.refresh(force = true)
    }

    return syncedCount
}

internal suspend fun ConnectionRepository.queuePendingConnection(request: ConnectionRequest): Connection {
    val queue = loadPendingConnectionQueue().toMutableList()
    val existing = queue.firstOrNull {
        it.request.userId1 == request.userId1 &&
            it.request.userId2 == request.userId2 &&
            it.request.connectionMethod == request.connectionMethod
    }
    if (existing != null) {
        AppDataManager.setPendingConnectionsCount(queue.size)
        return existing.toPlaceholderConnection(AppDataManager.locationPreferences.value.includeInInsightsEnabled)
    }

    val normalizedRequest = request.copy(
        qrToken = null,
        tokenAgeMs = null,
        skipQrTokenRedeem = false,
        preflightConnectionId = null,
        preflightEncounterLogged = null,
    )
    val draft = PendingConnectionDraft(
        localId = newPendingConnectionId(),
        request = normalizedRequest,
        queuedAt = Clock.System.now().toEpochMilliseconds()
    )
    queue += draft
    savePendingConnectionQueue(queue)
    AppDataManager.setPendingConnectionsCount(queue.size)
    return draft.toPlaceholderConnection(AppDataManager.locationPreferences.value.includeInInsightsEnabled)
}

internal suspend fun ConnectionRepository.loadPendingConnectionQueue(): List<PendingConnectionDraft> {
    val queueJson = tokenStorage.getPendingConnectionQueue()
    if (queueJson.isNullOrBlank()) return emptyList()

    return runCatching {
        json.decodeFromString<List<PendingConnectionDraft>>(queueJson)
    }.getOrElse {
        println("ConnectionRepository: Failed to decode pending queue: ${it.message}")
        emptyList()
    }
}

internal suspend fun ConnectionRepository.savePendingConnectionQueue(queue: List<PendingConnectionDraft>) {
    val serialized = if (queue.isEmpty()) null else json.encodeToString(queue)
    tokenStorage.savePendingConnectionQueue(serialized)
}

internal fun ConnectionRepository.shouldQueueOffline(request: ConnectionRequest, error: Throwable?): Boolean {
    if (error == null) return false

    val message = error.message?.lowercase().orEmpty()
    val isRetryableNetworkFailure = message.contains("timeout") ||
        message.contains("timed out") ||
        message.contains("network") ||
        message.contains("socket") ||
        message.contains("unable to resolve host") ||
        message.contains("failed to connect") ||
        message.contains("offline") ||
        message.contains("unreachable")

    if (!isRetryableNetworkFailure) return false

    return request.connectionMethod == "nfc" ||
        request.connectionMethod == "proximity" ||
        request.connectionMethod == "qr"
}

internal fun ConnectionRepository.shouldDropQueuedDraft(error: Throwable?): Boolean {
    val message = error?.message?.lowercase().orEmpty()
    return message.contains("already exists") ||
        message.contains("cannot connect with yourself") ||
        message.contains("invalid qr code") ||
        message.contains("already used") ||
        message.contains("expired") ||
        message.contains("same physical location")
}
