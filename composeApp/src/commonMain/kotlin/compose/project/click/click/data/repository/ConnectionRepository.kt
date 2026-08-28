@file:Suppress("ktlint:standard:no-wildcard-imports")

package compose.project.click.click.data.repository

import compose.project.click.click.collaboration.CollaborationSession
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.ContextTagTaxonomy
import compose.project.click.click.data.OpenMeteoWeatherService
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.WeatherService
import compose.project.click.click.data.api.ClickWebRequestException
import compose.project.click.click.data.api.CollaborationSessionPostResponse
import compose.project.click.click.data.api.ProximityBindOkResponseDto
import compose.project.click.click.data.auth.EnsureFreshAccessToken // pragma: allowlist secret
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.ConnectionActivityStatus
import compose.project.click.click.data.models.ConnectionRequest
import compose.project.click.click.data.models.ContextTag
import compose.project.click.click.data.models.HeightCategory
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.NoiseLevelCategory
import compose.project.click.click.data.models.PollPairSuggestion
import compose.project.click.click.data.models.ReconnectHelper
import compose.project.click.click.data.models.User
import compose.project.click.click.data.models.mergeRichestEncounterEvents
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.data.storage.createTokenStorage
import compose.project.click.click.encounter.PendingEncounterQueue
import compose.project.click.click.sensors.HardwareVibeSnapshot
import compose.project.click.click.util.redactedRestMessage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.*

/**
 * Result of [ConnectionRepository.createConnection]: [connection] is always persisted (or queued offline);
 * [encounterLogged] is false when the server rejected a new encounter row due to the 3h reconnection cap.
 */
data class ConnectionCreateOutcome(
    val connection: Connection,
    val encounterLogged: Boolean,
    val encounterId: String? = null,
    val collaborationTtl: String? = null,
)

internal fun Throwable.isRetryableForProximityBind(): Boolean {
    if (this is TimeoutCancellationException) return true
    if (this is CancellationException) return false
    // 503 with pending_handshake_id is handled as PendingMatch in ApiClient; bare 503 still queues.
    if (this is ClickWebRequestException && statusCode == 503) return true
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
        m.contains("host") &&
        m.contains("unreachable") ||
        m.contains("connection_unavailable") ||
        m.contains("pairwise_connection_unavailable") ||
        m.contains("service unavailable")
}

internal fun Throwable.isDuplicateConnectionKeyError(): Boolean {
    val m = message?.lowercase().orEmpty()
    return m.contains("duplicate key") ||
        m.contains("unique_user_pair") ||
        m.contains("unique constraint") ||
        m.contains("23505")
}

data class PendingProximityHandshakeSyncResult(
    val recoveredUsers: List<User>?,
    val remainingInQueue: Int,
    /** Aggregate from the last successful bind when [recoveredUsers] is non-null. */
    val recoveredEncounterLogged: Boolean = true,
    /** True when the last bind attempt failed due to an auth/session issue (401/403/invalid JWT). */
    val authorizationFailed: Boolean = false,
    /** From proximity bind when the replayed bind was a multi-user cluster (includes self). */
    val groupCliqueCandidateMemberIds: List<String>? = null,
    /** True when the server accepted the handshake but no peer is online yet (HTTP 202). */
    val serverPendingMatch: Boolean = false,
    /**
     * When set, recovered peers came from `awaiting_selection` and host must
     * [confirmProximitySelection] before create — do not treat as an already-created clique.
     */
    val pendingHandshakeId: String? = null,
    val isAggregateNewConnection: Boolean = true,
)

data class ProximityHandshakeRecoveryPayload(
    val users: List<User>,
    val encounterLogged: Boolean = true,
    val groupCliqueCandidateMemberIds: List<String>? = null,
    /** Present when recovery landed on `awaiting_selection` (host confirm still required). */
    val pendingHandshakeId: String? = null,
    val isAggregateNewConnection: Boolean = true,
)

data class BindProximityHandshakeOutcome(
    val matches: List<User>,
    /** Re-engagement Disposable Roll session (existing-friend bump). */
    val encounterId: String? = null,
    val collaborationTtl: String? = null,
    /**
     * Top-level `is_new_connection` from proximity bind when present; else derived from match rows.
     * When false, route reconnect UX (encounter logging) instead of the “new spark” sheet.
     */
    val isAggregateNewConnection: Boolean = true,
    /**
     * When false, every matched peer who already had a connection hit the 3h encounter rate limit;
     * the client should skip the post-confirm context-tagging sheet for this crossing.
     */
    val encounterLogged: Boolean,
    /**
     * When non-null, the server clustered ≥3 users in one proximity component; clients may start a
     * verified group flow with these ids (includes the caller).
     */
    val groupCliqueCandidateMemberIds: List<String>? = null,
    val connectionId: String? = null,
    val isGroup: Boolean = false,
)

/** Result of `POST /api/connections/proximity` — instant match vs async pending. */
sealed class BindProximityHandshakeResult {
    data class InstantMatch(
        val outcome: BindProximityHandshakeOutcome,
    ) : BindProximityHandshakeResult()

    /**
     * Multi-peer (≥3) first-time bind: server returned candidates; host must
     * [ConnectionRepository.confirmProximitySelection] before a connection is created.
     */
    data class AwaitingHostSelection(
        val pendingHandshakeId: String,
        val expiresAt: String?,
        val candidates: List<User>,
        val isAggregateNewConnection: Boolean = true,
        val groupCliqueCandidateMemberIds: List<String>? = null,
    ) : BindProximityHandshakeResult()

    data class PendingServerMatch(
        val pendingHandshakeId: String,
        val expiresAt: String,
    ) : BindProximityHandshakeResult()
}

/** Mirrors click-web `PROXIMITY_HOST_SELECTION_MAX_MEMBERS` (host + selected peers). */
const val PROXIMITY_HOST_SELECTION_MAX_MEMBERS = 12

/** Max peer ids the host may select (server adds the caller to form the member set). */
const val PROXIMITY_HOST_SELECTION_MAX_PEERS = PROXIMITY_HOST_SELECTION_MAX_MEMBERS - 1

internal fun ProximityBindOkResponseDto.toBindOutcome(): BindProximityHandshakeOutcome {
    val rows = matches.orEmpty()
    val aggregateEncounterLogged =
        encounterLogged
            ?: rows.none { it.encounterLogged == false }
    val aggregateNewConnection =
        isNewConnection
            ?: rows.any { it.isNewConnection }
    val groupCliqueCandidateMemberIds =
        groupCliqueCandidate?.memberUserIds?.takeIf { it.isNotEmpty() }
    return BindProximityHandshakeOutcome(
        matches = rows,
        isAggregateNewConnection = aggregateNewConnection,
        encounterLogged = aggregateEncounterLogged,
        groupCliqueCandidateMemberIds = groupCliqueCandidateMemberIds,
        connectionId = connectionId,
        isGroup = isGroup == true,
        encounterId = encounterId?.trim()?.takeIf { it.isNotEmpty() },
        collaborationTtl = collaborationTtl?.trim()?.takeIf { it.isNotEmpty() },
    )
}

internal fun ProximityBindOkResponseDto.toAwaitingHostSelectionOrNull(): BindProximityHandshakeResult.AwaitingHostSelection? {
    if (awaitingSelection != true) return null
    val pendingId = pendingHandshakeId?.trim().orEmpty()
    if (pendingId.isEmpty()) return null
    val rows = matches.orEmpty()
    if (rows.isEmpty()) return null
    val aggregateNewConnection =
        isNewConnection
            ?: rows.any { it.isNewConnection }
    return BindProximityHandshakeResult.AwaitingHostSelection(
        pendingHandshakeId = pendingId,
        expiresAt = expiresAt?.trim()?.takeIf { it.isNotEmpty() },
        candidates = rows,
        isAggregateNewConnection = aggregateNewConnection,
        groupCliqueCandidateMemberIds = groupCliqueCandidate?.memberUserIds?.takeIf { it.isNotEmpty() },
    )
}

internal fun buildUtcTimeOfDayLabel(epochMillis: Long): String {
    val utcTime =
        Clock.System
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
    data class Success(
        val connection: Connection,
    ) : ProximityResult()

    data class ProximityRejected(
        val distance: Int,
    ) : ProximityResult()

    data class Error(
        val message: String,
    ) : ProximityResult()
}

/**
 * Connection + encounter persistence.
 *
 * **QR (token) flows** must use the Next.js companion host only:
 * `POST {CLICK_WEB_BASE_URL}/api/qr` for redemption.
 * Proximity tap / deferred replay uses `POST /api/connections/proximity` via [bindProximityHandshake].
 *
 * Chat **read receipts / inbox unread badges** are handled by the chat layer
 * (`ChatViewModel` + `SupabaseChatRepository.markMessagesAsRead`), not this repository.
 */
class ConnectionRepository(
    internal val weatherService: WeatherService = OpenMeteoWeatherService(),
    internal val tokenStorage: TokenStorage = createTokenStorage(),
) {
    internal val authRepository = AuthRepository(tokenStorage = tokenStorage)

    @Serializable
    internal data class EncounterIdOnlyRow(
        val id: String,
    )

    @Serializable
    internal data class EncounterPatchRow(
        val id: String,
        @SerialName("encountered_at") val encounteredAt: String? = null,
        @SerialName("context_tags") val contextTags: List<String>? = null,
        @SerialName("reporting_user_id") val reportingUserId: String? = null,
    )

    internal val supabase by lazy { SupabaseConfig.client }
    internal val supabaseRepository = SupabaseRepository()
    internal val apiClient by lazy {
        compose.project.click.click.data.api
            .ApiClient()
    }
    internal val chatRepository: ChatRepository by lazy {
        SupabaseChatRepository(tokenStorage = tokenStorage)
    }

    /**
     * BFF read path (Phase 3 — C15) for the ProfileBottomSheet Media / Files subtabs.
     *
     * Calls `GET /api/connections/{connectionId}/tabs` on click-web, which queries the
     * `public.messages` table for the chat row bound to [connectionId] and returns the
     * `image`/`audio` (media) and `file` message rows. `Links` is deliberately *not*
     * part of this payload: message `content` is E2EE on the wire, so link extraction
     * has to run against the locally-decrypted chat state.
     */
    suspend fun fetchConnectionTabs(connectionId: String): Result<compose.project.click.click.data.api.ConnectionTabsGetResponse> {
        // Profile media tabs share the click-web bearer path — refresh before first call so a
        // stale cold-start JWT does not permanently empty the Media tab until sign-out.
        runCatching { authRepository.refreshSession() }
        val first = apiClient.getConnectionTabs(connectionId)
        if (first.isSuccess) return first
        runCatching { authRepository.refreshSession() }
        return apiClient.getConnectionTabs(connectionId)
    }

    /** POST `/api/connections/encounter` on click-web (JWT via ApiClient). */
    suspend fun postConnectionEncounter(
        userId: String,
        peerId: String,
        sensorData: kotlinx.serialization.json.JsonObject?,
    ): Result<Unit> {
        val uid = userId.trim()
        val pid = peerId.trim()
        if (uid.isEmpty() || pid.isEmpty()) {
            return Result.failure(IllegalArgumentException("user_id and peer_id required"))
        }
        return apiClient.postConnectionEncounter(
            compose.project.click.click.data.api.ConnectionEncounterPostBody(
                userId = uid,
                peerId = pid,
                sensorData = sensorData,
            ),
        )
    }

    /** POST `/api/connections/{id}/collaboration-session` — opens Disposable Roll for any bump. */
    suspend fun openCollaborationSession(connectionId: String): Result<CollaborationSession> {
        val cid = connectionId.trim()
        if (cid.isEmpty()) {
            return Result.failure(IllegalArgumentException("Invalid connection"))
        }
        val responseResult = openCollaborationSessionResponse(cid)
        return responseResult.mapCatching { response ->
            val encounterId =
                response.encounterId?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalStateException("Missing encounter id")
            val ttl =
                response.collaborationTtl?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalStateException("Missing collaboration window")
            CollaborationSession(
                encounterId = encounterId,
                connectionId = cid,
                collaborationTtlIso = ttl,
            )
        }
    }

    /** POST `/api/chats/{id}/collaboration-session` — opens Disposable Roll for a group chat. */
    suspend fun openCollaborationSessionForChat(chatId: String): Result<CollaborationSession> {
        val cid = chatId.trim()
        if (cid.isEmpty()) {
            return Result.failure(IllegalArgumentException("Invalid chat"))
        }
        return apiClient.postOpenCollaborationSessionForChat(cid).mapCatching { response ->
            val encounterId =
                response.encounterId?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalStateException("Missing encounter id")
            val ttl =
                response.collaborationTtl?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalStateException("Missing collaboration window")
            CollaborationSession(
                encounterId = encounterId,
                connectionId = "",
                chatId = cid,
                collaborationTtlIso = ttl,
            )
        }
    }

    internal suspend fun openCollaborationSessionResponse(connectionId: String): Result<CollaborationSessionPostResponse> {
        val primary = apiClient.postOpenCollaborationSession(connectionId)
        if (primary.isSuccess) return primary
        val status = (primary.exceptionOrNull() as? ClickWebRequestException)?.statusCode
        if (status == 404) {
            return apiClient.postOpenCollaborationSessionFallback(connectionId)
        }
        return primary
    }

    suspend fun getProfileTabs(userId: String): Result<compose.project.click.click.data.api.ConnectionTabsGetResponse> =
        apiClient.getConnectionTabs(userId)

    /**
     * Loads decrypted chat messages for a profile sheet by [connectionId] without requiring the
     * active ChatView state. Prefers repository fetch (full history) and falls back to the local
     * in-memory connection snapshot when no route could be resolved.
     */
    suspend fun fetchDecryptedMessagesForProfileConnection(
        connectionId: String,
        viewerUserId: String?,
    ): List<Message> {
        val cid = connectionId.trim()
        if (cid.isBlank()) return emptyList()

        val cachedConnection = AppDataManager.connections.value.firstOrNull { it.id == cid }
        val cachedMessages = cachedConnection?.chat?.messages.orEmpty()
        val cachedChatId = cachedConnection?.chat?.id
        val cachedThread = AppDataManager.cachedChatThreadFor(cid)
        if (cachedThread != null && cachedThread.messages.isNotEmpty()) {
            return cachedThread.messages
        }

        val chatId =
            runCatching {
                chatRepository.resolveChatIdForConnection(cid)
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: cachedChatId

        if (chatId.isNullOrBlank()) return cachedMessages

        val fetched =
            runCatching {
                chatRepository.fetchMessagesForChat(chatId, viewerUserId).orEmpty()
            }.getOrDefault(emptyList())

        if (fetched.isEmpty()) return cachedMessages
        val uid = viewerUserId?.trim().orEmpty()
        return if (uid.isNotBlank()) {
            chatRepository.vaultEncryptedMediaMessages(chatId, uid, fetched)
        } else {
            fetched
        }
    }

    suspend fun fetchDecryptedMessagesForChat(
        chatId: String,
        viewerUserId: String?,
    ): List<Message> {
        val cid = chatId.trim()
        if (cid.isBlank()) return emptyList()
        val cachedThread = AppDataManager.cachedChatThreadFor(cid)
        if (cachedThread != null && cachedThread.messages.isNotEmpty()) {
            return cachedThread.messages
        }
        val fetched =
            runCatching {
                chatRepository.fetchMessagesForChat(cid, viewerUserId).orEmpty()
            }.getOrDefault(emptyList())
        val uid = viewerUserId?.trim().orEmpty()
        return if (uid.isNotBlank() && fetched.isNotEmpty()) {
            chatRepository.vaultEncryptedMediaMessages(cid, uid, fetched)
        } else {
            fetched
        }
    }

    /**
     * Downloads and decrypts an attachment payload from a `ccx:v1` envelope tuple.
     */
    suspend fun downloadAttachmentPlaintext(
        path: String,
        fileMasterKeyBase64: String,
        expectedSha256Base64: String,
    ): ByteArray? {
        val p = path.trim()
        val k = fileMasterKeyBase64.trim()
        val s = expectedSha256Base64.trim()
        if (p.isBlank() || k.isBlank() || s.isBlank()) return null
        return runCatching {
            chatRepository.downloadAttachmentPlaintext(
                path = p,
                fileMasterKeyBase64 = k,
                expectedSha256Base64 = s,
            )
        }.getOrNull()
    }

    /** Mint a short-lived signed URL for a chat attachment object [path]. */
    suspend fun getSignedChatAttachmentUrl(path: String): String? {
        val p = path.trim()
        if (p.isBlank()) return null
        return runCatching {
            apiClient.getSignedChatAttachmentUrl(p).getOrNull()
        }.getOrNull()
    }

    /** Download and decrypt encrypted media bytes for profile media tiles. */
    suspend fun downloadAndDecryptChatMedia(
        chatId: String,
        viewerUserId: String,
        mediaUrl: String,
    ): ByteArray? {
        val cid = chatId.trim()
        val uid = viewerUserId.trim()
        val url = mediaUrl.trim()
        if (cid.isBlank() || uid.isBlank() || url.isBlank()) return null
        return runCatching {
            chatRepository.downloadAndDecryptChatMedia(cid, uid, url)
        }.getOrNull()
    }

    internal val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    internal val pendingEncounterQueue by lazy { PendingEncounterQueue(tokenStorage, json) }

    /** Next.js companion (`/api/qr`, `/api/connections/proximity`, etc.). */
    internal val companionWebHttpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(this@ConnectionRepository.json)
            }
        }
    }
    internal val connectionsSelectWithEncounters = Columns.raw("*, connection_encounters(*)")
    internal val connectionEncountersPerConnection = 25L
    internal val connectionEncountersTable = "connection_encounters"

    internal companion object {
        const val CONNECTION_TIMEOUT_MS = 15_000L
        const val ACTIVE_ENCOUNTER_CONTEXT_PATCH_WINDOW_MS = 30L * 60L * 1000L
        const val ACTIVE_ENCOUNTER_FUTURE_SKEW_MS = 2L * 60L * 1000L
    }

    internal fun Connection.withEncountersSortedNewestFirst(): Connection =
        copy(connectionEncounters = connectionEncounters.mergeRichestEncounterEvents().sortedByDescending { it.encounteredAt })

    internal fun Throwable.isClickWebAuthFailure(): Boolean {
        if (this is ClickWebRequestException && statusCode in setOf(401, 403)) return true
        val msg = redactedRestMessage().lowercase()
        return msg.contains("401") ||
            msg.contains("403") ||
            msg.contains("unauthorized") ||
            msg.contains("invalid jwt")
    }

    internal suspend fun refreshedJwtAfterAuthFailure(): String? {
        authRepository
            .refreshSession(forceRefresh = true)
            .onFailure { println("ConnectionRepository: token refresh failed: ${it.redactedRestMessage()}") }
        return EnsureFreshAccessToken.get(tokenStorage, authRepository, forceRefresh = false) // pragma: allowlist secret
    }

    internal fun normalizeContextTag(
        contextTagObject: ContextTag?,
        contextTag: String?,
    ): ContextTag? =
        contextTagObject ?: contextTag
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { ContextTagTaxonomy.formatCustomUserContextTag(it) }

    internal fun resolveContextTagId(contextTag: ContextTag?): String? =
        when {
            contextTag == null -> null
            contextTag.id == "custom" -> contextTag.label
            else -> contextTag.id
        }

    suspend fun prewarmBindProximityConnection(
        httpClient: HttpClient? = null,
        bearerJwt: String,
    ) = prewarmBindProximityConnectionImpl(httpClient = httpClient, bearerJwt = bearerJwt)

    suspend fun bindProximityHandshake(
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
    ): Result<BindProximityHandshakeResult> =
        bindProximityHandshakeImpl(
            httpClient = httpClient,
            bearerJwt = bearerJwt,
            myToken = myToken,
            heardTokens = heardTokens,
            detectedDevices = detectedDevices,
            latitude = latitude,
            longitude = longitude,
            exactBarometricElevationM = exactBarometricElevationM,
            hardwareVibe = hardwareVibe,
            clientContextFirst = clientContextFirst,
            weatherSnapshotLabel = weatherSnapshotLabel,
            bindContextTags = bindContextTags,
            bindNoiseLevelCategory = bindNoiseLevelCategory,
            bindExactNoiseLevelDb = bindExactNoiseLevelDb,
            bindHeightCategory = bindHeightCategory,
            simulatorMock = simulatorMock,
        )

    suspend fun recoverPendingProximityHandshake(
        bearerJwt: String,
        pendingHandshakeId: String,
    ): Result<BindProximityHandshakeResult> =
        recoverPendingProximityHandshakeImpl(bearerJwt = bearerJwt, pendingHandshakeId = pendingHandshakeId)

    suspend fun confirmProximitySelection(
        pendingHandshakeId: String,
        selectedMemberIds: List<String>,
        contextTags: List<String>? = null,
        bearerJwt: String? = null,
    ): Result<BindProximityHandshakeOutcome> =
        confirmProximitySelectionImpl(
            pendingHandshakeId = pendingHandshakeId,
            selectedMemberIds = selectedMemberIds,
            contextTags = contextTags,
            bearerJwt = bearerJwt,
        )

    suspend fun enqueuePendingProximityHandshake(
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
    ) = enqueuePendingProximityHandshakeImpl(
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

    suspend fun pendingProximityHandshakeQueueSize(): Int = pendingProximityHandshakeQueueSizeImpl()

    suspend fun syncPendingProximityHandshakes(bearerJwt: String): PendingProximityHandshakeSyncResult =
        syncPendingProximityHandshakesImpl(bearerJwt = bearerJwt)

    suspend fun updateConnectionTags(
        connectionId: String,
        reportingUserId: String? = null,
        contextTag: ContextTag?,
        noiseLevelCategory: NoiseLevelCategory?,
        exactNoiseLevelDb: Double?,
        heightCategory: HeightCategory?,
        exactBarometricElevationMeters: Double?,
    ): Result<Unit> =
        updateConnectionTagsImpl(
            connectionId = connectionId,
            reportingUserId = reportingUserId,
            contextTag = contextTag,
            noiseLevelCategory = noiseLevelCategory,
            exactNoiseLevelDb = exactNoiseLevelDb,
            heightCategory = heightCategory,
            exactBarometricElevationMeters = exactBarometricElevationMeters,
        )

    @Serializable
    internal data class RedeemQrTokenResponse(
        val success: Boolean? = null,
        val error: String? = null,
        @SerialName("user_id") val userId: String? = null,
        @SerialName("token_age_ms") val tokenAgeMs: Long? = null,
        @SerialName("distance_meters") val distanceMeters: Int? = null,
        @SerialName("encounter_logged") val encounterLogged: Boolean? = null,
        val reason: String? = null,
        @SerialName("connection_id") val connectionId: String? = null,
        @SerialName("encounter_id") val encounterId: String? = null,
        @SerialName("collaboration_ttl") val collaborationTtl: String? = null,
        @SerialName("weather_snapshot") val weatherSnapshot: String? = null,
    )

    @Serializable
    internal data class QrApiRedeemRequest(
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
    internal data class QrApiRedeemData(
        val targetUserId: String? = null,
        val tokenAgeMs: Long? = null,
        val encounterLogged: Boolean? = null,
        val reason: String? = null,
        val connectionId: String? = null,
        val encounterId: String? = null,
        val collaborationTtl: String? = null,
        val message: String? = null,
        val initiatorId: String? = null,
        val targetUserName: String? = null,
        @SerialName("weather_snapshot") val weatherSnapshot: String? = null,
    )

    @Serializable
    internal data class QrApiRedeemEnvelope(
        val success: Boolean? = false,
        val error: String? = null,
        @SerialName("encounter_logged") val encounterLogged: Boolean? = null,
        val reason: String? = null,
        @SerialName("connection_id") val connectionId: String? = null,
        @SerialName("encounter_id") val encounterId: String? = null,
        @SerialName("collaboration_ttl") val collaborationTtl: String? = null,
        @SerialName("weather_snapshot") val weatherSnapshot: String? = null,
        val data: QrApiRedeemData? = null,
    )

    suspend fun createConnection(request: ConnectionRequest): Result<ConnectionCreateOutcome> = createConnectionImpl(request = request)

    suspend fun syncPendingConnections(): Int = syncPendingConnectionsImpl()

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
        connectedUsers: Map<String, User>,
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
            }.sortedBy { it.third }
            .firstOrNull()
            ?.let { (connection, otherUserId, lastInteraction) ->
                val otherName = connectedUsers[otherUserId]?.name
                PollPairSuggestion(
                    connectionId = connection.id,
                    otherUserId = otherUserId,
                    otherUserName = otherName,
                    lastInteractionAt = lastInteraction,
                    daysSinceContact = ReconnectHelper.getDaysSinceContact(lastInteraction),
                    contextTag = connection.context_tag,
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
    suspend fun getUserConnections(userId: String): Result<List<Connection>> =
        try {
            val snapshot = supabaseRepository.fetchUserConnectionsSnapshot(userId)
            val visible = snapshot.connections.filter { it.isVisibleInActiveUi() }
            Result.success(visible)
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * Get user by ID
     */
    suspend fun getUserById(userId: String): Result<User> {
        return try {
            val user =
                supabaseRepository.fetchUsersByIds(listOf(userId)).firstOrNull()
                    ?: return Result.failure(Exception("User not found"))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Hide a connection for the signed-in user ([connection_hidden]); does not alter [connections] rows.
     */
    suspend fun deleteConnection(connectionId: String): Result<Unit> {
        return try {
            val uid =
                SupabaseConfig.client.auth
                    .currentUserOrNull()
                    ?.id
                    ?.takeIf { it.isNotBlank() }
                    ?: return Result.failure(Exception("Not signed in"))
            val row =
                AppDataManager.connections.value.firstOrNull { it.id == connectionId }
                    ?: fetchConnectionById(connectionId)
            val pair =
                row
                    ?.user_ids
                    ?.filter { it.isNotBlank() }
                    ?.distinct()
                    ?.takeIf { it.size >= 2 }
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
