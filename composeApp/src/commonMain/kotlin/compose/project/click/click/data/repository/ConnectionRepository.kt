package compose.project.click.click.data.repository

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
import compose.project.click.click.data.models.deriveHeightCategory
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
import compose.project.click.click.data.models.User
import compose.project.click.click.qr.CLICK_WEB_BASE_URL
import compose.project.click.click.sensors.HardwareVibeSnapshot
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
 * Result of [ConnectionRepository.createConnection]: [connection] is always persisted (or queued offline);
 * [encounterLogged] is false when the server rejected a new encounter row due to the 3h reconnection cap.
 */
data class ConnectionCreateOutcome(
    val connection: Connection,
    val encounterLogged: Boolean,
)

internal fun Throwable.isRetryableForProximityBind(): Boolean {
    if (this is TimeoutCancellationException) return true
    if (this is CancellationException) return false
    val name = this::class.simpleName.orEmpty()
    if (name.contains("HttpRequestTimeout", ignoreCase = true)) return true
    if (name.contains("ConnectTimeout", ignoreCase = true)) return true
    if (name.contains("UnresolvedAddress", ignoreCase = true)) return true
    if (name.contains("IOException", ignoreCase = true)) return true
    val m = message?.lowercase().orEmpty()
    return m.contains("timeout") ||
        m.contains("timed out") ||
        m.contains("network") ||
        m.contains("socket") ||
        m.contains("unable to resolve") ||
        m.contains("failed to connect") ||
        m.contains("unreachable") ||
        m.contains("offline") ||
        m.contains("connection reset") ||
        m.contains("connection refused") ||
        m.contains("no address associated") ||
        m.contains("host") && m.contains("unreachable")
}

data class PendingProximityHandshakeSyncResult(
    val recoveredUsers: List<User>?,
    val remainingInQueue: Int,
    /** Aggregate from the last successful bind when [recoveredUsers] is non-null. */
    val recoveredEncounterLogged: Boolean = true,
    /** True when the last bind attempt failed due to an auth/session issue (401/403/invalid JWT). */
    val authorizationFailed: Boolean = false,
    /** From [bind-proximity-connection] when the replayed bind was a multi-user cluster (includes self). */
    val groupCliqueCandidateMemberIds: List<String>? = null,
)

data class ProximityHandshakeRecoveryPayload(
    val users: List<User>,
    val encounterLogged: Boolean = true,
    val groupCliqueCandidateMemberIds: List<String>? = null,
)

data class BindProximityHandshakeOutcome(
    val matches: List<User>,
    /**
     * When false, every matched peer who already had a connection hit the 3h encounter rate limit;
     * the client should skip the post-confirm context-tagging sheet for this crossing.
     */
    val encounterLogged: Boolean,
    /**
     * When non-null, the edge clustered ≥3 users in one proximity component; clients may start a
     * verified group flow with these ids (includes the caller).
     */
    val groupCliqueCandidateMemberIds: List<String>? = null,
)

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
private data class GroupCliqueCandidatePayload(
    @SerialName("member_user_ids") val memberUserIds: List<String> = emptyList(),
)

@Serializable
private data class BindProximityResponse(
    val success: Boolean? = true,
    @SerialName("encounter_logged") val encounterLogged: Boolean? = null,
    @SerialName("connection_id") val connectionId: String? = null,
    @SerialName("is_new_connection") val isNewConnection: Boolean? = null,
    val matches: List<User>? = null,
    val error: String? = null,
    @SerialName("group_clique_candidate") val groupCliqueCandidate: GroupCliqueCandidatePayload? = null,
)

@Serializable
private data class BindProximityRequest(
    @SerialName("my_token") val myToken: String,
    @SerialName("heard_tokens") val heardTokens: List<String>,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("exact_barometric_elevation_m") val exactBarometricElevationM: Double? = null,
    @SerialName("noise_level") val noiseLevel: String? = null,
    @SerialName("exact_noise_level_db") val exactNoiseLevelDb: Double? = null,
    @SerialName("context_tags") val contextTags: List<String>? = null,
    @SerialName("height_category") val heightCategory: String? = null,
    @SerialName("lux_level") val luxLevel: Double? = null,
    @SerialName("motion_variance") val motionVariance: Double? = null,
    @SerialName("compass_azimuth") val compassAzimuth: Double? = null,
    @SerialName("battery_level") val batteryLevel: Int? = null,
    /** When true, edge function defers encounter rows until connection create. */
    @SerialName("client_context_first") val clientContextFirst: Boolean? = null,
    @SerialName("weather_snapshot") val weatherSnapshot: String? = null,
)

/**
 * Connection + encounter persistence.
 *
 * **QR (token) flows** must use the Next.js companion host only:
 * `POST {CLICK_WEB_BASE_URL}/api/qr` for redemption — never Supabase Edge `bind-proximity-connection`.
 * Proximity tap / deferred replay is the sole caller of [bindProximityHandshake].
 *
 * Chat **read receipts / inbox unread badges** are handled by the chat layer
 * (`ChatViewModel` + `SupabaseChatRepository.markMessagesAsRead`), not this repository.
 */
class ConnectionRepository(
    private val weatherService: WeatherService = OpenMeteoWeatherService(),
    private val tokenStorage: TokenStorage = createTokenStorage()
) {
    @Serializable
    private data class EncounterIdOnlyRow(val id: String)

    @Serializable
    private data class EncounterPatchRow(
        val id: String,
        @SerialName("context_tags") val contextTags: List<String>? = null,
    )

    private val supabase by lazy { SupabaseConfig.client }
    private val supabaseRepository = SupabaseRepository()
    private val apiClient by lazy { compose.project.click.click.data.api.ApiClient() }

    /**
     * BFF read path (Phase 3 — C15) for the ProfileBottomSheet Media / Files subtabs.
     *
     * Calls `GET /api/connections/{connectionId}/tabs` on click-web, which queries the
     * `public.messages` table for the chat row bound to [connectionId] and returns the
     * `image`/`audio` (media) and `file` message rows. `Links` is deliberately *not*
     * part of this payload: message `content` is E2EE on the wire, so link extraction
     * has to run against the locally-decrypted chat state.
     */
    suspend fun fetchConnectionTabs(
        connectionId: String,
    ): Result<compose.project.click.click.data.api.ConnectionTabsGetResponse> {
        return apiClient.getConnectionTabs(connectionId)
    }
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    /** Next.js companion (`/api/qr`, etc.) — not Supabase Functions. */
    private val companionWebHttpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(this@ConnectionRepository.json)
            }
        }
    }
    /** Supabase Edge Functions only (e.g. `bind-proximity-connection`). */
    private val supabaseFunctionsHttpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(this@ConnectionRepository.json)
            }
        }
    }
    private val connectionsSelectWithEncounters = Columns.raw("*, connection_encounters(*)")

    private companion object {
        const val CONNECTION_TIMEOUT_MS = 15_000L
    }

    private fun Connection.withEncountersSortedNewestFirst(): Connection =
        copy(connectionEncounters = connectionEncounters.sortedByDescending { it.encounteredAt })

    private fun normalizeContextTag(
        contextTagObject: ContextTag?,
        contextTag: String?
    ): ContextTag? {
        return contextTagObject ?: contextTag
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { ContextTagTaxonomy.formatCustomUserContextTag(it) }
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
        httpClient: HttpClient? = null,
        bearerJwt: String,
        myToken: String,
        heardTokens: List<String>,
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
    ): Result<BindProximityHandshakeOutcome> {
        return try {
            val client = httpClient ?: supabaseFunctionsHttpClient
            val hasGps = latitude != null && longitude != null &&
                latitude.isFinite() && longitude.isFinite() &&
                !(latitude == 0.0 && longitude == 0.0)
            val trimmedWeather = weatherSnapshotLabel?.trim()?.takeIf { it.isNotEmpty() }
            val normalizedBindTags = bindContextTags
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.distinct()
                ?.takeIf { it.isNotEmpty() }
            val request = BindProximityRequest(
                myToken = myToken,
                heardTokens = heardTokens,
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
            )
            val response = client.post(SupabaseConfig.functionUrl("bind-proximity-connection")) {
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
            val parsed = runCatching {
                json.decodeFromString(BindProximityResponse.serializer(), text)
            }.getOrElse {
                return Result.failure(Exception("Could not read proximity server response."))
            }
            if (!parsed.error.isNullOrBlank()) {
                return Result.failure(Exception(parsed.error))
            }
            val rows = parsed.matches.orEmpty()
            val aggregateEncounterLogged = parsed.encounterLogged
                ?: rows.none { it.encounterLogged == false }
            val groupCliqueCandidateMemberIds =
                parsed.groupCliqueCandidate?.memberUserIds?.takeIf { it.isNotEmpty() }
            Result.success(
                BindProximityHandshakeOutcome(
                    matches = rows,
                    encounterLogged = aggregateEncounterLogged,
                    groupCliqueCandidateMemberIds = groupCliqueCandidateMemberIds,
                ),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun enqueuePendingProximityHandshake(
        myToken: String,
        heardTokens: List<String>,
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
        val now = Clock.System.now().toEpochMilliseconds()
        val loc = if (latitude != null && longitude != null &&
            latitude.isFinite() && longitude.isFinite() &&
            !(latitude == 0.0 && longitude == 0.0)
        ) {
            ProximityHandshakeLocationSnapshot(
                latitude = latitude,
                longitude = longitude,
                altitudeMeters = altitudeMeters?.takeIf { it.isFinite() },
                capturedAtEpochMs = now,
            )
        } else {
            null
        }
        val draft = PendingHandshake(
            id = newPendingHandshakeId(),
            myToken = myToken.trim(),
            heardTokens = heardTokens.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            capturedAtEpochMs = now,
            location = loc,
            hardwareVibe = hardwareVibe,
            noiseLevel = noiseLevel?.trim()?.takeIf { it.isNotEmpty() },
            exactNoiseLevelDb = exactNoiseLevelDb?.takeIf { it.isFinite() },
            heightCategory = heightCategory?.trim()?.takeIf { it.isNotEmpty() },
            exactBarometricElevationM = exactBarometricElevationM?.takeIf { it.isFinite() },
            contextTags = contextTags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        )
        val q = loadPendingProximityHandshakeQueue().toMutableList()
        val dup = q.lastOrNull {
            it.myToken == draft.myToken &&
                it.heardTokens == draft.heardTokens &&
                (now - it.capturedAtEpochMs) < 60_000L
        }
        if (dup != null) return
        q += draft
        while (q.size > 32) q.removeAt(0)
        savePendingProximityHandshakeQueue(q)
    }

    suspend fun pendingProximityHandshakeQueueSize(): Int =
        loadPendingProximityHandshakeQueue().size

    /**
     * Replays the oldest queued handshake against [bind-proximity-connection].
     * Drops head when the server returns an empty match list (stale tokens).
     */
    suspend fun syncPendingProximityHandshakes(bearerJwt: String): PendingProximityHandshakeSyncResult {
        if (bearerJwt.isBlank()) {
            return PendingProximityHandshakeSyncResult(null, loadPendingProximityHandshakeQueue().size)
        }
        while (true) {
            val queue = loadPendingProximityHandshakeQueue()
            if (queue.isEmpty()) {
                return PendingProximityHandshakeSyncResult(null, 0)
            }
            val head = queue.first()
            val lat = head.location?.latitude
            val lng = head.location?.longitude
            val attempt = runCatching {
                withTimeout(CONNECTION_TIMEOUT_MS + 12_000L) {
                    bindProximityHandshake(
                        httpClient = null,
                        bearerJwt = bearerJwt,
                        myToken = head.myToken,
                        heardTokens = head.heardTokens,
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
                val outcome = attempt.getOrNull()!!
                val users = outcome.matches
                val rest = queue.drop(1)
                savePendingProximityHandshakeQueue(rest)
                if (users.isNotEmpty()) {
                    return PendingProximityHandshakeSyncResult(
                        recoveredUsers = users,
                        remainingInQueue = rest.size,
                        recoveredEncounterLogged = outcome.encounterLogged,
                        groupCliqueCandidateMemberIds = outcome.groupCliqueCandidateMemberIds,
                    )
                }
                continue
            }
            val err = attempt.exceptionOrNull() ?: return PendingProximityHandshakeSyncResult(null, queue.size)
            val authHint = err.message?.lowercase().orEmpty()
            if (authHint.contains("401") || authHint.contains("403") ||
                authHint.contains("unauthorized") || authHint.contains("invalid jwt")
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
            savePendingProximityHandshakeQueue(rest)
        }
    }

    private suspend fun loadPendingProximityHandshakeQueue(): List<PendingHandshake> {
        val raw = tokenStorage.getPendingProximityHandshakeQueue()
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<PendingHandshake>>(raw)
        }.getOrElse {
            println("ConnectionRepository: Failed to decode pending proximity handshake queue: ${it.message}")
            emptyList()
        }
    }

    private suspend fun savePendingProximityHandshakeQueue(items: List<PendingHandshake>) {
        tokenStorage.savePendingProximityHandshakeQueue(
            if (items.isEmpty()) null else json.encodeToString(items),
        )
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

    /** @return true when a new encounter row was inserted; false when rate-limited (chat bumped only). */
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
    private suspend fun mergePatchLatestEncounter(
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
            val latest = supabase.from("connection_encounters")
                .select {
                    filter { eq("connection_id", connectionId) }
                    order("encountered_at", Order.DESCENDING)
                    limit(1)
                }
                .decodeList<EncounterPatchRow>()
                .firstOrNull() ?: return Result.failure(Exception("No encounter row for connection"))

            val normalizedContextTag = normalizeContextTag(contextTagObject = contextTag, contextTag = null)
            val existingTags = latest.contextTags.orEmpty()
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
                noiseLevelCategory?.name?.let { put("noise_level", it) }
                heightCategory?.name?.let { put("elevation_category", it) }
                exactNoiseLevelDb?.takeIf { it.isFinite() }?.let { put("exact_noise_level_db", it) }
                exactBarometricElevationMeters?.takeIf { it.isFinite() }
                    ?.let { put("exact_barometric_elevation_m", it) }
            }
            if (payload.isEmpty()) {
                return Result.success(Unit)
            }
            supabase.from("connection_encounters")
                .update(payload) {
                    filter { eq("id", latest.id) }
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
            mergePatchLatestEncounter(
                connectionId = connectionId,
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

    @Serializable
    private data class RedeemQrTokenResponse(
        val success: Boolean? = null,
        val error: String? = null,
        @SerialName("user_id") val userId: String? = null,
        @SerialName("token_age_ms") val tokenAgeMs: Long? = null,
        @SerialName("distance_meters") val distanceMeters: Int? = null,
        @SerialName("encounter_logged") val encounterLogged: Boolean? = null,
        val reason: String? = null,
        @SerialName("connection_id") val connectionId: String? = null,
        @SerialName("weather_snapshot") val weatherSnapshot: String? = null,
    )

    @Serializable
    private data class QrApiRedeemRequest(
        val token: String,
        @SerialName("gps_lat") val gpsLat: Double? = null,
        @SerialName("gps_lon") val gpsLon: Double? = null,
        @SerialName("lux_level") val luxLevel: Double? = null,
        @SerialName("motion_variance") val motionVariance: Double? = null,
        @SerialName("compass_azimuth") val compassAzimuth: Double? = null,
        @SerialName("battery_level") val batteryLevel: Int? = null,
        @SerialName("weather_snapshot") val weatherSnapshot: String? = null,
        @SerialName("noise_level_category") val noiseLevelCategory: String? = null,
        @SerialName("exact_noise_level_db") val exactNoiseLevelDb: Double? = null,
        @SerialName("height_category") val heightCategory: String? = null,
        @SerialName("elevation_category") val elevationCategory: String? = null,
        @SerialName("exact_barometric_elevation_m") val exactBarometricElevationM: Double? = null,
        @SerialName("context_tags") val contextTags: List<String>? = null,
    )

    @Serializable
    private data class QrApiRedeemData(
        val targetUserId: String? = null,
        val tokenAgeMs: Long? = null,
        val encounterLogged: Boolean? = null,
        val reason: String? = null,
        val connectionId: String? = null,
        val message: String? = null,
        val initiatorId: String? = null,
        val targetUserName: String? = null,
        @SerialName("weather_snapshot") val weatherSnapshot: String? = null,
    )

    @Serializable
    private data class QrApiRedeemEnvelope(
        val success: Boolean? = false,
        val error: String? = null,
        @SerialName("encounter_logged") val encounterLogged: Boolean? = null,
        val reason: String? = null,
        @SerialName("connection_id") val connectionId: String? = null,
        @SerialName("weather_snapshot") val weatherSnapshot: String? = null,
        val data: QrApiRedeemData? = null,
    )

    /**
     * Create a connection between two users with proximity verification.
     *
     * Performs Haversine distance check if both locations are available,
     * computes a proximity confidence score, and stores the signals.
     * The Supabase anomaly detection trigger runs on INSERT.
     */
    suspend fun createConnection(request: ConnectionRequest): Result<ConnectionCreateOutcome> {
        return try {
            val onlineResult = withTimeout(CONNECTION_TIMEOUT_MS) {
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

    private suspend fun createConnectionOnline(request: ConnectionRequest): Result<ConnectionCreateOutcome> {
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

            // Never infer `elevation_category` from GPS `altitudeMeters` — only from barometric elevation we persist.
            val heightCategory = exactBarometricElevationMeters?.let { deriveHeightCategory(it) }

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
    private suspend fun restoreExistingConnection(
        request: ConnectionRequest,
        scannedUserId: String,
        existing: Connection,
        resolvedTokenAgeMs: Long?,
        preflightConnectionId: String?,
        preflightEncounterLogged: Boolean?,
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

            val heightCategory = exactBarometricElevationMeters?.let { deriveHeightCategory(it) }

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
            Result.success(ConnectionCreateOutcome(result, encounterLogged))
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
    private suspend fun findConnectionRowForUserPair(
        userId1: String,
        userId2: String
    ): Connection? {
        return try {
            supabase.from("connections")
                .select(columns = connectionsSelectWithEncounters) {
                    filter {
                        contains("user_ids", listOf(userId1, userId2))
                    }
                }
                .decodeList<Connection>()
                .map { it.withEncountersSortedNewestFirst() }
                .firstOrNull { conn ->
                    userId1 in conn.user_ids && userId2 in conn.user_ids
                }
        } catch (e: Exception) {
            println("Error checking connection: ${e.redactedRestMessage()}")
            null
        }
    }

    private suspend fun fetchConnectionById(connectionId: String): Connection? {
        if (connectionId.isBlank()) return null
        return try {
            supabase.from("connections")
                .select(columns = connectionsSelectWithEncounters) {
                    filter {
                        eq("id", connectionId)
                    }
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
    ): Result<RedeemQrTokenResponse> {
        return try {
            val jwt = tokenStorage.getJwt()?.takeIf { it.isNotBlank() }
                ?: return Result.failure(Exception("Please sign in again."))

            val trimmedWeather = weatherSnapshotLabel?.trim()?.takeIf { it.isNotEmpty() }
            val finiteBaro = exactBarometricElevationM?.takeIf { it.isFinite() }
            val heightWire = heightCategory?.name?.takeIf { finiteBaro != null }
            val requestPayload = QrApiRedeemRequest(
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
                heightCategory = heightWire,
                elevationCategory = heightWire,
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
                json.decodeFromString(QrApiRedeemEnvelope.serializer(), bodyText)
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

            if (targetUserId.isNullOrBlank()) {
                return Result.failure(Exception("Invalid QR code"))
            }

            if (encounterLogged == false && encounterReason == "rate_limit_active") {
                return Result.success(
                    RedeemQrTokenResponse(
                        success = true,
                        userId = targetUserId,
                        tokenAgeMs = tokenAgeMs,
                        encounterLogged = false,
                        reason = encounterReason,
                        connectionId = connectionId,
                    )
                )
            }

            Result.success(
                RedeemQrTokenResponse(
                    success = true,
                    userId = targetUserId,
                    tokenAgeMs = tokenAgeMs,
                    encounterLogged = encounterLogged,
                    reason = encounterReason,
                    connectionId = connectionId,
                )
            )
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
            val ok = supabaseRepository.hideConnectionForUser(uid, connectionId)
            if (ok) {
                AppDataManager.refresh(force = true)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Could not hide connection"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
