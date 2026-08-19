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

/**
 * Cold-start prewarm: POST an intentionally invalid body so JWT/auth paths execute but no
 * handshake row is inserted (`my_token` fails validation). Errors are ignored.
 */
internal suspend fun ConnectionRepository.prewarmBindProximityConnectionImpl(
    httpClient: HttpClient? = null,
    bearerJwt: String,
) {
    if (bearerJwt.isBlank()) return
    @Suppress("UNUSED_PARAMETER")
    val unusedClient = httpClient
    runCatching {
        apiClient.prewarmProximityHandshake()
    }
}

/**
 * Server-side tri-factor clustering: posts ephemeral token + heard tokens + GPS to
 * `POST /api/connections/proximity` and returns matched user profiles or a pending async match.
 */
internal suspend fun ConnectionRepository.bindProximityHandshakeImpl(
    httpClient: HttpClient? = null,
    bearerJwt: String,
    myToken: String,
    heardTokens: List<String>,
    detectedDevices: List<String> = emptyList(),
    latitude: Double?,
    longitude: Double?,
    exactBarometricElevationM: Double? = null,
    hardwareVibe: HardwareVibeSnapshot? = null,
    clientContextFirst: Boolean = true,
    weatherSnapshotLabel: String? = null,
    bindContextTags: List<String>? = null,
    bindNoiseLevelCategory: NoiseLevelCategory? = null,
    bindExactNoiseLevelDb: Double? = null,
    bindHeightCategory: HeightCategory? = null,
    simulatorMock: Boolean = false,
): Result<BindProximityHandshakeResult> {
    return try {
        @Suppress("UNUSED_PARAMETER")
        val unusedClient = httpClient
        if (bearerJwt.isBlank()) {
            return Result.failure(IllegalStateException("Please sign in again."))
        }
        val normalizedHeard = heardTokens.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val normalizedBle = detectedDevices.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val combinedPeerTokens = (normalizedHeard + normalizedBle).distinct()
        val hasGps = latitude != null && longitude != null &&
            latitude.isFinite() && longitude.isFinite() &&
            !(latitude == 0.0 && longitude == 0.0)
        val trimmedWeather = weatherSnapshotLabel?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedBindTags = bindContextTags
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
        val tzOffsetMinutes = Clock.System.now()
            .offsetIn(TimeZone.currentSystemDefault())
            .totalSeconds
            .div(60)
        val request = ProximityHandshakePostBody(
            myToken = myToken,
            tokens = combinedPeerTokens,
            heardTokens = normalizedHeard,
            detectedDevices = normalizedBle,
            timezoneOffsetMinutes = tzOffsetMinutes,
            latitude = if (hasGps) latitude else null,
            longitude = if (hasGps) longitude else null,
            exactBarometricElevationM = exactBarometricElevationM?.takeIf { it.isFinite() },
            noiseLevel = bindNoiseLevelCategory?.name,
            exactNoiseLevelDb = bindExactNoiseLevelDb?.takeIf { it.isFinite() },
            contextTags = normalizedBindTags,
            heightCategory = bindHeightCategory?.name,
            luxLevel = hardwareVibe?.luxLevel?.takeIf { it.isFinite() }?.toDouble(),
            motionVariance = hardwareVibe?.motionVariance?.takeIf { it.isFinite() }?.toDouble(),
            compassAzimuth = hardwareVibe?.compassAzimuth?.takeIf { it.isFinite() }?.toDouble(),
            batteryLevel = hardwareVibe?.batteryLevel?.takeIf { it in 0..100 },
            clientContextFirst = if (clientContextFirst) true else null,
            weatherSnapshot = trimmedWeather,
            simulatorMock = if (simulatorMock) true else null,
        )
        val outgoingJson = json.encodeToString(ProximityHandshakePostBody.serializer(), request)
        println("OUTGOING_HANDSHAKE_PAYLOAD: $outgoingJson")
        val firstResult = apiClient.postProximityHandshake(request, bearerJwt = bearerJwt)
        val apiResult = firstResult.getOrElse { firstError ->
            val refreshed = if (firstError.isClickWebAuthFailure()) refreshedJwtAfterAuthFailure() else null
            refreshed?.let { freshJwt ->
                apiClient.postProximityHandshake(request, bearerJwt = freshJwt).getOrNull()
            } ?: return Result.failure(firstError)
        }
        when (apiResult) {
            is ProximityHandshakePostResult.InstantMatch -> {
                val parsed = apiResult.body
                parsed.toAwaitingHostSelectionOrNull()?.let { awaiting ->
                    return Result.success(awaiting)
                }
                val outcome = parsed.toBindOutcome()
                Result.success(BindProximityHandshakeResult.InstantMatch(outcome))
            }
            is ProximityHandshakePostResult.PendingMatch -> {
                val pending = apiResult.body
                Result.success(
                    BindProximityHandshakeResult.PendingServerMatch(
                        pendingHandshakeId = pending.pendingHandshakeId,
                        expiresAt = pending.expiresAt,
                    ),
                )
            }
            is ProximityHandshakePostResult.IgnoredEmptyPayload -> {
                Result.failure(IllegalStateException(PROXIMITY_NO_NEARBY_DEVICES_MESSAGE))
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal suspend fun ConnectionRepository.recoverPendingProximityHandshakeImpl(
    bearerJwt: String,
    pendingHandshakeId: String,
): Result<BindProximityHandshakeResult> {
    if (bearerJwt.isBlank()) {
        return Result.failure(IllegalStateException("Please sign in again."))
    }
    val pendingId = pendingHandshakeId.trim()
    if (pendingId.isEmpty()) {
        return Result.failure(IllegalArgumentException("pendingHandshakeId required"))
    }
    return try {
        when (val apiResult = apiClient.getPendingProximityHandshake(pendingId, bearerJwt = bearerJwt).getOrThrow()) {
            is ProximityHandshakePostResult.InstantMatch -> {
                apiResult.body.toAwaitingHostSelectionOrNull()?.let { awaiting ->
                    return Result.success(awaiting)
                }
                Result.success(BindProximityHandshakeResult.InstantMatch(apiResult.body.toBindOutcome()))
            }
            is ProximityHandshakePostResult.PendingMatch ->
                Result.success(
                    BindProximityHandshakeResult.PendingServerMatch(
                        pendingHandshakeId = apiResult.body.pendingHandshakeId,
                        expiresAt = apiResult.body.expiresAt,
                    ),
                )
            is ProximityHandshakePostResult.IgnoredEmptyPayload ->
                Result.failure(IllegalStateException(PROXIMITY_NO_NEARBY_DEVICES_MESSAGE))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Host confirms selected members after an `awaiting_selection` bind response.
 * Posts to `POST /api/connections/proximity/confirm`.
 */
internal suspend fun ConnectionRepository.confirmProximitySelectionImpl(
    pendingHandshakeId: String,
    selectedMemberIds: List<String>,
    contextTags: List<String>? = null,
    bearerJwt: String? = null,
): Result<BindProximityHandshakeOutcome> {
    val pendingId = pendingHandshakeId.trim()
    if (pendingId.isEmpty()) {
        return Result.failure(IllegalArgumentException("pendingHandshakeId required"))
    }
    val members = selectedMemberIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        .take(PROXIMITY_HOST_SELECTION_MAX_PEERS)
    if (members.isEmpty()) {
        return Result.failure(IllegalArgumentException("selectedMemberIds required"))
    }
    val jwt = bearerJwt?.trim()?.takeIf { it.isNotEmpty() }
        ?: tokenStorage.getJwt()?.trim()?.takeIf { it.isNotEmpty() }
    if (jwt.isNullOrBlank()) {
        return Result.failure(IllegalStateException("Please sign in again."))
    }
    return try {
        val firstResult = apiClient.postProximityConfirmSelection(
            bearerJwt = jwt,
            pendingHandshakeId = pendingId,
            selectedMemberIds = members,
            contextTags = contextTags,
        )
        val dto = firstResult.getOrElse { firstError ->
            val refreshed = if (firstError.isClickWebAuthFailure()) refreshedJwtAfterAuthFailure() else null
            refreshed?.let { freshJwt ->
                apiClient.postProximityConfirmSelection(
                    bearerJwt = freshJwt,
                    pendingHandshakeId = pendingId,
                    selectedMemberIds = members,
                    contextTags = contextTags,
                ).getOrNull()
            } ?: return Result.failure(firstError)
        }
        Result.success(dto.toBindOutcome())
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal suspend fun ConnectionRepository.enqueuePendingProximityHandshakeImpl(
    myToken: String,
    heardTokens: List<String>,
    detectedDevices: List<String> = emptyList(),
    latitude: Double?,
    longitude: Double?,
    altitudeMeters: Double?,
    hardwareVibe: HardwareVibeSnapshot? = null,
    noiseLevel: String? = null,
    exactNoiseLevelDb: Double? = null,
    heightCategory: String? = null,
    exactBarometricElevationM: Double? = null,
    contextTags: List<String> = emptyList(),
) {
    pendingEncounterQueue.hydrate()
    pendingEncounterQueue.enqueue(
        myToken = myToken,
        heardTokens = heardTokens,
        detectedDevices = detectedDevices,
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = altitudeMeters,
        hardwareVibe = hardwareVibe,
        noiseLevel = noiseLevel,
        exactNoiseLevelDb = exactNoiseLevelDb,
        heightCategory = heightCategory,
        exactBarometricElevationM = exactBarometricElevationM,
        contextTags = contextTags,
    )
}

internal suspend fun ConnectionRepository.pendingProximityHandshakeQueueSizeImpl(): Int {
    pendingEncounterQueue.hydrate()
    return pendingEncounterQueue.size
}

/**
 * Replays the oldest queued handshake against `POST /api/connections/proximity`.
 * Drops head when the server returns an empty match list (stale tokens).
 */
internal suspend fun ConnectionRepository.syncPendingProximityHandshakesImpl(bearerJwt: String): PendingProximityHandshakeSyncResult {
    pendingEncounterQueue.hydrate()
    if (bearerJwt.isBlank()) {
        return PendingProximityHandshakeSyncResult(null, pendingEncounterQueue.size)
    }
    while (true) {
        val queue = pendingEncounterQueue.snapshot()
        if (queue.isEmpty()) {
            return PendingProximityHandshakeSyncResult(null, 0)
        }
        val head = queue.first()
        val lat = head.location?.latitude
        val lng = head.location?.longitude
        val attempt = runCatching {
            withTimeout(ConnectionRepository.CONNECTION_TIMEOUT_MS + 12_000L) {
                bindProximityHandshake(
                    httpClient = null,
                    bearerJwt = bearerJwt,
                    myToken = head.myToken,
                    heardTokens = head.heardTokens,
                    detectedDevices = head.detectedDevices,
                    latitude = lat,
                    longitude = lng,
                    exactBarometricElevationM = head.exactBarometricElevationM,
                    hardwareVibe = head.hardwareVibe,
                    bindContextTags = head.contextTags.takeIf { it.isNotEmpty() },
                    bindNoiseLevelCategory = head.noiseLevel?.let { raw ->
                        runCatching { NoiseLevelCategory.valueOf(raw.uppercase().replace(' ', '_')) }.getOrNull()
                    },
                    bindExactNoiseLevelDb = head.exactNoiseLevelDb,
                    bindHeightCategory = head.heightCategory?.let { raw ->
                        runCatching { HeightCategory.valueOf(raw.uppercase().replace(' ', '_')) }.getOrNull()
                    },
                ).getOrThrow()
            }
        }
        if (attempt.isSuccess) {
            val bindResult = attempt.getOrNull()!!
            val rest = queue.drop(1)
            pendingEncounterQueue.replaceAll(rest)
            when (bindResult) {
                is BindProximityHandshakeResult.PendingServerMatch -> {
                    return PendingProximityHandshakeSyncResult(
                        recoveredUsers = null,
                        remainingInQueue = rest.size,
                        serverPendingMatch = true,
                    )
                }
                is BindProximityHandshakeResult.AwaitingHostSelection -> {
                    return PendingProximityHandshakeSyncResult(
                        recoveredUsers = bindResult.candidates,
                        remainingInQueue = rest.size,
                        recoveredEncounterLogged = false,
                        groupCliqueCandidateMemberIds = bindResult.groupCliqueCandidateMemberIds,
                        pendingHandshakeId = bindResult.pendingHandshakeId,
                        isAggregateNewConnection = bindResult.isAggregateNewConnection,
                    )
                }
                is BindProximityHandshakeResult.InstantMatch -> {
                    val outcome = bindResult.outcome
                    val users = outcome.matches
                    if (users.isNotEmpty()) {
                        return PendingProximityHandshakeSyncResult(
                            recoveredUsers = users,
                            remainingInQueue = rest.size,
                            recoveredEncounterLogged = outcome.encounterLogged,
                            groupCliqueCandidateMemberIds = outcome.groupCliqueCandidateMemberIds,
                            isAggregateNewConnection = outcome.isAggregateNewConnection,
                        )
                    }
                    continue
                }
            }
        }
        val err = attempt.exceptionOrNull() ?: return PendingProximityHandshakeSyncResult(null, queue.size)
        val authHint = err.message?.lowercase().orEmpty()
        if (authHint.contains("401") || authHint.contains("403") ||
            authHint.contains("unauthorized") || authHint.contains("invalid jwt") ||
            err is ClickWebRequestException && err.statusCode in setOf(401, 403)
        ) {
            return PendingProximityHandshakeSyncResult(
                recoveredUsers = null,
                remainingInQueue = queue.size,
                authorizationFailed = true,
            )
        }
        if (err is TimeoutCancellationException || err.isRetryableForProximityBind()) {
            return PendingProximityHandshakeSyncResult(null, queue.size)
        }
        val rest = queue.drop(1)
        pendingEncounterQueue.replaceAll(rest)
    }
}
