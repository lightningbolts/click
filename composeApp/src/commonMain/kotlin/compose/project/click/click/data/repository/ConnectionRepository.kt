package compose.project.click.click.data.repository

import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.OpenMeteoWeatherService
import compose.project.click.click.data.WeatherService
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.calibrateBarometricElevationMeters
import compose.project.click.click.data.models.ConnectionInsert
import compose.project.click.click.data.models.ConnectionRequest
import compose.project.click.click.data.models.ContextTag
import compose.project.click.click.data.models.PendingConnectionDraft
import compose.project.click.click.data.models.deriveHeightCategory
import compose.project.click.click.data.models.GeoLocation
import compose.project.click.click.data.models.HeightCategory
import compose.project.click.click.data.models.ConnectionEncounter
import compose.project.click.click.data.models.MemoryCapsule
import compose.project.click.click.data.models.WeatherSnapshot
import compose.project.click.click.data.models.NoiseLevelCategory
import compose.project.click.click.data.models.newPendingConnectionId
import compose.project.click.click.data.models.PollPairSuggestion
import compose.project.click.click.data.models.ReconnectHelper
import compose.project.click.click.data.models.ConnectionActivityStatus
import compose.project.click.click.data.models.User
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.data.storage.createTokenStorage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.withTimeout
import kotlin.math.*

private fun buildUtcTimeOfDayLabel(epochMillis: Long): String {
    val utcTime = Clock.System
        .now()
        .let { kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis) }
        .toLocalDateTime(TimeZone.UTC)

    fun Int.twoDigits(): String = toString().padStart(2, '0')

    return "${utcTime.hour.twoDigits()}:${utcTime.minute.twoDigits()}:${utcTime.second.twoDigits()} UTC"
}

/**
 * Proximity verification result from server-side validation.
 */
sealed class ProximityResult {
    data class Success(val connection: Connection) : ProximityResult()
    data class ProximityRejected(val distance: Int) : ProximityResult()
    data class Error(val message: String) : ProximityResult()
}

@Serializable
private data class BindProximityResponse(
    val matches: List<User> = emptyList(),
    val error: String? = null,
)

@Serializable
private data class BindProximityRequest(
    @SerialName("my_token") val myToken: String,
    @SerialName("heard_tokens") val heardTokens: List<String>,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

class ConnectionRepository(
    private val weatherService: WeatherService = OpenMeteoWeatherService(),
    private val tokenStorage: TokenStorage = createTokenStorage()
) {
    @Serializable
    private data class EncounterIdOnlyRow(val id: String)

    private val supabase by lazy { SupabaseConfig.client }
    private val supabaseRepository = SupabaseRepository()
    private val json = Json { ignoreUnknownKeys = true }
    private companion object {
        const val CONNECTION_TIMEOUT_MS = 15_000L
    }

    private fun normalizeContextTag(
        contextTagObject: ContextTag?,
        contextTag: String?
    ): ContextTag? {
        return contextTagObject ?: contextTag
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { ContextTag(id = "custom", label = it, emoji = "✏️") }
    }

    private fun resolveContextTagId(contextTag: ContextTag?): String? {
        return when {
            contextTag == null -> null
            contextTag.id == "custom" -> contextTag.label
            else -> contextTag.id
        }
    }

    /**
     * Server-side tri-factor clustering: posts ephemeral token + heard tokens + GPS to the
     * Supabase Edge Function [bind-proximity-connection] and returns matched user profiles.
     */
    suspend fun bindProximityHandshake(
        httpClient: HttpClient,
        bearerJwt: String,
        myToken: String,
        heardTokens: List<String>,
        latitude: Double?,
        longitude: Double?,
    ): Result<List<User>> {
        return try {
            val hasGps = latitude != null && longitude != null &&
                latitude.isFinite() && longitude.isFinite() &&
                !(latitude == 0.0 && longitude == 0.0)
            val request = BindProximityRequest(
                myToken = myToken,
                heardTokens = heardTokens,
                latitude = if (hasGps) latitude else null,
                longitude = if (hasGps) longitude else null,
            )
            val response = httpClient.post(SupabaseConfig.functionUrl("bind-proximity-connection")) {
                contentType(ContentType.Application.Json)
                headers {
                    append("apikey", SupabaseConfig.supabaseAnonApiKey)
                    append(HttpHeaders.Authorization, "Bearer $bearerJwt")
                }
                setBody(request)
            }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                return Result.failure(Exception(text.ifBlank { "bind-proximity-connection failed" }))
            }
            val parsed = json.decodeFromString(BindProximityResponse.serializer(), text)
            if (!parsed.error.isNullOrBlank()) {
                return Result.failure(Exception(parsed.error))
            }
            Result.success(parsed.matches)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun bumpChatUpdatedAt(connectionId: String, atMs: Long) {
        try {
            supabase.from("chats")
                .update(buildJsonObject { put("updated_at", atMs) }) {
                    filter { eq("connection_id", connectionId) }
                }
        } catch (_: Exception) {
            // Non-fatal
        }
    }

    private suspend fun insertConnectionEncounter(
        connectionId: String,
        encounteredAtMs: Long,
        locationName: String?,
        lat: Double?,
        lon: Double?,
        weather: WeatherSnapshot?,
        contextTags: List<String>,
        noiseLevel: String?,
        elevationCategory: String?,
    ) {
        val payload = buildJsonObject {
            put("connection_id", connectionId)
            put("encountered_at", Instant.fromEpochMilliseconds(encounteredAtMs).toString())
            locationName?.trim()?.takeIf { it.isNotEmpty() }?.let { put("location_name", it) }
            if (lat != null && lon != null && lat.isFinite() && lon.isFinite() && !(lat == 0.0 && lon == 0.0)) {
                put("gps_lat", lat)
                put("gps_lon", lon)
            }
            if (weather != null) {
                put("weather_snapshot", json.encodeToJsonElement(WeatherSnapshot.serializer(), weather))
            }
            noiseLevel?.trim()?.takeIf { it.isNotEmpty() }?.let { put("noise_level", it) }
            elevationCategory?.trim()?.takeIf { it.isNotEmpty() }?.let { put("elevation_category", it) }
            put("context_tags", JsonArray(contextTags.map { JsonPrimitive(it) }))
        }
        try {
            supabase.from("connection_encounters").insert(payload)
        } catch (e: RestException) {
            val msg = e.message ?: e.toString()
            if (msg.contains("encounter_rate_limit_3h", ignoreCase = true)) {
                bumpChatUpdatedAt(connectionId, encounteredAtMs)
            } else {
                throw e
            }
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("encounter_rate_limit_3h", ignoreCase = true)) {
                bumpChatUpdatedAt(connectionId, encounteredAtMs)
            } else {
                throw e
            }
        }
    }

    /**
     * Persists subjective encounter context on the **latest** [connection_encounters] row
     * (proximity fan-out / tag sheet after a crossing).
     */
    suspend fun updateConnectionTags(
        connectionId: String,
        contextTag: ContextTag?,
        noiseLevelCategory: NoiseLevelCategory?,
        exactNoiseLevelDb: Double?,
        heightCategory: HeightCategory?,
        exactBarometricElevationMeters: Double?,
    ): Result<Unit> {
        if (connectionId.isBlank()) {
            return Result.failure(Exception("Missing connection id"))
        }
        return try {
            fetchConnectionById(connectionId) ?: return Result.failure(Exception("Connection not found"))
            val normalizedContextTag = normalizeContextTag(contextTagObject = contextTag, contextTag = null)
            val latest = supabase.from("connection_encounters")
                .select {
                    filter { eq("connection_id", connectionId) }
                    order("encountered_at", Order.DESCENDING)
                    limit(1)
                }
                .decodeList<EncounterIdOnlyRow>()
                .firstOrNull() ?: return Result.failure(Exception("No encounter row for connection"))

            val tags = listOfNotNull(normalizedContextTag?.label?.trim()?.takeIf { it.isNotEmpty() })
                .ifEmpty { listOfNotNull(resolveContextTagId(normalizedContextTag)?.trim()) }
                .map { JsonPrimitive(it) }
            supabase.from("connection_encounters")
                .update(buildJsonObject {
                    put("context_tags", JsonArray(tags))
                    noiseLevelCategory?.name?.let { put("noise_level", it) }
                    heightCategory?.name?.let { put("elevation_category", it) }
                }) {
                    filter { eq("id", latest.id) }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Serializable
    private data class RedeemQrTokenResponse(
        val success: Boolean,
        val error: String? = null,
        @SerialName("user_id") val userId: String? = null,
        @SerialName("token_age_ms") val tokenAgeMs: Long? = null,
        @SerialName("distance_meters") val distanceMeters: Int? = null,
    )

    /**
     * `redeem_qr_token` returns a JSON **object** (`RETURNS JSONB`). supabase-kt's [decodeSingle] decodes the
     * HTTP body as `List<T>` then `.first()`, which throws "Expected start of the array '['…" on `{…}` payloads.
     */
    private fun decodeRedeemQrTokenPayload(body: String): RedeemQrTokenResponse {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            throw SerializationException("Empty redeem_qr_token response")
        }
        return try {
            json.decodeFromString(RedeemQrTokenResponse.serializer(), trimmed)
        } catch (e: SerializationException) {
            val root: JsonElement = try {
                json.parseToJsonElement(trimmed)
            } catch (_: Exception) {
                throw e
            }
            when (root) {
                is JsonArray -> {
                    val sole = root.firstOrNull() as? JsonObject
                        ?: throw SerializationException("redeem_qr_token array payload missing object row")
                    json.decodeFromJsonElement(RedeemQrTokenResponse.serializer(), sole)
                }
                is JsonObject -> json.decodeFromJsonElement(RedeemQrTokenResponse.serializer(), root)
                is JsonPrimitive -> {
                    if (!root.isString) throw e
                    json.decodeFromString(RedeemQrTokenResponse.serializer(), root.content)
                }
                else -> throw e
            }
        }
    }

    /**
     * Create a connection between two users with proximity verification.
     *
     * Performs Haversine distance check if both locations are available,
     * computes a proximity confidence score, and stores the signals.
     * The Supabase anomaly detection trigger runs on INSERT.
     */
    suspend fun createConnection(request: ConnectionRequest): Result<Connection> {
        return try {
            val onlineResult = withTimeout(CONNECTION_TIMEOUT_MS) {
                createConnectionOnline(request)
            }
            if (onlineResult.isSuccess) {
                onlineResult
            } else {
                val error = onlineResult.exceptionOrNull()
                if (shouldQueueOffline(request, error)) {
                    Result.success(queuePendingConnection(request))
                } else {
                    onlineResult
                }
            }
        } catch (e: Exception) {
            if (shouldQueueOffline(request, e)) {
                Result.success(queuePendingConnection(request))
            } else {
                Result.failure(e)
            }
        }
    }

    private suspend fun createConnectionOnline(request: ConnectionRequest): Result<Connection> {
        return try {
            val redeemedToken = if (!request.qrToken.isNullOrBlank()) {
                val sendScannerGps = request.venueId.isNullOrBlank()
                redeemQrToken(
                    token = request.qrToken,
                    scannerLat = if (sendScannerGps) request.locationLat else null,
                    scannerLon = if (sendScannerGps) request.locationLng else null,
                ).getOrElse { return Result.failure(it) }
            } else {
                null
            }

            val scannedUserId = redeemedToken?.userId ?: request.userId2
            if (scannedUserId.isBlank()) {
                return Result.failure(Exception("Invalid QR code"))
            }

            if (scannedUserId == request.userId1) {
                return Result.failure(Exception("You cannot connect with yourself!"))
            }

            val resolvedTokenAgeMs = redeemedToken?.tokenAgeMs ?: request.tokenAgeMs

            val existingConnection = findConnectionRowForUserPair(
                request.userId1,
                scannedUserId
            )

            if (existingConnection != null) {
                return restoreExistingConnection(
                    request = request,
                    scannedUserId = scannedUserId,
                    existing = existingConnection,
                    resolvedTokenAgeMs = resolvedTokenAgeMs,
                )
            }

            val nowInstant = Clock.System.now()
            val now = nowInstant.toEpochMilliseconds()
            val expiry = now + (30L * 24 * 60 * 60 * 1000) // 30 days
            val createdUtc = nowInstant.toString()
            val timeOfDayUtc = buildUtcTimeOfDayLabel(now)

            // ── Proximity validation ──

            val loc1Valid = request.locationLat != null && request.locationLng != null &&
                request.locationLat.isFinite() && request.locationLng.isFinite() &&
                !(request.locationLat == 0.0 && request.locationLng == 0.0)

            // For now, we only have the initiator's location on mobile.
            // The server can reject if it also has the scanner's location.
            val gpsAvailable = loc1Valid

            // Compute proximity confidence score
            val proximityScore = computeProximityScore(
                connectionMethod = request.connectionMethod,
                gpsAvailable = gpsAvailable,
                tokenAgeMs = resolvedTokenAgeMs
            )

            // Build proximity signals for auditability
            val proximitySignals = buildJsonObject {
                put("connection_method", request.connectionMethod)
                put("gps_available", gpsAvailable)
                if (resolvedTokenAgeMs != null) {
                    put("token_age_seconds", resolvedTokenAgeMs / 1000.0)
                }
                if (loc1Valid) {
                    put("initiator_lat", request.locationLat!!)
                    put("initiator_lon", request.locationLng!!)
                }
                if (!request.venueId.isNullOrBlank()) {
                    put("venue_id", request.venueId)
                }
            }

            val normalizedContextTag = normalizeContextTag(
                contextTagObject = request.contextTagObject,
                contextTag = request.contextTag
            )
            val contextTagId = resolveContextTagId(normalizedContextTag)
            val initiatorId = request.initiatorId ?: when (request.connectionMethod) {
                "qr" -> scannedUserId
                "nfc", "proximity" -> if (request.userId1 == scannedUserId) request.userId2 else scannedUserId
                else -> null
            }
            val responderId = request.responderId ?: when (request.connectionMethod) {
                "qr" -> request.userId1
                "nfc", "proximity" -> if (initiatorId == request.userId1) scannedUserId else request.userId1
                else -> null
            }

            val connectionInsert = ConnectionInsert(
                user_ids = listOf(request.userId1, scannedUserId),
                created = now,
                expiry = expiry,
                should_continue = listOf(false, false),
                has_begun = false,
                expiry_state = "pending",
                include_in_business_insights = AppDataManager.locationPreferences.value.includeInInsightsEnabled,
                initiator_id = initiatorId,
                responder_id = responderId
            )

            val result = supabase.from("connections")
                .insert(connectionInsert) {
                    select()
                }
                .decodeSingle<Connection>()

            var semanticLocationName: String? = null
            var fullLocationMap: Map<String, String>? = null

            if (loc1Valid) {
                try {
                    val semanticResult = resolveSemanticLocation(
                        lat = request.locationLat!!,
                        lon = request.locationLng!!
                    )
                    if (semanticResult != null) {
                        semanticLocationName = semanticResult.first
                        fullLocationMap = semanticResult.second
                    }
                } catch (e: Exception) {
                    println("ConnectionRepository: Failed to resolve semantic location: ${e.message}")
                }
            }

            val weatherSnapshot = if (loc1Valid) {
                weatherService.fetchWeather(request.locationLat!!, request.locationLng!!)
            } else {
                null
            }

            val exactBarometricElevationMeters = calibrateBarometricElevationMeters(
                stationPressureHpa = request.exactBarometricPressureHpa,
                seaLevelPressureHpa = weatherSnapshot?.pressureMslHpa
            ) ?: request.altitudeMeters?.takeIf { request.exactBarometricPressureHpa != null }
                ?: request.exactBarometricElevationMeters

            val heightCategory = deriveHeightCategory(exactBarometricElevationMeters ?: request.altitudeMeters)
                ?: request.heightCategory

            val contextTags = listOfNotNull(contextTagId?.trim()?.takeIf { it.isNotEmpty() })
            try {
                supabase.from("connections")
                    .update(buildJsonObject {
                        put("proximity_confidence", proximityScore)
                        put("proximity_signals", proximitySignals)
                        put("connection_method", request.connectionMethod)
                        put("flagged", proximityScore < 20)
                        put("created_utc", createdUtc)
                        put("time_of_day_utc", timeOfDayUtc)
                    }) {
                        filter {
                            eq("id", result.id)
                        }
                    }
            } catch (e: Exception) {
                println("ConnectionRepository: Failed to update connection metadata: ${e.message}")
            }

            runCatching {
                insertConnectionEncounter(
                    connectionId = result.id,
                    encounteredAtMs = now,
                    locationName = semanticLocationName,
                    lat = if (loc1Valid) request.locationLat else null,
                    lon = if (loc1Valid) request.locationLng else null,
                    weather = weatherSnapshot,
                    contextTags = contextTags,
                    noiseLevel = request.noiseLevelCategory?.name,
                    elevationCategory = heightCategory?.name,
                )
            }.onFailure { println("ConnectionRepository: encounter insert: ${it.message}") }

            // Create chat row for this connection
            try {
                supabase.from("chats")
                    .insert(buildJsonObject {
                        put("connection_id", result.id)
                        put("created_at", now)
                        put("updated_at", now)
                    })
            } catch (e: Exception) {
                println("ConnectionRepository: Failed to create chat: ${e.message}")
                // Non-fatal — connection was created
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reconnect: clear per-user junction rows for both participants, reset lifecycle fields, refresh metadata.
     */
    private suspend fun restoreExistingConnection(
        request: ConnectionRequest,
        scannedUserId: String,
        existing: Connection,
        resolvedTokenAgeMs: Long?,
    ): Result<Connection> {
        return try {
            supabaseRepository.clearConnectionJunctionForPair(existing.id, existing.user_ids)

            val nowInstant = Clock.System.now()
            val now = nowInstant.toEpochMilliseconds()
            val expiry = now + (30L * 24 * 60 * 60 * 1000)
            val createdUtc = nowInstant.toString()
            val timeOfDayUtc = buildUtcTimeOfDayLabel(now)

            val loc1Valid = request.locationLat != null && request.locationLng != null &&
                request.locationLat.isFinite() && request.locationLng.isFinite() &&
                !(request.locationLat == 0.0 && request.locationLng == 0.0)
            val gpsAvailable = loc1Valid

            val proximityScore = computeProximityScore(
                connectionMethod = request.connectionMethod,
                gpsAvailable = gpsAvailable,
                tokenAgeMs = resolvedTokenAgeMs,
            )
            val proximitySignals = buildJsonObject {
                put("connection_method", request.connectionMethod)
                put("gps_available", gpsAvailable)
                if (resolvedTokenAgeMs != null) {
                    put("token_age_seconds", resolvedTokenAgeMs / 1000.0)
                }
                if (loc1Valid) {
                    put("initiator_lat", request.locationLat!!)
                    put("initiator_lon", request.locationLng!!)
                }
                if (!request.venueId.isNullOrBlank()) {
                    put("venue_id", request.venueId)
                }
            }

            val normalizedContextTag = normalizeContextTag(
                contextTagObject = request.contextTagObject,
                contextTag = request.contextTag,
            )
            val contextTagId = resolveContextTagId(normalizedContextTag)
            val initiatorId = request.initiatorId ?: when (request.connectionMethod) {
                "qr" -> scannedUserId
                "nfc", "proximity" -> if (request.userId1 == scannedUserId) request.userId2 else scannedUserId
                else -> null
            }
            val responderId = request.responderId ?: when (request.connectionMethod) {
                "qr" -> request.userId1
                "nfc", "proximity" -> if (initiatorId == request.userId1) scannedUserId else request.userId1
                else -> null
            }

            var semanticLocationName: String? = null
            var fullLocationMap: Map<String, String>? = null
            if (loc1Valid) {
                try {
                    val semanticResult = resolveSemanticLocation(
                        lat = request.locationLat!!,
                        lon = request.locationLng!!,
                    )
                    if (semanticResult != null) {
                        semanticLocationName = semanticResult.first
                        fullLocationMap = semanticResult.second
                    }
                } catch (e: Exception) {
                    println("ConnectionRepository: Failed to resolve semantic location: ${e.message}")
                }
            }

            val weatherSnapshot = if (loc1Valid) {
                weatherService.fetchWeather(request.locationLat!!, request.locationLng!!)
            } else {
                null
            }

            val exactBarometricElevationMeters = calibrateBarometricElevationMeters(
                stationPressureHpa = request.exactBarometricPressureHpa,
                seaLevelPressureHpa = weatherSnapshot?.pressureMslHpa,
            ) ?: request.altitudeMeters?.takeIf { request.exactBarometricPressureHpa != null }
                ?: request.exactBarometricElevationMeters

            val heightCategory = deriveHeightCategory(exactBarometricElevationMeters ?: request.altitudeMeters)
                ?: request.heightCategory

            val result = supabase.from("connections")
                .update(buildJsonObject {
                    put("status", "active")
                    put("expiry_state", "active")
                    put("last_message_at", now)
                    put("created", now)
                    put("expiry", expiry)
                    put("created_utc", createdUtc)
                    put("time_of_day_utc", timeOfDayUtc)
                    put("proximity_confidence", proximityScore)
                    put("proximity_signals", proximitySignals)
                    put("connection_method", request.connectionMethod)
                    put("flagged", proximityScore < 20)
                    put("include_in_business_insights", AppDataManager.locationPreferences.value.includeInInsightsEnabled)
                    initiatorId?.let { put("initiator_id", it) }
                    responderId?.let { put("responder_id", it) }
                }) {
                    filter {
                        eq("id", existing.id)
                    }
                    select()
                }
                .decodeSingle<Connection>()

            val restoreTags = listOfNotNull(contextTagId?.trim()?.takeIf { it.isNotEmpty() })
            runCatching {
                insertConnectionEncounter(
                    connectionId = result.id,
                    encounteredAtMs = now,
                    locationName = semanticLocationName,
                    lat = if (loc1Valid) request.locationLat else null,
                    lon = if (loc1Valid) request.locationLng else null,
                    weather = weatherSnapshot,
                    contextTags = restoreTags,
                    noiseLevel = request.noiseLevelCategory?.name,
                    elevationCategory = heightCategory?.name,
                )
            }.onFailure { println("ConnectionRepository: restore encounter insert: ${it.message}") }

            try {
                supabase.from("chats")
                    .insert(buildJsonObject {
                        put("connection_id", result.id)
                        put("created_at", now)
                        put("updated_at", now)
                    })
            } catch (e: Exception) {
                println("ConnectionRepository: Failed to create chat on restore: ${e.message}")
            }

            AppDataManager.applyRestoredConnection(result)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncPendingConnections(): Int {
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
                withTimeout(CONNECTION_TIMEOUT_MS) {
                    createConnectionOnline(draft.request)
                }
            }.getOrElse { Result.failure(it) }

            if (result.isSuccess) {
                val connection = result.getOrNull() ?: return@forEach
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

    /**
     * Compute a local proximity confidence score based on available signals.
     *
     * | Signal                  | Points |
     * |─────────────────────────|────────|
     * | NFC connection          | +50    |
     * | GPS available           | +15    |
     * | GPS not available       |  0     |
     * | QR token age < 30s      | +10    |
     * | QR token age 30–60s     | +5     |
     * | QR token age > 60s      |  0     |
     *
     * Note: Full scoring (GPS distance, shared BSSID) requires both users'
     * data and is done server-side when using the web API endpoint.
     */
    private fun computeProximityScore(
        connectionMethod: String,
        gpsAvailable: Boolean,
        tokenAgeMs: Long?
    ): Int {
        var score = 0

        // Connection method baseline
        if (connectionMethod == "nfc" || connectionMethod == "proximity") {
            score += 50
        }

        // GPS availability (single-side — we don't have both locs on mobile)
        if (gpsAvailable) {
            score += 15
        }

        // QR token age scoring
        if (tokenAgeMs != null) {
            val tokenAgeSec = tokenAgeMs / 1000
            if (tokenAgeSec < 30) score += 10
            else if (tokenAgeSec <= 60) score += 5
        }

        return score.coerceIn(0, 100)
    }

    /**
     * Reverse geocode GPS coordinates to a semantic location name
     * using the OpenStreetMap Nominatim API.
     *
     * @return Pair of (display_name, full_address_map) or null on failure.
     */
    private suspend fun resolveSemanticLocation(lat: Double, lon: Double): Pair<String, Map<String, String>>? {
        return try {
            val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json"
            val client = io.ktor.client.HttpClient()
            val response = client.get(url) {
                headers {
                    append("User-Agent", "ClickApp/1.0")
                }
            }
            val body = response.bodyAsText()
            client.close()

            val jsonObj = kotlinx.serialization.json.Json.parseToJsonElement(body)
                .jsonObject

            val displayName = jsonObj["display_name"]?.jsonPrimitive?.contentOrNull
            val addressObj = jsonObj["address"]?.jsonObject

            // Build a flat map of address components
            val addressMap = addressObj?.mapValues { (_, v) ->
                v.jsonPrimitive.contentOrNull ?: ""
            } ?: emptyMap()

            // Use a short display name: prefer building/amenity, then road, then full display_name
            val shortName = addressMap["building"]
                ?: addressMap["amenity"]
                ?: addressMap["leisure"]
                ?: addressMap["tourism"]
                ?: listOfNotNull(
                    addressMap["road"],
                    addressMap["neighbourhood"] ?: addressMap["suburb"]
                ).joinToString(", ").ifEmpty { null }
                ?: displayName

            if (shortName != null) {
                Pair(shortName, addressMap)
            } else {
                null
            }
        } catch (e: Exception) {
            println("ConnectionRepository: Nominatim reverse geocode failed: ${e.message}")
            null
        }
    }


    /**
     * Any connection row for the unordered user pair (including removed/archived lifecycle).
     */
    private suspend fun findConnectionRowForUserPair(
        userId1: String,
        userId2: String
    ): Connection? {
        return try {
            supabase.from("connections")
                .select {
                    filter {
                        contains("user_ids", listOf(userId1, userId2))
                    }
                }
                .decodeList<Connection>()
                .firstOrNull { conn ->
                    userId1 in conn.user_ids && userId2 in conn.user_ids
                }
        } catch (e: Exception) {
            println("Error checking connection: ${e.message}")
            null
        }
    }

    private suspend fun fetchConnectionById(connectionId: String): Connection? {
        if (connectionId.isBlank()) return null
        return try {
            supabase.from("connections")
                .select {
                    filter {
                        eq("id", connectionId)
                    }
                    limit(1)
                }
                .decodeList<Connection>()
                .firstOrNull()
        } catch (e: Exception) {
            println("ConnectionRepository: fetchConnectionById failed: ${e.message}")
            null
        }
    }

    /**
     * Pick one connection the user has not interacted with recently: sorts by
     * last interaction time ascending (oldest first) among connections whose
     * activity is cooling, dormant, or inactive (7+ days since last message
     * or connection creation when never messaged). Uses the in-memory
     * [connections] list (e.g. from [AppDataManager]) to avoid an extra round-trip.
     */
    fun getPollPairSuggestion(
        userId: String,
        connections: List<Connection>,
        connectedUsers: Map<String, User>
    ): PollPairSuggestion? {
        return connections
            .asSequence()
            .filter { it.isVisibleInActiveUi() }
            .mapNotNull { connection ->
                val otherUserId = connection.user_ids.firstOrNull { it != userId } ?: return@mapNotNull null
                val lastInteraction = connection.last_message_at ?: connection.created
                val status = ReconnectHelper.getActivityStatus(lastInteraction)
                if (status == ConnectionActivityStatus.ACTIVE) return@mapNotNull null
                Triple(connection, otherUserId, lastInteraction)
            }
            .sortedBy { it.third }
            .firstOrNull()
            ?.let { (connection, otherUserId, lastInteraction) ->
                val otherName = connectedUsers[otherUserId]?.name
                PollPairSuggestion(
                    connectionId = connection.id,
                    otherUserId = otherUserId,
                    otherUserName = otherName,
                    lastInteractionAt = lastInteraction,
                    daysSinceContact = ReconnectHelper.getDaysSinceContact(lastInteraction),
                    contextTag = connection.context_tag
                )
            }
    }

    /**
     * Load connections from Supabase and return a poll-pair suggestion, or null.
     */
    suspend fun getPollPairSuggestion(userId: String): PollPairSuggestion? {
        val connections = getUserConnections(userId).getOrNull() ?: return null
        val usersMap = AppDataManager.connectedUsers.value
        return getPollPairSuggestion(userId, connections, usersMap)
    }

    /**
     * Get all connections for a user
     */
    suspend fun getUserConnections(userId: String): Result<List<Connection>> {
        return try {
            val snapshot = supabaseRepository.fetchUserConnectionsSnapshot(userId)
            val visible = snapshot.connections.filter { it.isVisibleInActiveUi() }
            Result.success(visible)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get user by ID
     */
    suspend fun getUserById(userId: String): Result<User> {
        return try {
            val user = supabaseRepository.fetchUsersByIds(listOf(userId)).firstOrNull()
                ?: return Result.failure(Exception("User not found"))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun queuePendingConnection(request: ConnectionRequest): Connection {
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
            tokenAgeMs = null
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

    private suspend fun loadPendingConnectionQueue(): List<PendingConnectionDraft> {
        val queueJson = tokenStorage.getPendingConnectionQueue()
        if (queueJson.isNullOrBlank()) return emptyList()

        return runCatching {
            json.decodeFromString<List<PendingConnectionDraft>>(queueJson)
        }.getOrElse {
            println("ConnectionRepository: Failed to decode pending queue: ${it.message}")
            emptyList()
        }
    }

    private suspend fun savePendingConnectionQueue(queue: List<PendingConnectionDraft>) {
        val serialized = if (queue.isEmpty()) null else json.encodeToString(queue)
        tokenStorage.savePendingConnectionQueue(serialized)
    }

    private fun shouldQueueOffline(request: ConnectionRequest, error: Throwable?): Boolean {
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

    private fun shouldDropQueuedDraft(error: Throwable?): Boolean {
        val message = error?.message?.lowercase().orEmpty()
        return message.contains("already exists") ||
            message.contains("cannot connect with yourself") ||
            message.contains("invalid qr code") ||
            message.contains("already used") ||
            message.contains("expired") ||
            message.contains("same physical location")
    }

    private suspend fun redeemQrToken(
        token: String,
        scannerLat: Double? = null,
        scannerLon: Double? = null
    ): Result<RedeemQrTokenResponse> {
        return try {
            val rpcResult = supabase.postgrest.rpc(
                "redeem_qr_token",
                buildJsonObject {
                    put("p_token", token)
                    if (scannerLat != null && scannerLon != null
                        && scannerLat.isFinite() && scannerLon.isFinite()
                        && !(scannerLat == 0.0 && scannerLon == 0.0)) {
                        put("p_scanner_lat", scannerLat)
                        put("p_scanner_lon", scannerLon)
                    }
                }
            )
            val response = try {
                decodeRedeemQrTokenPayload(rpcResult.data)
            } catch (e: Exception) {
                return Result.failure(e)
            }

            if (!response.success) {
                val message = when (response.error) {
                    "proximity_failed" -> "Connection failed: Users must be in the same physical location."
                    "expired" -> "This QR code has expired. Ask them to generate a new one."
                    "already_used" -> "This QR code was already used. Ask them to generate a new one."
                    "not_found" -> "This QR code is no longer valid. Ask them to generate a new one."
                    else -> "Failed to redeem QR code"
                }
                Result.failure(Exception(message))
            } else if (response.userId.isNullOrBlank()) {
                Result.failure(Exception("Invalid QR code"))
            } else {
                Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Hide a connection for the signed-in user ([connection_hidden]); does not alter [connections] rows.
     */
    suspend fun deleteConnection(connectionId: String): Result<Unit> {
        return try {
            val uid = SupabaseConfig.client.auth.currentUserOrNull()?.id?.takeIf { it.isNotBlank() }
                ?: return Result.failure(Exception("Not signed in"))
            val row = AppDataManager.connections.value.firstOrNull { it.id == connectionId }
                ?: fetchConnectionById(connectionId)
            val pair = row?.user_ids?.filter { it.isNotBlank() }?.distinct()?.takeIf { it.size >= 2 }
                ?: return Result.failure(Exception("Connection not found"))
            if (uid !in pair) {
                return Result.failure(Exception("Connection not found"))
            }
            val ok = supabaseRepository.hideConnectionForUsers(pair, connectionId)
            if (ok) Result.success(Unit) else Result.failure(Exception("Could not hide connection"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}