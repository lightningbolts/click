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
 * Create a connection between two users with proximity verification.
 *
 * Performs Haversine distance check if both locations are available,
 * computes a proximity confidence score, and stores the signals.
 * The Supabase anomaly detection trigger runs on INSERT.
 */
internal suspend fun ConnectionRepository.createConnectionImpl(request: ConnectionRequest): Result<ConnectionCreateOutcome> {
    return try {
        val onlineResult = withTimeout(ConnectionRepository.CONNECTION_TIMEOUT_MS) {
            createConnectionOnline(request)
        }
        if (onlineResult.isSuccess) {
            onlineResult
        } else {
            val error = onlineResult.exceptionOrNull()
            if (shouldQueueOffline(request, error)) {
                val queued = queuePendingConnection(request)
                Result.success(ConnectionCreateOutcome(queued, encounterLogged = true))
            } else {
                onlineResult
            }
        }
    } catch (e: Exception) {
        if (shouldQueueOffline(request, e)) {
            val queued = queuePendingConnection(request)
            Result.success(ConnectionCreateOutcome(queued, encounterLogged = true))
        } else {
            Result.failure(e)
        }
    }
}

internal suspend fun ConnectionRepository.createConnectionOnline(request: ConnectionRequest): Result<ConnectionCreateOutcome> {
    return try {
        val redeemedToken = if (!request.qrToken.isNullOrBlank() && !request.skipQrTokenRedeem) {
            val sendScannerGps = request.venueId.isNullOrBlank()
            val redeemContextTag = normalizeContextTag(
                contextTagObject = request.contextTagObject,
                contextTag = request.contextTag,
            )
            val redeemTagLine = resolveContextTagId(redeemContextTag)?.trim()?.takeIf { it.isNotEmpty() }
            redeemQrToken(
                token = request.qrToken,
                scannerLat = if (sendScannerGps) request.locationLat else null,
                scannerLon = if (sendScannerGps) request.locationLng else null,
                luxLevel = request.luxLevel,
                motionVariance = request.motionVariance,
                compassAzimuth = request.compassAzimuth,
                batteryLevel = request.batteryLevel,
                weatherSnapshotLabel = request.weatherSnapshotLabel,
                noiseLevelCategory = request.noiseLevelCategory,
                exactNoiseLevelDb = request.exactNoiseLevelDb,
                heightCategory = request.heightCategory,
                exactBarometricElevationM = request.exactBarometricElevationMeters,
                contextTags = redeemTagLine?.let { listOf(it) },
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
        val preflightConnectionId = redeemedToken?.connectionId ?: request.preflightConnectionId
        val preflightEncounterLogged = redeemedToken?.encounterLogged ?: request.preflightEncounterLogged
        val preflightEncounterId = redeemedToken?.encounterId
        val preflightCollaborationTtl = redeemedToken?.collaborationTtl

        // Prefer server-issued connection id from proximity bind before any insert.
        val byPreflightId = preflightConnectionId
            ?.takeIf { it.isNotBlank() }
            ?.let { fetchConnectionById(it) }
        if (byPreflightId != null) {
            return restoreExistingConnection(
                request = request,
                scannedUserId = scannedUserId,
                existing = byPreflightId,
                resolvedTokenAgeMs = resolvedTokenAgeMs,
                preflightConnectionId = preflightConnectionId,
                preflightEncounterLogged = preflightEncounterLogged,
                preflightEncounterId = preflightEncounterId,
                preflightCollaborationTtl = preflightCollaborationTtl,
            )
        }

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
                preflightConnectionId = preflightConnectionId,
                preflightEncounterLogged = preflightEncounterLogged,
                preflightEncounterId = preflightEncounterId,
                preflightCollaborationTtl = preflightCollaborationTtl,
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
            request.luxLevel?.takeIf { it.isFinite() }?.let { put("lux_level", it) }
            request.motionVariance?.takeIf { it.isFinite() }?.let { put("motion_variance", it) }
            request.compassAzimuth?.takeIf { it.isFinite() }?.let { put("compass_azimuth", it) }
            request.batteryLevel?.takeIf { it in 0..100 }?.let { put("battery_level", it) }
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

        val result = try {
            supabase.from("connections")
                .insert(connectionInsert) {
                    select()
                }
                .decodeSingle<Connection>()
        } catch (e: SerializationException) {
            return Result.failure(
                Exception("Could not read the new connection from the server. Try refreshing your connections."),
            )
        } catch (e: Exception) {
            if (e.isDuplicateConnectionKeyError()) {
                val raced = findConnectionRowForUserPair(request.userId1, scannedUserId)
                    ?: preflightConnectionId?.takeIf { it.isNotBlank() }?.let { fetchConnectionById(it) }
                if (raced != null) {
                    return restoreExistingConnection(
                        request = request,
                        scannedUserId = scannedUserId,
                        existing = raced,
                        resolvedTokenAgeMs = resolvedTokenAgeMs,
                        preflightConnectionId = preflightConnectionId,
                        preflightEncounterLogged = preflightEncounterLogged,
                        preflightEncounterId = preflightEncounterId,
                        preflightCollaborationTtl = preflightCollaborationTtl,
                    )
                }
            }
            return Result.failure(e)
        }

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
                println("ConnectionRepository: Failed to resolve semantic location: ${e.redactedRestMessage()}")
            }
        }

        val clientWeatherLabel = request.weatherSnapshotLabel?.trim()?.takeIf { it.isNotEmpty() }
        val weatherSnapshot = when {
            clientWeatherLabel != null -> null
            loc1Valid -> weatherService.fetchWeather(request.locationLat!!, request.locationLng!!)
            else -> null
        }

        val exactBarometricElevationMeters = calibrateBarometricElevationMeters(
            stationPressureHpa = request.exactBarometricPressureHpa,
            seaLevelPressureHpa = weatherSnapshot?.pressureMslHpa
        )?.takeIf { it.isFinite() }
            ?: request.exactBarometricElevationMeters?.takeIf { it.isFinite() }

        // elevation_category is derived server-side from relative_altitude_m (AGL = AMSL − DEM).
        // Never persist AMSL-based categories from the client.
        val heightCategory: HeightCategory? = null

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
            println("ConnectionRepository: Failed to update connection metadata: ${e.redactedRestMessage()}")
        }

        val encounterAlreadyHandled = request.connectionMethod == "qr" &&
            preflightConnectionId != null &&
            preflightConnectionId == result.id &&
            preflightEncounterLogged != null
        val encounterLogged = when {
            request.skipEncounterInsert -> true
            encounterAlreadyHandled -> preflightEncounterLogged!!
            else -> try {
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
                    exactNoiseLevelDb = request.exactNoiseLevelDb,
                    exactBarometricElevationM = exactBarometricElevationMeters,
                    luxLevel = request.luxLevel,
                    motionVariance = request.motionVariance,
                    compassAzimuth = request.compassAzimuth,
                    batteryLevel = request.batteryLevel,
                    weatherSnapshotLabel = clientWeatherLabel,
                )
            } catch (e: Exception) {
                println("ConnectionRepository: encounter insert: ${e.redactedRestMessage()}")
                true
            }
        }

        if (encounterAlreadyHandled && request.connectionMethod == "qr") {
            mergePatchLatestEncounter(
                connectionId = result.id,
                reportingUserId = request.userId1,
                contextTag = normalizeContextTag(
                    contextTagObject = request.contextTagObject,
                    contextTag = request.contextTag,
                ),
                noiseLevelCategory = request.noiseLevelCategory,
                exactNoiseLevelDb = request.exactNoiseLevelDb,
                heightCategory = heightCategory,
                exactBarometricElevationMeters = exactBarometricElevationMeters,
            ).exceptionOrNull()?.let {
                println("ConnectionRepository: post-redeem encounter merge (new row): ${it.message}")
            }
        }

        // Create chat row for this connection
        try {
            supabase.from("chats")
                .insert(buildJsonObject {
                    put("connection_id", result.id)
                    put("created_at", now)
                    put("updated_at", now)
                })
        } catch (e: Exception) {
            println("ConnectionRepository: Failed to create chat: ${e.redactedRestMessage()}")
            // Non-fatal — connection was created
        }

        Result.success(ConnectionCreateOutcome(result, encounterLogged))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Reconnect: clear per-user junction rows for both participants, reset lifecycle fields, refresh metadata.
 */
internal suspend fun ConnectionRepository.restoreExistingConnection(
    request: ConnectionRequest,
    scannedUserId: String,
    existing: Connection,
    resolvedTokenAgeMs: Long?,
    preflightConnectionId: String?,
    preflightEncounterLogged: Boolean?,
    preflightEncounterId: String? = null,
    preflightCollaborationTtl: String? = null,
): Result<ConnectionCreateOutcome> {
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
            request.luxLevel?.takeIf { it.isFinite() }?.let { put("lux_level", it) }
            request.motionVariance?.takeIf { it.isFinite() }?.let { put("motion_variance", it) }
            request.compassAzimuth?.takeIf { it.isFinite() }?.let { put("compass_azimuth", it) }
            request.batteryLevel?.takeIf { it in 0..100 }?.let { put("battery_level", it) }
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
                println("ConnectionRepository: Failed to resolve semantic location: ${e.redactedRestMessage()}")
            }
        }

        val clientWeatherLabelRestore = request.weatherSnapshotLabel?.trim()?.takeIf { it.isNotEmpty() }
        val weatherSnapshot = when {
            clientWeatherLabelRestore != null -> null
            loc1Valid -> weatherService.fetchWeather(request.locationLat!!, request.locationLng!!)
            else -> null
        }

        val exactBarometricElevationMeters = calibrateBarometricElevationMeters(
            stationPressureHpa = request.exactBarometricPressureHpa,
            seaLevelPressureHpa = weatherSnapshot?.pressureMslHpa,
        )?.takeIf { it.isFinite() }
            ?: request.exactBarometricElevationMeters?.takeIf { it.isFinite() }

        // elevation_category is derived server-side from relative_altitude_m (AGL).
        val heightCategory: HeightCategory? = null

        val result = try {
            supabase.from("connections")
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
        } catch (e: SerializationException) {
            return Result.failure(
                Exception("Could not read the restored connection from the server. Try refreshing your connections."),
            )
        }

        val restoreTags = listOfNotNull(contextTagId?.trim()?.takeIf { it.isNotEmpty() })
        val encounterAlreadyHandled = request.connectionMethod == "qr" &&
            preflightConnectionId != null &&
            preflightConnectionId == result.id &&
            preflightEncounterLogged != null
        val encounterLogged = when {
            request.skipEncounterInsert -> true
            encounterAlreadyHandled -> preflightEncounterLogged!!
            else -> try {
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
                    exactNoiseLevelDb = request.exactNoiseLevelDb,
                    exactBarometricElevationM = exactBarometricElevationMeters,
                    luxLevel = request.luxLevel,
                    motionVariance = request.motionVariance,
                    compassAzimuth = request.compassAzimuth,
                    batteryLevel = request.batteryLevel,
                    weatherSnapshotLabel = clientWeatherLabelRestore,
                )
            } catch (e: Exception) {
                println("ConnectionRepository: restore encounter insert: ${e.redactedRestMessage()}")
                true
            }
        }

        if (encounterAlreadyHandled && request.connectionMethod == "qr") {
            mergePatchLatestEncounter(
                connectionId = result.id,
                reportingUserId = request.userId1,
                contextTag = normalizeContextTag(
                    contextTagObject = request.contextTagObject,
                    contextTag = request.contextTag,
                ),
                noiseLevelCategory = request.noiseLevelCategory,
                exactNoiseLevelDb = request.exactNoiseLevelDb,
                heightCategory = heightCategory,
                exactBarometricElevationMeters = exactBarometricElevationMeters,
            ).exceptionOrNull()?.let {
                println("ConnectionRepository: post-redeem encounter merge: ${it.message}")
            }
        }

        try {
            val existingChat = supabase.from("chats")
                .select(columns = Columns.list("id")) {
                    filter {
                        eq("connection_id", result.id)
                    }
                    limit(1)
                }
                .decodeList<Chat>()
                .firstOrNull()

            if (existingChat == null) {
                supabase.from("chats")
                    .insert(buildJsonObject {
                        put("connection_id", result.id)
                        put("created_at", now)
                        put("updated_at", now)
                    })
            }
        } catch (e: Exception) {
            println("ConnectionRepository: Failed to ensure chat on restore: ${e.redactedRestMessage()}")
        }

        AppDataManager.applyRestoredConnection(result)
        val rollEncounterId = preflightEncounterId?.trim()?.takeIf { it.isNotEmpty() }
        val rollCollaborationTtl = preflightCollaborationTtl?.trim()?.takeIf { it.isNotEmpty() }
        Result.success(
            ConnectionCreateOutcome(
                connection = result,
                encounterLogged = encounterLogged,
                encounterId = rollEncounterId,
                collaborationTtl = rollCollaborationTtl,
            ),
        )
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal suspend fun ConnectionRepository.redeemQrToken(
    token: String,
    scannerLat: Double? = null,
    scannerLon: Double? = null,
    luxLevel: Double? = null,
    motionVariance: Double? = null,
    compassAzimuth: Double? = null,
    batteryLevel: Int? = null,
    weatherSnapshotLabel: String? = null,
    noiseLevelCategory: NoiseLevelCategory? = null,
    exactNoiseLevelDb: Double? = null,
    heightCategory: HeightCategory? = null,
    exactBarometricElevationM: Double? = null,
    contextTags: List<String>? = null,
): Result<ConnectionRepository.RedeemQrTokenResponse> {
    return try {
        val jwt = tokenStorage.getJwt()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("Please sign in again."))

        val trimmedWeather = weatherSnapshotLabel?.trim()?.takeIf { it.isNotEmpty() }
        val finiteBaro = exactBarometricElevationM?.takeIf { it.isFinite() }
        // Omit AMSL-derived height_category; QR route derives elevation_category from AGL after DEM.
        val requestPayload = ConnectionRepository.QrApiRedeemRequest(
            token = token,
            gpsLat = scannerLat?.takeIf { it.isFinite() && it != 0.0 },
            gpsLon = scannerLon?.takeIf { it.isFinite() && it != 0.0 },
            luxLevel = luxLevel?.takeIf { it.isFinite() },
            motionVariance = motionVariance?.takeIf { it.isFinite() },
            compassAzimuth = compassAzimuth?.takeIf { it.isFinite() },
            batteryLevel = batteryLevel?.takeIf { it in 0..100 },
            weatherSnapshot = trimmedWeather,
            noiseLevelCategory = noiseLevelCategory?.name,
            exactNoiseLevelDb = exactNoiseLevelDb?.takeIf { it.isFinite() },
            heightCategory = null,
            elevationCategory = null,
            exactBarometricElevationM = finiteBaro,
            contextTags = contextTags?.filter { it.isNotBlank() }?.distinct()?.takeIf { it.isNotEmpty() },
        )

        val response = companionWebHttpClient.post("$CLICK_WEB_BASE_URL/api/qr") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $jwt")
            }
            contentType(ContentType.Application.Json)
            setBody(requestPayload)
        }

        val bodyText = response.bodyAsText()
        val envelope = runCatching {
            json.decodeFromString(ConnectionRepository.QrApiRedeemEnvelope.serializer(), bodyText)
        }.getOrNull()

        if (!response.status.isSuccess()) {
            val errorCode = envelope?.error
            val message = when (errorCode) {
                "proximity_failed" -> "Connection failed: Users must be in the same physical location."
                "expired" -> "This QR code has expired. Ask them to generate a new one."
                "already_used" -> "This QR code was already used. Ask them to generate a new one."
                "not_found" -> "This QR code is no longer valid. Ask them to generate a new one."
                else -> envelope?.error?.takeIf { it.isNotBlank() } ?: "Failed to redeem QR code"
            }
            return Result.failure(Exception(message))
        }

        if (envelope == null) {
            return Result.failure(Exception("Failed to parse QR API response"))
        }

        if (envelope.success != true) {
            val message = when (envelope.error) {
                "proximity_failed" -> "Connection failed: Users must be in the same physical location."
                "expired" -> "This QR code has expired. Ask them to generate a new one."
                "already_used" -> "This QR code was already used. Ask them to generate a new one."
                "not_found" -> "This QR code is no longer valid. Ask them to generate a new one."
                else -> "Failed to redeem QR code"
            }
            return Result.failure(Exception(message))
        }

        val targetUserId = envelope.data?.targetUserId
        val tokenAgeMs = envelope.data?.tokenAgeMs
        val encounterLogged = envelope.data?.encounterLogged ?: envelope.encounterLogged
        val encounterReason = envelope.data?.reason ?: envelope.reason
        val connectionId = envelope.data?.connectionId ?: envelope.connectionId
        val encounterId = envelope.data?.encounterId ?: envelope.encounterId
        val collaborationTtl = envelope.data?.collaborationTtl ?: envelope.collaborationTtl

        if (targetUserId.isNullOrBlank()) {
            return Result.failure(Exception("Invalid QR code"))
        }

        if (encounterLogged == false && encounterReason == "rate_limit_active") {
            return Result.success(
                ConnectionRepository.RedeemQrTokenResponse(
                    success = true,
                    userId = targetUserId,
                    tokenAgeMs = tokenAgeMs,
                    encounterLogged = false,
                    reason = encounterReason,
                    connectionId = connectionId,
                    encounterId = encounterId,
                    collaborationTtl = collaborationTtl,
                )
            )
        }

        Result.success(
            ConnectionRepository.RedeemQrTokenResponse(
                success = true,
                userId = targetUserId,
                tokenAgeMs = tokenAgeMs,
                encounterLogged = encounterLogged,
                reason = encounterReason,
                connectionId = connectionId,
                encounterId = encounterId,
                collaborationTtl = collaborationTtl,
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }
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
internal fun ConnectionRepository.computeProximityScore(
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
internal suspend fun ConnectionRepository.resolveSemanticLocation(lat: Double, lon: Double): Pair<String, Map<String, String>>? {
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

        val houseNum = addressMap["house_number"]?.trim()?.takeIf { it.isNotEmpty() }
        val roadOnly = addressMap["road"]?.trim()?.takeIf { it.isNotEmpty() }
        val houseAndRoad = if (houseNum != null && roadOnly != null) "$houseNum $roadOnly" else null

        // Use a short display name: prefer house+road, then building/amenity, then road area, then display_name
        val shortName = houseAndRoad
            ?: addressMap["building"]
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
        println("ConnectionRepository: Nominatim reverse geocode failed: ${e.redactedRestMessage()}")
        null
    }
}

/**
 * Any connection row for the unordered user pair (including removed/archived lifecycle).
 */
internal suspend fun ConnectionRepository.findConnectionRowForUserPair(
    userId1: String,
    userId2: String
): Connection? {
    return try {
        supabase.from("connections")
            .select(columns = connectionsSelectWithEncounters) {
                filter {
                    contains("user_ids", listOf(userId1, userId2))
                }
                order("encountered_at", Order.DESCENDING, referencedTable = connectionEncountersTable)
                limit(connectionEncountersPerConnection, referencedTable = connectionEncountersTable)
            }
            .decodeList<Connection>()
            .map { it.withEncountersSortedNewestFirst() }
            .firstOrNull { conn ->
                conn.user_ids.size == 2 &&
                    userId1 in conn.user_ids &&
                    userId2 in conn.user_ids
            }
    } catch (e: Exception) {
        println("Error checking connection: ${e.redactedRestMessage()}")
        null
    }
}

internal suspend fun ConnectionRepository.fetchConnectionById(connectionId: String): Connection? {
    if (connectionId.isBlank()) return null
    return try {
        supabase.from("connections")
            .select(columns = connectionsSelectWithEncounters) {
                filter {
                    eq("id", connectionId)
                }
                order("encountered_at", Order.DESCENDING, referencedTable = connectionEncountersTable)
                limit(connectionEncountersPerConnection, referencedTable = connectionEncountersTable)
                limit(1)
            }
            .decodeList<Connection>()
            .map { it.withEncountersSortedNewestFirst() }
            .firstOrNull()
    } catch (e: Exception) {
        println("ConnectionRepository: fetchConnectionById failed: ${e.redactedRestMessage()}")
        null
    }
}
