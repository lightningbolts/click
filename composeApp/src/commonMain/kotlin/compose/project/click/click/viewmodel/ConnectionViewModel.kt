@file:Suppress("ktlint:standard:no-wildcard-imports", "ktlint:standard:backing-property-naming")

package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.collaboration.CollaborationSession // pragma: allowlist secret
import compose.project.click.click.collaboration.CollaborationSessionManager // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.WeatherService // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.ContextTag // pragma: allowlist secret
import compose.project.click.click.data.models.HeightCategory // pragma: allowlist secret
import compose.project.click.click.data.models.NoiseLevelCategory // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.UserProfile // pragma: allowlist secret
import compose.project.click.click.data.models.isActiveForUser // pragma: allowlist secret
import compose.project.click.click.data.models.toUserProfile // pragma: allowlist secret
import compose.project.click.click.data.repository.BindProximityHandshakeOutcome // pragma: allowlist secret
import compose.project.click.click.data.repository.ConnectionCreateOutcome // pragma: allowlist secret
import compose.project.click.click.data.repository.ConnectionRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.ProximityHandshakeRecoveryPayload // pragma: allowlist secret
import compose.project.click.click.proximity.ProximityManager // pragma: allowlist secret
import compose.project.click.click.sensors.AmbientNoiseMonitor // pragma: allowlist secret
import compose.project.click.click.sensors.BarometricHeightMonitor // pragma: allowlist secret
import compose.project.click.click.sensors.ConnectionSensorContext // pragma: allowlist secret
import compose.project.click.click.sensors.HardwareVibeSnapshot // pragma: allowlist secret
import compose.project.click.click.telemetry.ConnectionFlowTelemetry
import compose.project.click.click.utils.LocationService // pragma: allowlist secret
import io.ktor.client.HttpClient // pragma: allowlist secret
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Emitted when a proximity bind returns a multi-user verified-clique cluster from the edge function. */
data class VerifiedCliqueProximityIntent(
    val preselectFriendIds: List<String>,
    val matchedUsers: List<User>,
)

internal fun reconnectEncounterPeersNeedingInsert(
    targetUsers: List<UserProfile>,
    currentUserId: String,
    bindEncounterPersistedPeerIds: Set<String>,
): List<UserProfile> {
    if (currentUserId.isBlank()) return emptyList()
    return targetUsers
        .filter { it.id.isNotBlank() && it.id != currentUserId }
        .distinctBy { it.id }
        .filterNot { bindEncounterPersistedPeerIds.contains(it.id) }
}

sealed class ConnectionState {
    object Idle : ConnectionState()

    object Loading : ConnectionState()

    data class Success(
        val connection: Connection,
        val connectedUser: User,
    ) : ConnectionState()

    data class Error(
        val message: String,
    ) : ConnectionState()

    object ProximityFetchingLocation : ConnectionState()

    object ProximityHandshaking : ConnectionState()

    data class PendingConfirmation(
        val users: List<User>,
    ) : ConnectionState()

    /**
     * Tri-factor tokens are stored locally; `POST /api/connections/proximity` will run when online again.
     */
    data class ProximityCapturedOfflineSyncing(
        val message: String = "Handshake saved offline. Will sync when connected.",
    ) : ConnectionState()

    /**
     * Server accepted the handshake (HTTP 202) but the peer is not online yet for instant match.
     */
    data class ProximityHandshakePendingMatch(
        val message: String = "Handshake saved! Waiting for the other user to come online...",
    ) : ConnectionState()

    /**
     * Proximity group (or single peer) connections are created; user is adding subjective context tags
     * to be fanned out to every [newConnections] row.
     *
     * When [requiresSelection] is true (multi-peer `awaiting_selection` or legacy PendingConfirmation),
     * [selectableUsers] / [pendingHandshakeId] feed the combined people + tags sheet before create.
     */
    data class TaggingContext(
        val newConnections: List<Connection>,
        val targetUsers: List<UserProfile>,
        val isGroup: Boolean = false,
        val memberUserIds: List<String> = emptyList(),
        val bindEncounterPersistedPeerIds: Set<String> = emptySet(),
        /** From bind-proximity `is_new_connection` aggregate — false → encounter-log UX. */
        val isNewConnection: Boolean = true,
        /** True while POST `/api/connections/encounter` is in flight. */
        val encounterSubmitting: Boolean = false,
        /** Candidates for host multi-peer selection (people + tags sheet). */
        val selectableUsers: List<UserProfile> = emptyList(),
        /** Pending handshake id when server returned `awaiting_selection`. */
        val pendingHandshakeId: String? = null,
        /** When true, host must pick peers (and optionally tags) before connection create. */
        val requiresSelection: Boolean = false,
        /** Currently selected peer ids when [requiresSelection] is true. */
        val selectedPeerIds: Set<String> = emptySet(),
    ) : ConnectionState()

    /** QR parsed locally; user fills context before any redeem/create network work. */
    data class QrAwaitingContext(
        val scannedUserId: String,
        val qrToken: String?,
        val venueId: String?,
        val targetUsers: List<UserProfile>,
    ) : ConnectionState()

    /** Bind in flight after handshake; avoids [Loading] so the NFC sheet stays responsive. */
    object ProximityResolving : ConnectionState()

    object SecuringConnection : ConnectionState()
}

class ConnectionViewModel : ViewModel() {
    internal val repository = ConnectionRepository()

    internal val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    companion object {
        const val RECONNECTION_ENCOUNTER_COOLDOWN_MESSAGE: String =
            "You recently crossed paths with this person! Wait a bit before logging another memory."
        const val HARDWARE_PERMISSIONS_MISSING_MESSAGE: String =
            "Hardware Permissions Missing: enable Bluetooth and Microphone access to use Tap to Connect."
        const val PROXIMITY_PENDING_MATCH_MESSAGE: String =
            "Handshake saved! Waiting for the other user to come online..."
        const val PROXIMITY_OFFLINE_SYNC_MESSAGE: String =
            "Handshake saved offline. Will sync when connected."
        internal const val SIMULATOR_MOCK_MY_TOKEN: String = "1234"
        internal val SIMULATOR_MOCK_HEARD_TOKENS: List<String> = listOf("5678")
        internal const val PENDING_MATCH_RECOVERY_ATTEMPTS: Int = 12
        internal const val PENDING_MATCH_RECOVERY_DELAY_MS: Long = 2_500L
        internal const val TAP_PROXIMITY_DEBOUNCE_MS: Long = 12_000L
    }

    internal val _transientNotice = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val transientNotice: SharedFlow<String> = _transientNotice.asSharedFlow()

    internal val _verifiedCliqueFromProximity = MutableSharedFlow<VerifiedCliqueProximityIntent>(extraBufferCapacity = 1)
    val verifiedCliqueFromProximity: SharedFlow<VerifiedCliqueProximityIntent> =
        _verifiedCliqueFromProximity.asSharedFlow()

    /**
     * Aggregate from the last proximity bind response (or deferred sync recovery).
     * Used for diagnostics; per-edge encounter logging is taken from each [ConnectionRepository.createConnection] result.
     */
    internal var lastProximityEncounterLoggedAggregate: Boolean = true

    internal var lastProximityLat: Double? = null
    internal var lastProximityLng: Double? = null
    internal var lastProximityAltitudeMeters: Double? = null
    internal var lastProximityHardwareVibe: HardwareVibeSnapshot? = null
    internal var lastTapProximityStartedAtMs: Long = 0L

    fun lastProximityCoordinates(): Pair<Double?, Double?> = lastProximityLat to lastProximityLng

    internal fun isProximityHandshakeInFlight(): Boolean =
        when (_connectionState.value) {
            is ConnectionState.ProximityFetchingLocation,
            is ConnectionState.ProximityHandshaking,
            is ConnectionState.ProximityResolving,
            is ConnectionState.ProximityHandshakePendingMatch,
            is ConnectionState.Loading,
            is ConnectionState.SecuringConnection,
            is ConnectionState.PendingConfirmation,
            is ConnectionState.TaggingContext,
            -> true
            else -> false
        }

    internal suspend fun <T> Deferred<T>.awaitWithin(timeoutMs: Long): T? = withTimeoutOrNull(timeoutMs) { await() }

    internal fun activateCollaborationSessionIfPresent(
        connectionId: String?,
        encounterId: String?,
        collaborationTtl: String?,
    ) {
        val cid = connectionId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val eid = encounterId?.trim()?.takeIf { it.isNotEmpty() }
        val ttl = collaborationTtl?.trim()?.takeIf { it.isNotEmpty() }
        if (eid != null && ttl != null) {
            CollaborationSessionManager.activate(
                CollaborationSession(
                    encounterId = eid,
                    connectionId = cid,
                    collaborationTtlIso = ttl,
                ),
            )
            return
        }
        viewModelScope.launch {
            if (CollaborationSessionManager.forConnection(cid) != null) return@launch
            repository.openCollaborationSession(cid).onSuccess { session ->
                CollaborationSessionManager.activate(session)
            }
        }
    }

    internal fun activateCollaborationSessionIfPresent(outcome: BindProximityHandshakeOutcome) {
        activateCollaborationSessionIfPresent(
            connectionId = outcome.connectionId,
            encounterId = outcome.encounterId,
            collaborationTtl = outcome.collaborationTtl,
        )
    }

    internal fun activateCollaborationSessionIfPresent(outcome: ConnectionCreateOutcome) {
        activateCollaborationSessionIfPresent(
            connectionId = outcome.connection.id,
            encounterId = outcome.encounterId,
            collaborationTtl = outcome.collaborationTtl,
        )
    }

    /**
     * After a valid QR payload is read, show the context sheet before redeem/create.
     */
    fun presentQrContextSheetFromScan(
        scannedUserId: String,
        qrToken: String?,
        venueId: String?,
    ) {
        if (scannedUserId.isBlank()) return
        viewModelScope.launch {
            val profile =
                repository.getUserById(scannedUserId).getOrNull()?.toUserProfile()
                    ?: UserProfile(id = scannedUserId, displayName = "Connection")
            _connectionState.value =
                ConnectionState.QrAwaitingContext(
                    scannedUserId = scannedUserId,
                    qrToken = qrToken,
                    venueId = venueId?.takeIf { it.isNotBlank() },
                    targetUsers = listOf(profile),
                )
        }
    }

    /**
     * Shared connection rows from [AppDataManager] (`MutableStateFlow` backed).
     * Screens such as [compose.project.click.click.ui.screens.ConnectionsScreen] use [ChatViewModel.chatListState]
     * for chat previews and **inbox unread badges**; those counts live on `ChatWithDetails.unreadCount` in
     * [ChatViewModel.chatListState], not on [Connection]. Use this flow when you only need raw [Connection] rows.
     * Excludes server-archived and removed rows so counts match the active map/home surfaces.
     */
    val userConnections: StateFlow<List<Connection>> =
        combine(
            AppDataManager.connections,
            AppDataManager.archivedConnectionIds,
            AppDataManager.hiddenConnectionIds,
        ) { connections, archived, hidden ->
            connections.filter { it.isActiveForUser(archived, hidden) }
        }.distinctUntilChanged()
            // R1.5: SharingStarted.WhileSubscribed keeps the combine() alive only while a UI
            // collector is active, with a 5s grace window to survive configuration changes and
            // brief navigation transitions. This prevents this ViewModel from holding references
            // to AppDataManager's realtime flows indefinitely after the user leaves screens that
            // surface connections (reducing leaks during logout/login cycles).
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Alias for callers that expect a `connections` name (same backing flow as [userConnections]). */
    val connections: StateFlow<List<Connection>> = userConnections

    /** Call when starting a connection so UI can show loading before network work begins. */
    fun markConnecting() {
        _connectionState.value = ConnectionState.Loading
    }

    /** Wake `POST /api/connections/proximity` when the connect screen appears (cold-start prewarm). */
    fun prewarmBindProximityEdgeFunction(
        httpClient: HttpClient,
        jwt: String,
    ) {
        if (jwt.isBlank()) return
        viewModelScope.launch(Dispatchers.Default) {
            repository.prewarmBindProximityConnection(httpClient = httpClient, bearerJwt = jwt)
        }
    }

    fun showHardwarePermissionsMissing() {
        _connectionState.value = ConnectionState.Error(HARDWARE_PERMISSIONS_MISSING_MESSAGE)
    }

    fun startTapProximityHandshake(
        httpClient: HttpClient,
        proximityManager: ProximityManager,
        jwt: String,
        currentUserId: String,
        locationService: LocationService,
        skipLocation: Boolean,
        ambientNoiseMonitor: AmbientNoiseMonitor? = null,
        barometricHeightMonitor: BarometricHeightMonitor? = null,
        weatherService: WeatherService? = null,
    ) = startTapProximityHandshakeImpl(
        httpClient = httpClient,
        proximityManager = proximityManager,
        jwt = jwt,
        currentUserId = currentUserId,
        locationService = locationService,
        skipLocation = skipLocation,
        ambientNoiseMonitor = ambientNoiseMonitor,
        barometricHeightMonitor = barometricHeightMonitor,
        weatherService = weatherService,
    )

    fun onProximityHandshakeRecoveredFromBackground(
        payload: ProximityHandshakeRecoveryPayload,
        currentUserId: String,
    ) = onProximityHandshakeRecoveredFromBackgroundImpl(payload = payload, currentUserId = currentUserId)

    fun tryFlushPendingProximityHandshakes(
        jwt: String,
        currentUserId: String,
    ) = tryFlushPendingProximityHandshakesImpl(jwt = jwt, currentUserId = currentUserId)

    fun connectWithUser(
        scannedUserId: String,
        currentUserId: String,
        latitude: Double? = null,
        longitude: Double? = null,
        venueId: String? = null,
        altitudeMeters: Double? = null,
        heightCategory: HeightCategory? = null,
        exactBarometricElevationMeters: Double? = null,
        exactBarometricPressureHpa: Double? = null,
        contextTag: String? = null,
        contextTagObject: ContextTag? = null,
        connectionMethod: String = "qr",
        tokenAgeMs: Long? = null,
        qrToken: String? = null,
        noiseLevelCategory: NoiseLevelCategory? = null,
        exactNoiseLevelDb: Double? = null,
        initiatorId: String? = null,
        responderId: String? = null,
        hardwareVibeOverride: HardwareVibeSnapshot? = null,
        weatherSnapshotLabel: String? = null,
    ) = connectWithUserImpl(
        scannedUserId = scannedUserId,
        currentUserId = currentUserId,
        latitude = latitude,
        longitude = longitude,
        venueId = venueId,
        altitudeMeters = altitudeMeters,
        heightCategory = heightCategory,
        exactBarometricElevationMeters = exactBarometricElevationMeters,
        exactBarometricPressureHpa = exactBarometricPressureHpa,
        contextTag = contextTag,
        contextTagObject = contextTagObject,
        connectionMethod = connectionMethod,
        tokenAgeMs = tokenAgeMs,
        qrToken = qrToken,
        noiseLevelCategory = noiseLevelCategory,
        exactNoiseLevelDb = exactNoiseLevelDb,
        initiatorId = initiatorId,
        responderId = responderId,
        hardwareVibeOverride = hardwareVibeOverride,
        weatherSnapshotLabel = weatherSnapshotLabel,
    )

    fun resetConnectionState() {
        val tagging = _connectionState.value as? ConnectionState.TaggingContext
        if (tagging?.requiresSelection == true) {
            ConnectionFlowTelemetry.recordHostSelectionAbandoned(
                candidateCount =
                    tagging.selectableUsers.size.takeIf { it > 0 }
                        ?: tagging.targetUsers.size,
                reason = "dismissed",
            )
        }
        lastProximityEncounterLoggedAggregate = true
        lastProximityHardwareVibe = null
        _connectionState.value = ConnectionState.Idle
    }

    /** Ensures a server-backed collaboration session exists for Disposable Roll. */
    suspend fun ensureCollaborationSessionReady(connectionId: String): Result<CollaborationSession> {
        val cid = connectionId.trim()
        if (cid.isEmpty()) {
            return Result.failure(IllegalArgumentException("Invalid connection"))
        }
        CollaborationSessionManager.forConnection(cid)?.let { return Result.success(it) }
        return repository.openCollaborationSession(cid).onSuccess { session ->
            CollaborationSessionManager.activate(session)
        }
    }

    suspend fun ensureCollaborationSessionReadyForChat(chatId: String): Result<CollaborationSession> {
        val cid = chatId.trim()
        if (cid.isEmpty()) {
            return Result.failure(IllegalArgumentException("Invalid chat"))
        }
        CollaborationSessionManager.forChat(cid)?.let { return Result.success(it) }
        return repository.openCollaborationSessionForChat(cid).onSuccess { session ->
            CollaborationSessionManager.activate(session)
        }
    }

    fun saveReconnectEncounter(
        tagging: ConnectionState.TaggingContext,
        currentUserId: String,
        ambientNoiseMonitor: AmbientNoiseMonitor? = null,
        barometricHeightMonitor: BarometricHeightMonitor? = null,
        ambientNoiseOptIn: Boolean = false,
        barometricContextOptIn: Boolean = false,
    ) = saveReconnectEncounterImpl(
        tagging = tagging,
        currentUserId = currentUserId,
        ambientNoiseMonitor = ambientNoiseMonitor,
        barometricHeightMonitor = barometricHeightMonitor,
        ambientNoiseOptIn = ambientNoiseOptIn,
        barometricContextOptIn = barometricContextOptIn,
    )

    fun confirmProximityConnection(
        peerUsers: List<User>,
        currentUserId: String,
        hardwareVibe: HardwareVibeSnapshot? = null,
        weatherSnapshotLabel: String? = null,
        sensorContext: ConnectionSensorContext? = null,
    ) = confirmProximityConnectionImpl(
        peerUsers = peerUsers,
        currentUserId = currentUserId,
        hardwareVibe = hardwareVibe,
        weatherSnapshotLabel = weatherSnapshotLabel,
        sensorContext = sensorContext,
    )

    fun toggleHostSelectionPeer(peerId: String) = toggleHostSelectionPeerImpl(peerId = peerId)

    fun promotePendingConfirmationToHostSelection(currentUserId: String) =
        promotePendingConfirmationToHostSelectionImpl(currentUserId = currentUserId)

    fun confirmHostProximitySelection(
        selectedPeerIds: List<String>,
        currentUserId: String,
        contextTag: ContextTag? = null,
        hardwareVibe: HardwareVibeSnapshot? = null,
        weatherSnapshotLabel: String? = null,
        sensorContext: ConnectionSensorContext? = null,
    ) = confirmHostProximitySelectionImpl(
        selectedPeerIds = selectedPeerIds,
        currentUserId = currentUserId,
        contextTag = contextTag,
        hardwareVibe = hardwareVibe,
        weatherSnapshotLabel = weatherSnapshotLabel,
        sensorContext = sensorContext,
    )

    fun saveContextTags(
        tagging: ConnectionState.TaggingContext,
        contextTag: ContextTag?,
        noiseLevelCategory: NoiseLevelCategory?,
        exactNoiseLevelDb: Double?,
        heightCategory: HeightCategory?,
        exactBarometricElevationMeters: Double?,
        ambientNoiseMonitor: AmbientNoiseMonitor? = null,
        barometricHeightMonitor: BarometricHeightMonitor? = null,
        ambientNoiseOptIn: Boolean = false,
        barometricContextOptIn: Boolean = false,
    ) = saveContextTagsImpl(
        tagging = tagging,
        contextTag = contextTag,
        noiseLevelCategory = noiseLevelCategory,
        exactNoiseLevelDb = exactNoiseLevelDb,
        heightCategory = heightCategory,
        exactBarometricElevationMeters = exactBarometricElevationMeters,
        ambientNoiseMonitor = ambientNoiseMonitor,
        barometricHeightMonitor = barometricHeightMonitor,
        ambientNoiseOptIn = ambientNoiseOptIn,
        barometricContextOptIn = barometricContextOptIn,
    )
}
