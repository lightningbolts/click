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

internal suspend fun ConnectionRepository.bumpChatUpdatedAt(connectionId: String, atMs: Long) {
    try {
        supabase.from("chats")
            .update(buildJsonObject { put("updated_at", atMs) }) {
                filter { eq("connection_id", connectionId) }
            }
    } catch (_: Exception) {
        // Non-fatal
    }
}

/** @return true when a new encounter row was inserted; false when rate-limited (chat bumped only). */
internal suspend fun ConnectionRepository.insertConnectionEncounter(
    connectionId: String,
    encounteredAtMs: Long,
    locationName: String?,
    lat: Double?,
    lon: Double?,
    weather: WeatherSnapshot?,
    contextTags: List<String>,
    noiseLevel: String?,
    elevationCategory: String?,
    exactNoiseLevelDb: Double? = null,
    exactBarometricElevationM: Double? = null,
    luxLevel: Double? = null,
    motionVariance: Double? = null,
    compassAzimuth: Double? = null,
    batteryLevel: Int? = null,
    weatherSnapshotLabel: String? = null,
): Boolean {
    val trimmedLabel = weatherSnapshotLabel?.trim()?.takeIf { it.isNotEmpty() }
    val payload = buildJsonObject {
        put("connection_id", connectionId)
        put("encountered_at", Instant.fromEpochMilliseconds(encounteredAtMs).toString())
        locationName?.trim()?.takeIf { it.isNotEmpty() }?.let { put("location_name", it) }
        if (lat != null && lon != null && lat.isFinite() && lon.isFinite() && !(lat == 0.0 && lon == 0.0)) {
            put("gps_lat", lat)
            put("gps_lon", lon)
        }
        when {
            trimmedLabel != null -> {
                val asElement = runCatching { json.parseToJsonElement(trimmedLabel) }.getOrNull()
                if (asElement != null && asElement !is JsonNull) {
                    put("weather_snapshot", asElement)
                } else {
                    put("weather_snapshot", JsonPrimitive(trimmedLabel))
                }
            }
            weather != null ->
                put("weather_snapshot", json.encodeToJsonElement(WeatherSnapshot.serializer(), weather))
        }
        noiseLevel?.trim()?.takeIf { it.isNotEmpty() }?.let { put("noise_level", it) }
        elevationCategory?.trim()?.takeIf { it.isNotEmpty() }?.let { put("elevation_category", it) }
        exactNoiseLevelDb?.takeIf { it.isFinite() }?.let { put("exact_noise_level_db", it) }
        exactBarometricElevationM?.takeIf { it.isFinite() }?.let { put("exact_barometric_elevation_m", it) }
        luxLevel?.takeIf { it.isFinite() }?.let { put("lux_level", it) }
        motionVariance?.takeIf { it.isFinite() }?.let { put("motion_variance", it) }
        compassAzimuth?.takeIf { it.isFinite() }?.let { put("compass_azimuth", it) }
        batteryLevel?.takeIf { it in 0..100 }?.let { put("battery_level", it) }
        put("context_tags", JsonArray(contextTags.map { JsonPrimitive(it) }))
    }
    return try {
        supabase.from("connection_encounters").insert(payload)
        true
    } catch (e: RestException) {
        val msg = e.message ?: e.toString()
        if (msg.contains("encounter_rate_limit_3h", ignoreCase = true)) {
            bumpChatUpdatedAt(connectionId, encounteredAtMs)
            false
        } else {
            throw e
        }
    } catch (e: Exception) {
        val msg = e.message ?: ""
        if (msg.contains("encounter_rate_limit_3h", ignoreCase = true)) {
            bumpChatUpdatedAt(connectionId, encounteredAtMs)
            false
        } else {
            throw e
        }
    }
}

/**
 * Merges [contextTag] into existing `context_tags` (deduped, order preserved) and applies
 * optional sensor columns on the latest [connection_encounters] row for [connectionId].
 */
internal suspend fun ConnectionRepository.mergePatchLatestEncounter(
    connectionId: String,
    reportingUserId: String?,
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
        val latestRows = supabase.from("connection_encounters")
            .select {
                filter { eq("connection_id", connectionId) }
                order("encountered_at", Order.DESCENDING)
                limit(25)
            }
            .decodeList<ConnectionRepository.EncounterPatchRow>()
        if (latestRows.isEmpty()) {
            return Result.failure(Exception("No encounter row for connection"))
        }
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val patchableRows = latestRows.filter { row ->
            val encounteredAtMs = row.encounteredAt
                ?.let { raw -> runCatching { Instant.parse(raw).toEpochMilliseconds() }.getOrNull() }
            encounteredAtMs != null &&
                encounteredAtMs >= nowMs - ConnectionRepository.ACTIVE_ENCOUNTER_CONTEXT_PATCH_WINDOW_MS &&
                encounteredAtMs <= nowMs + ConnectionRepository.ACTIVE_ENCOUNTER_FUTURE_SKEW_MS
        }
        if (patchableRows.isEmpty()) {
            return Result.failure(Exception("No active encounter row for this save"))
        }

        val normalizedContextTag = normalizeContextTag(contextTagObject = contextTag, contextTag = null)
        val existingTags = patchableRows.flatMap { it.contextTags.orEmpty() }.distinct()
        val tagId = resolveContextTagId(normalizedContextTag)?.trim()?.takeIf { it.isNotEmpty() }
        val tagAddsNew = tagId != null && !existingTags.contains(tagId)
        val mergedTags = if (tagAddsNew) {
            existingTags + tagId!!
        } else {
            existingTags
        }

        val payload = buildJsonObject {
            if (tagAddsNew) {
                put("context_tags", JsonArray(mergedTags.map { JsonPrimitive(it) }))
            }
        }
        val telemetryPayload = buildJsonObject {
            noiseLevelCategory?.name?.let { put("noise_level", it) }
            heightCategory?.name?.let { put("elevation_category", it) }
            exactNoiseLevelDb?.takeIf { it.isFinite() }?.let { put("exact_noise_level_db", it) }
            exactBarometricElevationMeters?.takeIf { it.isFinite() }
                ?.let { put("exact_barometric_elevation_m", it) }
        }
        if (payload.isEmpty() && telemetryPayload.isEmpty()) {
            return Result.success(Unit)
        }
        for (latest in patchableRows) {
            val rowPayload = buildJsonObject {
                payload.forEach { (key, value) -> put(key, value) }
                val ownsRow = reportingUserId.isNullOrBlank() ||
                    latest.reportingUserId == null ||
                    latest.reportingUserId == reportingUserId
                if (ownsRow) {
                    telemetryPayload.forEach { (key, value) -> put(key, value) }
                }
            }
            if (rowPayload.isEmpty()) continue
            supabase.from("connection_encounters")
                .update(rowPayload) {
                    filter { eq("id", latest.id) }
                }
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Persists subjective encounter context on the **latest** [connection_encounters] row
 * (proximity fan-out / tag sheet after a crossing).
 */
internal suspend fun ConnectionRepository.updateConnectionTagsImpl(
    connectionId: String,
    reportingUserId: String? = null,
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
        mergePatchLatestEncounter(
            connectionId = connectionId,
            reportingUserId = reportingUserId,
            contextTag = contextTag,
            noiseLevelCategory = noiseLevelCategory,
            exactNoiseLevelDb = exactNoiseLevelDb,
            heightCategory = heightCategory,
            exactBarometricElevationMeters = exactBarometricElevationMeters,
        )
    } catch (e: Exception) {
        Result.failure(e)
    }
}
