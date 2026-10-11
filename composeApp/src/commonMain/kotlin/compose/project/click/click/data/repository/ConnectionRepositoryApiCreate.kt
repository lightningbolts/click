package compose.project.click.click.data.repository

import compose.project.click.click.core.FeatureFlagGate // pragma: allowlist secret
import compose.project.click.click.data.auth.EnsureFreshAccessToken // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.ConnectionRequest // pragma: allowlist secret
import compose.project.click.click.qr.CLICK_WEB_BASE_URL
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetIn
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Kill switch for G4-10: when on, QR/manual connect falls back to the legacy direct PostgREST
 * inserts for one release. Off by default (the API path applies server enrichment).
 */
internal const val CONNECT_POSTGREST_FALLBACK_FLAG: String = "connect_postgrest_fallback"

internal fun useConnectApiPath(request: ConnectionRequest): Boolean =
    request.connectionMethod != "proximity" && !FeatureFlagGate.isEnabled(CONNECT_POSTGREST_FALLBACK_FLAG)

/**
 * `POST /api/connections` body, as iOS `ClickConnectionRedeemer.submit` builds it: the pair,
 * method, token age, this phone's own `location1`, the encounter context and this phone's
 * quality keys + `sensor_observation` ([ConnectionRequest.observationKeys]).
 */
internal fun connectionsCreateBody(
    request: ConnectionRequest,
    scannedUserId: String,
    tokenAgeMs: Long?,
    contextTagId: String?,
    timezoneOffsetMinutes: Int,
): JsonObject =
    buildJsonObject {
        put("userId1", request.userId1)
        put("userId2", scannedUserId)
        put("connectionMethod", request.connectionMethod)
        tokenAgeMs?.let { put("tokenAgeMs", it) }
        val lat = request.locationLat
        val lon = request.locationLng
        // A venue QR resolves its own location server-side; never send this phone's then.
        if (request.venueId.isNullOrBlank() && lat != null && lon != null && lat.isFinite() && lon.isFinite() && !(lat == 0.0 && lon == 0.0)) {
            put(
                "location1",
                buildJsonObject {
                    put("lat", lat)
                    put("lon", lon)
                },
            )
        }
        request.initiatorId?.let { put("initiator_id", it) }
        request.responderId?.let { put("responder_id", it) }
        contextTagId?.trim()?.takeIf { it.isNotEmpty() }?.let { put("context_tags", JsonArray(listOf(JsonPrimitive(it)))) }
        request.noiseLevelCategory?.let { put("noise_level", it.name) }
        request.exactNoiseLevelDb?.takeIf { it.isFinite() }?.let { put("exact_noise_level_db", it) }
        request.exactBarometricElevationMeters?.takeIf { it.isFinite() }?.let { put("exact_barometric_elevation_m", it) }
        request.luxLevel?.takeIf { it.isFinite() }?.let { put("lux_level", it) }
        request.motionVariance?.takeIf { it.isFinite() }?.let { put("motion_variance", it) }
        request.compassAzimuth?.takeIf { it.isFinite() }?.let { put("compass_azimuth", it) }
        request.batteryLevel?.takeIf { it in 0..100 }?.let { put("battery_level", it) }
        request.weatherSnapshotLabel?.trim()?.takeIf { it.isNotEmpty() }?.let { put("weather_snapshot", it) }
        put("timezone_offset_minutes", timezoneOffsetMinutes)
        request.observationKeys?.forEach { (key, value) -> put(key, value) }
    }

/**
 * Creates (or reactivates) the pair through `POST /api/connections` instead of PostgREST, so
 * the server applies `place_id`, the insights opt-in, quality keys, weather and dedupe (G4-10).
 */
internal suspend fun ConnectionRepository.createConnectionViaApi(
    request: ConnectionRequest,
    scannedUserId: String,
    tokenAgeMs: Long?,
): Result<ConnectionCreateOutcome> {
    val jwt =
        EnsureFreshAccessToken.get(tokenStorage)?.takeIf { it.isNotBlank() } // pragma: allowlist secret
            ?: return Result.failure(Exception("Please sign in again."))
    val contextTagId =
        resolveContextTagId(normalizeContextTag(contextTagObject = request.contextTagObject, contextTag = request.contextTag))
    val tzOffset =
        Clock.System
            .now()
            .offsetIn(TimeZone.currentSystemDefault())
            .totalSeconds / 60
    val body = connectionsCreateBody(request, scannedUserId, tokenAgeMs, contextTagId, tzOffset)
    val response =
        companionWebHttpClient.post("$CLICK_WEB_BASE_URL/api/connections") {
            headers { append(HttpHeaders.Authorization, "Bearer $jwt") }
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    val text = response.bodyAsText()
    val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
    if (!response.status.isSuccess()) {
        val error = (root?.get("message") ?: root?.get("error"))?.jsonPrimitive?.contentOrNull
        return Result.failure(Exception(error?.takeIf { it.isNotBlank() } ?: "Failed to create connection"))
    }
    val connectionJson = root?.get("connection") as? JsonObject
    val connectionId =
        root?.get("connection_id")?.jsonPrimitive?.contentOrNull
            ?: connectionJson?.get("id")?.jsonPrimitive?.contentOrNull
            ?: return Result.failure(Exception("Could not read the new connection from the server. Try refreshing your connections."))
    val connection =
        connectionJson?.let { runCatching { json.decodeFromJsonElement(Connection.serializer(), it) }.getOrNull() }
            ?: fetchConnectionById(connectionId)
            ?: return Result.failure(Exception("Could not read the new connection from the server. Try refreshing your connections."))
    return Result.success(
        ConnectionCreateOutcome(
            connection = connection,
            encounterLogged = root?.get("encounter_logged")?.jsonPrimitive?.booleanOrNull ?: true,
            encounterId = root?.get("encounter_id")?.jsonPrimitive?.contentOrNull,
            collaborationTtl = root?.get("collaboration_ttl")?.jsonPrimitive?.contentOrNull,
        ),
    )
}
