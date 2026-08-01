package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.collaboration.CollaborationSession // pragma: allowlist secret
import compose.project.click.click.collaboration.CollaborationSessionManager // pragma: allowlist secret
import compose.project.click.click.data.repository.BindProximityHandshakeOutcome // pragma: allowlist secret
import compose.project.click.click.data.repository.BindProximityHandshakeResult // pragma: allowlist secret
import compose.project.click.click.data.repository.PROXIMITY_HOST_SELECTION_MAX_PEERS // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.isActiveForUser // pragma: allowlist secret
import compose.project.click.click.data.models.isOneToOnePairEdge // pragma: allowlist secret
import compose.project.click.click.data.models.ConnectionRequest // pragma: allowlist secret
import compose.project.click.click.data.models.ContextTag // pragma: allowlist secret
import compose.project.click.click.data.models.HeightCategory // pragma: allowlist secret
import compose.project.click.click.data.models.NoiseLevelCategory // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.UserProfile // pragma: allowlist secret
import compose.project.click.click.data.models.toUserProfile // pragma: allowlist secret
import compose.project.click.click.data.models.isPendingSync // pragma: allowlist secret
import compose.project.click.click.data.repository.ConnectionCreateOutcome // pragma: allowlist secret
import compose.project.click.click.data.repository.ConnectionRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.ProximityHandshakeRecoveryPayload // pragma: allowlist secret
import compose.project.click.click.data.repository.isRetryableForProximityBind // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseChatRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.domain.VerifiedCliqueCreation // pragma: allowlist secret
import compose.project.click.click.proximity.MockProximityManager // pragma: allowlist secret
import compose.project.click.click.proximity.ProximityHardwarePermissionException // pragma: allowlist secret
import compose.project.click.click.proximity.ProximityManager // pragma: allowlist secret
import compose.project.click.click.proximity.isSimulatorOrEmulatorRuntime // pragma: allowlist secret
import compose.project.click.click.proximity.ProximityHandshakeListenResult
import compose.project.click.click.proximity.PROXIMITY_SENSOR_LOCATION_WAIT_MS
import compose.project.click.click.proximity.PROXIMITY_SENSOR_WAIT_MS
import compose.project.click.click.proximity.proximityBindLocationWaitMs
import compose.project.click.click.proximity.scheduleProximityHandshakeSync // pragma: allowlist secret
import compose.project.click.click.sensors.AmbientNoiseMonitor // pragma: allowlist secret
import compose.project.click.click.sensors.BarometricHeightMonitor // pragma: allowlist secret
import compose.project.click.click.sensors.buildEncounterSensorJson // pragma: allowlist secret
import compose.project.click.click.sensors.captureConnectionSensorContext // pragma: allowlist secret
import compose.project.click.click.sensors.ConnectionSensorContext // pragma: allowlist secret
import compose.project.click.click.sensors.HardwareVibeMonitor // pragma: allowlist secret
import compose.project.click.click.sensors.HardwareVibeSnapshot // pragma: allowlist secret
import compose.project.click.click.telemetry.ConnectionFlowTelemetry
import compose.project.click.click.utils.LocationResult // pragma: allowlist secret
import compose.project.click.click.utils.LocationService // pragma: allowlist secret
import io.ktor.client.HttpClient // pragma: allowlist secret
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock

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
    data class Error(val message: String) : ConnectionState()
    object ProximityFetchingLocation : ConnectionState()
    object ProximityHandshaking : ConnectionState()
    data class PendingConfirmation(val users: List<User>) : ConnectionState()
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
    private val repository = ConnectionRepository()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
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
        private const val SIMULATOR_MOCK_MY_TOKEN: String = "1234"
        private val SIMULATOR_MOCK_HEARD_TOKENS: List<String> = listOf("5678")
        private const val PENDING_MATCH_RECOVERY_ATTEMPTS: Int = 12
        private const val PENDING_MATCH_RECOVERY_DELAY_MS: Long = 2_500L
        private const val TAP_PROXIMITY_DEBOUNCE_MS: Long = 12_000L
    }

    private val _transientNotice = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val transientNotice: SharedFlow<String> = _transientNotice.asSharedFlow()

    private val _verifiedCliqueFromProximity = MutableSharedFlow<VerifiedCliqueProximityIntent>(extraBufferCapacity = 1)
    val verifiedCliqueFromProximity: SharedFlow<VerifiedCliqueProximityIntent> =
        _verifiedCliqueFromProximity.asSharedFlow()

    /**
     * Aggregate from the last proximity bind response (or deferred sync recovery).
     * Used for diagnostics; per-edge encounter logging is taken from each [ConnectionRepository.createConnection] result.
     */
    private var lastProximityEncounterLoggedAggregate: Boolean = true

    private var lastProximityLat: Double? = null
    private var lastProximityLng: Double? = null
    private var lastProximityAltitudeMeters: Double? = null
    private var lastProximityHardwareVibe: HardwareVibeSnapshot? = null
    private var lastTapProximityStartedAtMs: Long = 0L

    fun lastProximityCoordinates(): Pair<Double?, Double?> = lastProximityLat to lastProximityLng

    private fun isProximityHandshakeInFlight(): Boolean {
        return when (_connectionState.value) {
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
    }

    private suspend fun <T> Deferred<T>.awaitWithin(timeoutMs: Long): T? =
        withTimeoutOrNull(timeoutMs) { await() }

    private fun activateCollaborationSessionIfPresent(
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

    private fun activateCollaborationSessionIfPresent(outcome: BindProximityHandshakeOutcome) {
        activateCollaborationSessionIfPresent(
            connectionId = outcome.connectionId,
            encounterId = outcome.encounterId,
            collaborationTtl = outcome.collaborationTtl,
        )
    }

    private fun activateCollaborationSessionIfPresent(outcome: ConnectionCreateOutcome) {
        activateCollaborationSessionIfPresent(
            connectionId = outcome.connection.id,
            encounterId = outcome.encounterId,
            collaborationTtl = outcome.collaborationTtl,
        )
    }

    private fun handleInstantProximityOutcome(outcome: BindProximityHandshakeOutcome, currentUserId: String) {
        activateCollaborationSessionIfPresent(outcome)
        lastProximityEncounterLoggedAggregate = outcome.encounterLogged
        val users = outcome.matches
        if (users.isEmpty()) {
            ConnectionFlowTelemetry.recordFailed(reason = "no_nearby_tap")
            _connectionState.value = ConnectionState.Error("No nearby tap detected. Try again closer together.")
            return
        }
        val isReconnect = !outcome.isAggregateNewConnection
        if (shouldBlockForRateLimit(users, outcome.encounterLogged)) {
            ConnectionFlowTelemetry.recordReconnectRateLimited(
                peerCount = users.size,
                isGroup = outcome.isGroup,
            )
            _transientNotice.tryEmit(RECONNECTION_ENCOUNTER_COOLDOWN_MESSAGE)
        }
        PlatformHapticsPolicy.successNotification()
        val groupIds = outcome.groupCliqueCandidateMemberIds?.distinct()?.sorted()
        val others = groupIds?.filter { it != currentUserId }.orEmpty()
        if (outcome.isGroup && outcome.connectionId != null && groupIds != null && others.size >= 2) {
            ConnectionFlowTelemetry.recordMatched(
                peerCount = users.size,
                isGroup = true,
                isReconnect = isReconnect,
            )
            val groupConnection = syntheticProximityConnection(
                connectionId = outcome.connectionId,
                memberUserIds = groupIds,
                isGroup = true,
            )
            upsertProximityConnectionIfNeeded(
                connection = groupConnection,
                otherUser = syntheticUserForProximitySuccess(users.map { it.toUserProfile() }),
                isNewConnection = outcome.isAggregateNewConnection,
            )
            _connectionState.value = ConnectionState.TaggingContext(
                newConnections = listOf(groupConnection),
                targetUsers = users.map { it.toUserProfile() },
                isGroup = true,
                memberUserIds = groupIds,
                bindEncounterPersistedPeerIds = users
                    .filter { it.encounterPersistedOnBind }
                    .map { it.id }
                    .toSet(),
                isNewConnection = outcome.isAggregateNewConnection,
            )
        } else if (!outcome.isGroup && outcome.connectionId != null && users.size == 1) {
            ConnectionFlowTelemetry.recordMatched(
                peerCount = 1,
                isGroup = false,
                isReconnect = isReconnect,
            )
            val peer = users.first()
            val connection = syntheticProximityConnection(
                connectionId = outcome.connectionId,
                memberUserIds = listOf(currentUserId, peer.id).distinct().sorted(),
                isGroup = false,
            )
            upsertProximityConnectionIfNeeded(
                connection = connection,
                otherUser = peer,
                isNewConnection = outcome.isAggregateNewConnection,
            )
            _connectionState.value = ConnectionState.TaggingContext(
                newConnections = listOf(connection),
                targetUsers = listOf(peer.toUserProfile()),
                bindEncounterPersistedPeerIds = users
                    .filter { it.encounterPersistedOnBind }
                    .map { it.id }
                    .toSet(),
                isNewConnection = outcome.isAggregateNewConnection,
            )
        } else {
            ConnectionFlowTelemetry.recordAwaitingSelection(
                peerCount = users.size,
                candidateCount = users.size,
                isGroup = outcome.isGroup || users.size > 1,
                isReconnect = isReconnect,
            )
            val peers = users.filter { it.id.isNotBlank() && it.id != currentUserId }
            val profiles = peers.map { it.toUserProfile() }.take(PROXIMITY_HOST_SELECTION_MAX_PEERS)
            _connectionState.value = ConnectionState.TaggingContext(
                newConnections = emptyList(),
                targetUsers = profiles,
                isGroup = peers.size >= 2,
                memberUserIds = (listOf(currentUserId) + profiles.map { it.id }).distinct().sorted(),
                bindEncounterPersistedPeerIds = peers
                    .filter { it.encounterPersistedOnBind }
                    .map { it.id }
                    .toSet(),
                isNewConnection = outcome.isAggregateNewConnection,
                selectableUsers = profiles,
                requiresSelection = outcome.isAggregateNewConnection && peers.size >= 2,
                selectedPeerIds = profiles.map { it.id }.toSet(),
            )
        }
    }

    private fun upsertProximityConnectionIfNeeded(
        connection: Connection,
        otherUser: User?,
        isNewConnection: Boolean,
    ) {
        val alreadyPresent = AppDataManager.connections.value.any { it.id == connection.id }
        if (!isNewConnection && alreadyPresent) return
        AppDataManager.addConnection(connection, otherUser)
    }

    private fun handleAwaitingHostSelection(
        awaiting: BindProximityHandshakeResult.AwaitingHostSelection,
        currentUserId: String,
    ) {
        lastProximityEncounterLoggedAggregate = false
        val users = awaiting.candidates.filter { it.id.isNotBlank() && it.id != currentUserId }
        if (users.isEmpty()) {
            ConnectionFlowTelemetry.recordFailed(reason = "no_nearby_tap")
            _connectionState.value = ConnectionState.Error("No nearby tap detected. Try again closer together.")
            return
        }
        ConnectionFlowTelemetry.recordAwaitingSelection(
            peerCount = users.size,
            candidateCount = users.size,
            isGroup = awaiting.groupCliqueCandidateMemberIds.orEmpty().size >= 3 || users.size >= 2,
            isReconnect = !awaiting.isAggregateNewConnection,
        )
        PlatformHapticsPolicy.successNotification()
        val profiles = users.map { it.toUserProfile() }.take(PROXIMITY_HOST_SELECTION_MAX_PEERS)
        val selected = profiles.map { it.id }.toSet()
        val groupIds = awaiting.groupCliqueCandidateMemberIds?.distinct()?.sorted().orEmpty()
        _connectionState.value = ConnectionState.TaggingContext(
            newConnections = emptyList(),
            targetUsers = profiles,
            isGroup = groupIds.size >= 3 || users.size >= 2,
            memberUserIds = groupIds.ifEmpty {
                (listOf(currentUserId) + profiles.map { it.id }).distinct().sorted()
            },
            isNewConnection = awaiting.isAggregateNewConnection,
            selectableUsers = profiles,
            pendingHandshakeId = awaiting.pendingHandshakeId,
            requiresSelection = true,
            selectedPeerIds = selected,
        )
    }

    private fun startPendingProximityRecovery(
        pendingHandshakeId: String,
        bearerJwt: String,
        currentUserId: String,
    ) {
        val pendingId = pendingHandshakeId.trim()
        if (pendingId.isEmpty() || bearerJwt.isBlank()) return
        viewModelScope.launch {
            repeat(PENDING_MATCH_RECOVERY_ATTEMPTS) {
                delay(PENDING_MATCH_RECOVERY_DELAY_MS)
                if (_connectionState.value !is ConnectionState.ProximityHandshakePendingMatch) {
                    return@launch
                }
                val recovered = withContext(Dispatchers.Default) {
                    repository.recoverPendingProximityHandshake(
                        bearerJwt = bearerJwt,
                        pendingHandshakeId = pendingId,
                    )
                }.getOrNull()
                when (recovered) {
                    is BindProximityHandshakeResult.InstantMatch -> {
                        ConnectionFlowTelemetry.recordRecoveryPollSuccess(peerCount = recovered.outcome.matches.size)
                        handleInstantProximityOutcome(recovered.outcome, currentUserId)
                        return@launch
                    }
                    is BindProximityHandshakeResult.AwaitingHostSelection -> {
                        ConnectionFlowTelemetry.recordRecoveryPollSuccess(peerCount = recovered.candidates.size)
                        handleAwaitingHostSelection(recovered, currentUserId)
                        return@launch
                    }
                    is BindProximityHandshakeResult.PendingServerMatch,
                    null,
                    -> Unit
                }
            }
            if (_connectionState.value is ConnectionState.ProximityHandshakePendingMatch) {
                ConnectionFlowTelemetry.recordRecoveryPollTimeout(reason = "pending_match_polls_exhausted")
            }
        }
    }

    /**
     * After a valid QR payload is read, show the context sheet before redeem/create.
     */
    fun presentQrContextSheetFromScan(scannedUserId: String, qrToken: String?, venueId: String?) {
        if (scannedUserId.isBlank()) return
        viewModelScope.launch {
            val profile = repository.getUserById(scannedUserId).getOrNull()?.toUserProfile()
                ?: UserProfile(id = scannedUserId, displayName = "Connection")
            _connectionState.value = ConnectionState.QrAwaitingContext(
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
    val userConnections: StateFlow<List<Connection>> = combine(
        AppDataManager.connections,
        AppDataManager.archivedConnectionIds,
        AppDataManager.hiddenConnectionIds,
    ) { connections, archived, hidden ->
        connections.filter { it.isActiveForUser(archived, hidden) }
    }
        .distinctUntilChanged()
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
    fun prewarmBindProximityEdgeFunction(httpClient: HttpClient, jwt: String) {
        if (jwt.isBlank()) return
        viewModelScope.launch(Dispatchers.Default) {
            repository.prewarmBindProximityConnection(httpClient = httpClient, bearerJwt = jwt)
        }
    }

    fun showHardwarePermissionsMissing() {
        _connectionState.value = ConnectionState.Error(HARDWARE_PERMISSIONS_MISSING_MESSAGE)
    }

    private fun shouldBlockForRateLimit(users: List<User>, aggregateEncounterLogged: Boolean): Boolean {
        if (users.isEmpty()) return false
        val allReconnect = users.all { !it.isNewConnection }
        val allBoundPersisted = users.all { it.encounterPersistedOnBind }
        if (allReconnect && allBoundPersisted && aggregateEncounterLogged) {
            return false
        }
        return !aggregateEncounterLogged ||
            users.any { it.encounterLogged == false || it.reason == "rate_limit_active" }
    }

    /**
     * Tri-factor tap flow: GPS → concurrent BLE broadcast + 5s listen → server clustering → tagging / selection.
     */
    fun startTapProximityHandshake(
        httpClient: HttpClient,
        proximityManager: ProximityManager,
        jwt: String,
        currentUserId: String,
        locationService: LocationService,
        skipLocation: Boolean,
        ambientNoiseMonitor: AmbientNoiseMonitor? = null,
        barometricHeightMonitor: BarometricHeightMonitor? = null,
    ) {
        if (currentUserId.isBlank()) {
            _connectionState.value = ConnectionState.Error("User not logged in")
            return
        }
        if (jwt.isBlank()) {
            _connectionState.value = ConnectionState.Error("Please sign in again.")
            return
        }
        if (!proximityManager.supportsTapExchange()) {
            _connectionState.value = ConnectionState.Error(proximityManager.capabilityNote())
            return
        }
        if (isProximityHandshakeInFlight()) {
            return
        }
        val nowMs = Clock.System.now().toEpochMilliseconds()
        if (nowMs - lastTapProximityStartedAtMs < TAP_PROXIMITY_DEBOUNCE_MS) {
            _transientNotice.tryEmit("Tap already registered. Wait a moment before tapping again.")
            return
        }
        lastTapProximityStartedAtMs = nowMs
        ConnectionFlowTelemetry.recordStarted()
        viewModelScope.launch {
            try {
                lastProximityEncounterLoggedAggregate = true
                val shouldFetchLocation = !skipLocation && AppDataManager.shouldCaptureLocationAtTap()

                val simulatorMock = proximityManager is MockProximityManager || isSimulatorOrEmulatorRuntime()
                val myToken = if (simulatorMock) {
                    SIMULATOR_MOCK_MY_TOKEN
                } else {
                    (0..9999).random().toString().padStart(4, '0')
                }

                val tokenStorage = createTokenStorage()
                val noiseOptIn = tokenStorage.getAmbientNoiseOptIn() ?: true
                val baroOptIn = tokenStorage.getBarometricContextOptIn() ?: true

                _connectionState.value = if (shouldFetchLocation) {
                    ConnectionState.ProximityFetchingLocation
                } else {
                    ConnectionState.ProximityHandshaking
                }

                coroutineScope {
                    val locationDeferred = if (shouldFetchLocation) {
                        async {
                            if (!locationService.hasLocationPermission()) {
                                locationService.requestLocationPermission()
                                delay(800L)
                            }
                            runCatching { locationService.getHighAccuracyLocation(6500L) }.getOrNull()
                        }
                    } else {
                        null
                    }

                    val sensorDeferred = if (ambientNoiseMonitor != null && barometricHeightMonitor != null) {
                        async {
                            runCatching {
                                val locationForSensors = locationDeferred?.awaitWithin(PROXIMITY_SENSOR_LOCATION_WAIT_MS)
                                captureConnectionSensorContext(
                                    ambientNoiseMonitor = ambientNoiseMonitor,
                                    barometricHeightMonitor = barometricHeightMonitor,
                                    ambientNoiseOptIn = noiseOptIn,
                                    barometricContextOptIn = baroOptIn,
                                    latitude = locationForSensors?.latitude,
                                    longitude = locationForSensors?.longitude,
                                )
                            }.getOrNull()
                        }
                    } else {
                        null
                    }

                    val vibeDeferred = async(Dispatchers.Default) {
                        runCatching { HardwareVibeMonitor().takeSnapshot() }.getOrNull()
                    }

                    _connectionState.value = ConnectionState.ProximityHandshaking
                    val listenResult: ProximityHandshakeListenResult = if (simulatorMock) {
                        delay(2_000L)
                        ProximityHandshakeListenResult(
                            heardTokens = SIMULATOR_MOCK_HEARD_TOKENS,
                            detectedDevices = emptyList(),
                        )
                    } else {
                        try {
                            val listen = async { proximityManager.startHandshakeListening(myToken) }
                            delay(120L)
                            // Stagger ultrasonic broadcasts so several nearby devices are less likely to talk over each other.
                            delay(Random.nextLong(0, 400))
                            proximityManager.startHandshakeBroadcast(myToken)
                            listen.await()
                        } finally {
                            // Broadcast/permission failures and cancellation must still release
                            // BLE advertise/scan and the microphone, not just the happy path.
                            runCatching { proximityManager.stopAll() }
                        }
                    }

                    val heardTokensAudio = listenResult.heardTokens
                    val detectedDevicesBle = listenResult.detectedDevices
                    val locationWaitMs = proximityBindLocationWaitMs(listenResult)

                    val location = locationDeferred?.awaitWithin(locationWaitMs)
                        ?: if (shouldFetchLocation) {
                            runCatching { locationService.getCurrentLocation() }.getOrNull()
                                ?: AppDataManager.lastKnownDeviceLocation.value?.let { (la, lo) ->
                                    LocationResult(latitude = la, longitude = lo)
                                }
                        } else {
                            null
                        }
                    lastProximityLat = location?.latitude
                    lastProximityLng = location?.longitude
                    lastProximityAltitudeMeters = location?.altitudeMeters

                    val proximitySensorContext = sensorDeferred?.awaitWithin(PROXIMITY_SENSOR_WAIT_MS)
                    val vibe = vibeDeferred.awaitWithin(PROXIMITY_SENSOR_WAIT_MS)
                    lastProximityHardwareVibe = vibe

                    _connectionState.value = ConnectionState.ProximityResolving
                    val bindResult = withContext(Dispatchers.Default) {
                        runCatching {
                            withTimeout(22_000L) {
                                repository.bindProximityHandshake(
                                    httpClient = httpClient,
                                    bearerJwt = jwt,
                                    myToken = myToken,
                                    heardTokens = heardTokensAudio,
                                    detectedDevices = detectedDevicesBle,
                                    latitude = lastProximityLat,
                                    longitude = lastProximityLng,
                                    exactBarometricElevationM = proximitySensorContext
                                        ?.exactBarometricElevationMeters
                                        ?.takeIf { it.isFinite() },
                                    hardwareVibe = vibe,
                                    clientContextFirst = true,
                                    bindNoiseLevelCategory = proximitySensorContext?.noiseLevelCategory,
                                    bindExactNoiseLevelDb = proximitySensorContext?.exactNoiseLevelDb,
                                    bindHeightCategory = proximitySensorContext?.heightCategory,
                                    simulatorMock = simulatorMock,
                                ).getOrThrow()
                            }
                        }
                    }

                    if (bindResult.isSuccess) {
                        when (val handshake = bindResult.getOrNull()!!) {
                            is BindProximityHandshakeResult.PendingServerMatch -> {
                                ConnectionFlowTelemetry.recordPending()
                                _connectionState.value = ConnectionState.ProximityHandshakePendingMatch(
                                    message = PROXIMITY_PENDING_MATCH_MESSAGE,
                                )
                                startPendingProximityRecovery(
                                    pendingHandshakeId = handshake.pendingHandshakeId,
                                    bearerJwt = jwt,
                                    currentUserId = currentUserId,
                                )
                            }
                            is BindProximityHandshakeResult.AwaitingHostSelection -> {
                                handleAwaitingHostSelection(handshake, currentUserId)
                            }
                            is BindProximityHandshakeResult.InstantMatch -> {
                                handleInstantProximityOutcome(handshake.outcome, currentUserId)
                            }
                        }
                    } else {
                        val e = bindResult.exceptionOrNull()!!
                        if (e.isRetryableForProximityBind()) {
                            ConnectionFlowTelemetry.recordOfflineQueued(reason = "retryable_bind")
                            repository.enqueuePendingProximityHandshake(
                                myToken = myToken,
                                heardTokens = heardTokensAudio,
                                detectedDevices = detectedDevicesBle,
                                latitude = lastProximityLat,
                                longitude = lastProximityLng,
                                altitudeMeters = lastProximityAltitudeMeters,
                                hardwareVibe = lastProximityHardwareVibe,
                                noiseLevel = proximitySensorContext?.noiseLevelCategory?.name,
                                exactNoiseLevelDb = proximitySensorContext?.exactNoiseLevelDb,
                                heightCategory = proximitySensorContext?.heightCategory?.name,
                                exactBarometricElevationM = proximitySensorContext
                                    ?.exactBarometricElevationMeters
                                    ?.takeIf { it.isFinite() },
                            )
                            scheduleProximityHandshakeSync()
                            _connectionState.value = ConnectionState.ProximityCapturedOfflineSyncing(
                                message = PROXIMITY_OFFLINE_SYNC_MESSAGE,
                            )
                        } else {
                            ConnectionFlowTelemetry.recordFailed(reason = "bind_failed")
                            _connectionState.value = ConnectionState.Error(e.message ?: "Proximity handshake failed")
                        }
                    }
                }
            } catch (e: ProximityHardwarePermissionException) {
                ConnectionFlowTelemetry.recordFailed(reason = "hardware_permissions")
                _connectionState.value = ConnectionState.Error(HARDWARE_PERMISSIONS_MISSING_MESSAGE)
            } catch (e: Exception) {
                ConnectionFlowTelemetry.recordFailed(reason = "handshake_exception")
                _connectionState.value = ConnectionState.Error(e.message ?: "Proximity handshake failed")
            }
        }
    }

    /**
     * When [AppDataManager] / WorkManager finishes a deferred bind, resume the confirm step or
     * emit [verifiedCliqueFromProximity] for a multi-user cluster.
     */
    fun onProximityHandshakeRecoveredFromBackground(
        payload: ProximityHandshakeRecoveryPayload,
        currentUserId: String,
    ) {
        val users = payload.users
        if (users.isEmpty()) return
        lastProximityEncounterLoggedAggregate = payload.encounterLogged
        if (shouldBlockForRateLimit(users, payload.encounterLogged)) {
            _transientNotice.tryEmit(RECONNECTION_ENCOUNTER_COOLDOWN_MESSAGE)
        }
        val awaitingPendingId = payload.pendingHandshakeId?.trim().orEmpty()
        // awaiting_selection recovery must open host pick + confirm — never auto-create a clique.
        if (awaitingPendingId.isNotEmpty()) {
            PlatformHapticsPolicy.successNotification()
            handleAwaitingHostSelection(
                BindProximityHandshakeResult.AwaitingHostSelection(
                    pendingHandshakeId = awaitingPendingId,
                    expiresAt = null,
                    candidates = users,
                    isAggregateNewConnection = payload.isAggregateNewConnection,
                    groupCliqueCandidateMemberIds = payload.groupCliqueCandidateMemberIds,
                ),
                currentUserId,
            )
            return
        }
        val groupIds = payload.groupCliqueCandidateMemberIds
        val others = groupIds?.filter { it != currentUserId }?.distinct().orEmpty()
        // Only first-time multi (≥2 peers) autofills verified clique; reconnects open encounter sheet.
        if (
            payload.isAggregateNewConnection &&
            currentUserId.isNotBlank() &&
            groupIds != null &&
            others.size >= 2
        ) {
            PlatformHapticsPolicy.successNotification()
            viewModelScope.launch {
                _verifiedCliqueFromProximity.emit(
                    VerifiedCliqueProximityIntent(
                        preselectFriendIds = others.take(PROXIMITY_HOST_SELECTION_MAX_PEERS),
                        matchedUsers = users,
                    ),
                )
            }
            return
        }
        val cur = _connectionState.value
        if (cur is ConnectionState.ProximityCapturedOfflineSyncing ||
            cur is ConnectionState.ProximityHandshakePendingMatch ||
            cur is ConnectionState.Idle
        ) {
            PlatformHapticsPolicy.successNotification()
            val peers = users.filter { it.id.isNotBlank() && it.id != currentUserId }
            val profiles = peers.map { it.toUserProfile() }
            val cappedProfiles = profiles.take(PROXIMITY_HOST_SELECTION_MAX_PEERS)
            _connectionState.value = ConnectionState.TaggingContext(
                newConnections = emptyList(),
                targetUsers = cappedProfiles,
                isGroup = peers.size >= 2 || groupIds.orEmpty().size >= 3,
                memberUserIds = groupIds?.takeIf { it.isNotEmpty() }
                    ?: (listOf(currentUserId) + cappedProfiles.map { it.id }).distinct().sorted(),
                bindEncounterPersistedPeerIds = peers
                    .filter { it.encounterPersistedOnBind }
                    .map { it.id }
                    .toSet(),
                isNewConnection = payload.isAggregateNewConnection,
                selectableUsers = cappedProfiles,
                // Reconnect multi: optional people pick for encounter; first-time without pending id
                // is legacy — still requires host pick before create.
                requiresSelection = payload.isAggregateNewConnection && peers.size >= 2,
                selectedPeerIds = cappedProfiles.map { it.id }.toSet(),
            )
        }
    }

    /** Manual retry from the offline-captured UI. */
    fun tryFlushPendingProximityHandshakes(jwt: String, currentUserId: String) {
        if (jwt.isBlank()) {
            _connectionState.value = ConnectionState.Error("Please sign in again.")
            return
        }
        viewModelScope.launch {
            _connectionState.value = ConnectionState.Loading
            val r = withContext(Dispatchers.Default) {
                repository.syncPendingProximityHandshakes(jwt)
            }
            when {
                r.serverPendingMatch ->
                    _connectionState.value = ConnectionState.ProximityHandshakePendingMatch(
                        message = PROXIMITY_PENDING_MATCH_MESSAGE,
                    )
                !r.recoveredUsers.isNullOrEmpty() -> {
                    lastProximityEncounterLoggedAggregate = r.recoveredEncounterLogged
                    if (shouldBlockForRateLimit(r.recoveredUsers, r.recoveredEncounterLogged)) {
                        _transientNotice.tryEmit(RECONNECTION_ENCOUNTER_COOLDOWN_MESSAGE)
                    }
                    PlatformHapticsPolicy.successNotification()
                    val awaitingPendingId = r.pendingHandshakeId?.trim().orEmpty()
                    if (awaitingPendingId.isNotEmpty()) {
                        handleAwaitingHostSelection(
                            BindProximityHandshakeResult.AwaitingHostSelection(
                                pendingHandshakeId = awaitingPendingId,
                                expiresAt = null,
                                candidates = r.recoveredUsers,
                                isAggregateNewConnection = r.isAggregateNewConnection,
                                groupCliqueCandidateMemberIds = r.groupCliqueCandidateMemberIds,
                            ),
                            currentUserId,
                        )
                        return@launch
                    }
                    val g = r.groupCliqueCandidateMemberIds
                    val others = g?.filter { it != currentUserId }?.distinct().orEmpty()
                    if (
                        r.isAggregateNewConnection &&
                        currentUserId.isNotBlank() &&
                        g != null &&
                        others.size >= 2
                    ) {
                        _verifiedCliqueFromProximity.emit(
                            VerifiedCliqueProximityIntent(
                                preselectFriendIds = others.take(PROXIMITY_HOST_SELECTION_MAX_PEERS),
                                matchedUsers = r.recoveredUsers,
                            ),
                        )
                        _connectionState.value = ConnectionState.Idle
                    } else {
                        val peers = r.recoveredUsers
                            .filter { it.id.isNotBlank() && it.id != currentUserId }
                        val profiles = peers.map { it.toUserProfile() }
                            .take(PROXIMITY_HOST_SELECTION_MAX_PEERS)
                        _connectionState.value = ConnectionState.TaggingContext(
                            newConnections = emptyList(),
                            targetUsers = profiles,
                            isGroup = peers.size >= 2,
                            memberUserIds = (listOf(currentUserId) + peers.map { it.id }).distinct().sorted(),
                            bindEncounterPersistedPeerIds = peers
                                .filter { it.encounterPersistedOnBind }
                                .map { it.id }
                                .toSet(),
                            isNewConnection = r.isAggregateNewConnection,
                            selectableUsers = profiles,
                            requiresSelection = r.isAggregateNewConnection && peers.size >= 2,
                            selectedPeerIds = profiles.map { it.id }.toSet(),
                        )
                    }
                }
                r.remainingInQueue > 0 ->
                    _connectionState.value = ConnectionState.ProximityCapturedOfflineSyncing(
                        message = PROXIMITY_OFFLINE_SYNC_MESSAGE,
                    )
                else ->
                    _connectionState.value = ConnectionState.Idle
            }
        }
    }

    /**
     * Connect with a user via QR code scan or confirmed proximity match.
     *
     * @param connectionMethod "qr", "proximity", or legacy "nfc"
     * @param initiatorId When null, derived for qr / proximity / nfc from [scannedUserId] / [currentUserId].
     */
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
    ) {
        viewModelScope.launch {
            _connectionState.value = ConnectionState.Loading

            try {
                if (scannedUserId == currentUserId) {
                    _connectionState.value = ConnectionState.Error("You cannot connect with yourself!")
                    return@launch
                }

                val resolvedInitiator = initiatorId ?: when (connectionMethod) {
                    "qr" -> scannedUserId
                    "proximity", "nfc" -> scannedUserId
                    else -> null
                }
                val resolvedResponder = responderId ?: when (connectionMethod) {
                    "qr" -> currentUserId
                    "proximity", "nfc" -> currentUserId
                    else -> null
                }

                val locLat = latitude ?: lastProximityLat
                val locLng = longitude ?: lastProximityLng
                val locAlt = altitudeMeters ?: lastProximityAltitudeMeters
                val qrHardwareVibe = when (connectionMethod) {
                    "qr" -> hardwareVibeOverride ?: withContext(Dispatchers.Default) {
                        runCatching { HardwareVibeMonitor().takeSnapshot() }.getOrNull()
                    }
                    else -> null
                }
                val requestHardwareVibe = qrHardwareVibe ?: lastProximityHardwareVibe

                val request = ConnectionRequest(
                    userId1 = currentUserId,
                    userId2 = scannedUserId,
                    locationLat = locLat,
                    locationLng = locLng,
                    venueId = venueId,
                    altitudeMeters = locAlt,
                    heightCategory = heightCategory,
                    exactBarometricElevationMeters = exactBarometricElevationMeters,
                    exactBarometricPressureHpa = exactBarometricPressureHpa,
                    contextTag = contextTagObject?.label ?: contextTag,
                    contextTagObject = contextTagObject,
                    connectionMethod = connectionMethod,
                    tokenAgeMs = tokenAgeMs,
                    qrToken = qrToken,
                    initiatorId = resolvedInitiator,
                    responderId = resolvedResponder,
                    noiseLevelCategory = noiseLevelCategory,
                    exactNoiseLevelDb = exactNoiseLevelDb,
                    luxLevel = requestHardwareVibe?.luxLevel?.takeIf { it.isFinite() }?.toDouble(),
                    motionVariance = requestHardwareVibe?.motionVariance?.takeIf { it.isFinite() }?.toDouble(),
                    compassAzimuth = requestHardwareVibe?.compassAzimuth?.takeIf { it.isFinite() }?.toDouble(),
                    batteryLevel = requestHardwareVibe?.batteryLevel?.takeIf { it in 0..100 },
                    weatherSnapshotLabel = weatherSnapshotLabel?.trim()?.takeIf { it.isNotEmpty() },
                )

                val result = withContext(Dispatchers.Default) {
                    repository.createConnection(request)
                }

                if (result.isSuccess) {
                    val outcome = result.getOrNull()!!
                    val connection = outcome.connection
                    val encounterLogged = outcome.encounterLogged
                    activateCollaborationSessionIfPresent(outcome)
                    val connectedUserId = connection.user_ids.firstOrNull { it != currentUserId } ?: scannedUserId
                    val connectedUser = withContext(Dispatchers.Default) {
                        repository.getUserById(connectedUserId)
                    }.getOrElse {
                        User(id = connectedUserId, name = "Connection", createdAt = 0L)
                    }

                    AppDataManager.addConnection(connection, connectedUser)

                    if (!connection.isPendingSync()) {
                        AppDataManager.refresh(force = true)
                    }

                    when {
                        !encounterLogged -> {
                            _transientNotice.tryEmit(RECONNECTION_ENCOUNTER_COOLDOWN_MESSAGE)
                            if (connection.isPendingSync()) {
                                _connectionState.value = ConnectionState.Success(connection, connectedUser)
                            } else if (
                                connectionMethod == "qr" &&
                                    contextTagObject == null &&
                                    contextTag.isNullOrBlank()
                            ) {
                                _connectionState.value = ConnectionState.TaggingContext(
                                    newConnections = listOf(connection),
                                    targetUsers = listOf(connectedUser.toUserProfile()),
                                    isNewConnection = true,
                                )
                            } else {
                                _connectionState.value = ConnectionState.Success(connection, connectedUser)
                            }
                        }
                        connection.isPendingSync() -> {
                            _connectionState.value = ConnectionState.Success(connection, connectedUser)
                        }
                        connectionMethod == "qr" &&
                            contextTagObject == null &&
                            contextTag.isNullOrBlank() -> {
                            _connectionState.value = ConnectionState.TaggingContext(
                                newConnections = listOf(connection),
                                targetUsers = listOf(connectedUser.toUserProfile()),
                                isNewConnection = true,
                            )
                        }
                        else -> {
                            _connectionState.value = ConnectionState.Success(connection, connectedUser)
                        }
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to create connection"
                    _connectionState.value = ConnectionState.Error(error)
                }
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetConnectionState() {
        val tagging = _connectionState.value as? ConnectionState.TaggingContext
        if (tagging?.requiresSelection == true) {
            ConnectionFlowTelemetry.recordHostSelectionAbandoned(
                candidateCount = tagging.selectableUsers.size.takeIf { it > 0 }
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

    /**
     * Reconnection encounter: POST `/api/connections/encounter` with ambient sensor snapshot + last proximity GPS.
     */
    fun saveReconnectEncounter(
        tagging: ConnectionState.TaggingContext,
        currentUserId: String,
        ambientNoiseMonitor: AmbientNoiseMonitor? = null,
        barometricHeightMonitor: BarometricHeightMonitor? = null,
        ambientNoiseOptIn: Boolean = true,
        barometricContextOptIn: Boolean = true,
    ) {
        if (currentUserId.isBlank()) return
        val peersNeedingInsert = reconnectEncounterPeersNeedingInsert(
            targetUsers = tagging.targetUsers,
            currentUserId = currentUserId,
            bindEncounterPersistedPeerIds = tagging.bindEncounterPersistedPeerIds,
        )
        val validPeerCount = tagging.targetUsers
            .filter { it.id.isNotBlank() && it.id != currentUserId }
            .distinctBy { it.id }
            .size
        if (validPeerCount == 0) return
        viewModelScope.launch {
            _connectionState.value = tagging.copy(encounterSubmitting = true)
            try {
                val snapshot = if (ambientNoiseMonitor != null && barometricHeightMonitor != null) {
                    captureConnectionSensorContext(
                        ambientNoiseMonitor = ambientNoiseMonitor,
                        barometricHeightMonitor = barometricHeightMonitor,
                        ambientNoiseOptIn = ambientNoiseOptIn,
                        barometricContextOptIn = barometricContextOptIn,
                        latitude = lastProximityLat,
                        longitude = lastProximityLng,
                    )
                } else {
                    null
                }
                val sensorJson = buildEncounterSensorJson(
                    context = snapshot,
                    hardwareVibe = lastProximityHardwareVibe,
                    latitude = lastProximityLat,
                    longitude = lastProximityLng,
                ).takeUnless { it.isEmpty() }
                if (peersNeedingInsert.isEmpty()) {
                    for (connection in tagging.newConnections) {
                        if (connection.isPendingSync()) continue
                        val patch = withContext(Dispatchers.Default) {
                            repository.updateConnectionTags(
                                connectionId = connection.id,
                                reportingUserId = currentUserId,
                                contextTag = null,
                                noiseLevelCategory = snapshot?.noiseLevelCategory,
                                exactNoiseLevelDb = snapshot?.exactNoiseLevelDb,
                                heightCategory = snapshot?.heightCategory,
                                exactBarometricElevationMeters = snapshot?.exactBarometricElevationMeters,
                            )
                        }
                        if (patch.isFailure) {
                            _connectionState.value = ConnectionState.Error(
                                patch.exceptionOrNull()?.message ?: "Could not update encounter",
                            )
                            return@launch
                        }
                    }
                    PlatformHapticsPolicy.successNotification()
                    ConnectionFlowTelemetry.recordReconnectEncounterSaved(
                        peerCount = validPeerCount,
                        isGroup = validPeerCount >= 2 || tagging.isGroup,
                    )
                    _connectionState.value = ConnectionState.Idle
                    return@launch
                }
                for (peer in peersNeedingInsert) {
                    val result = withContext(Dispatchers.Default) {
                        repository.postConnectionEncounter(
                            userId = currentUserId,
                            peerId = peer.id,
                            sensorData = sensorJson,
                        )
                    }
                    if (result.isFailure) {
                        _connectionState.value = ConnectionState.Error(
                            result.exceptionOrNull()?.message ?: "Could not save encounter",
                        )
                        return@launch
                    }
                }
                PlatformHapticsPolicy.successNotification()
                ConnectionFlowTelemetry.recordReconnectEncounterSaved(
                    peerCount = peersNeedingInsert.size.coerceAtLeast(validPeerCount),
                    isGroup = peersNeedingInsert.size >= 2 || tagging.isGroup,
                )
                _connectionState.value = ConnectionState.Idle
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e.message ?: "Could not save encounter")
            }
        }
    }

    /**
     * After the user confirms the proximity match list, create one connection per peer (1-to-1 edges),
     * then move to [ConnectionState.TaggingContext] so [saveContextTags] can fan out tags.
     */
    fun confirmProximityConnection(
        peerUsers: List<User>,
        currentUserId: String,
        hardwareVibe: HardwareVibeSnapshot? = null,
        weatherSnapshotLabel: String? = null,
        sensorContext: ConnectionSensorContext? = null,
    ) {
        val peers = peerUsers.filter { it.id.isNotBlank() && it.id != currentUserId }.distinctBy { it.id }
        if (peers.isEmpty()) {
            _connectionState.value = ConnectionState.Error("No users to connect with")
            return
        }
        viewModelScope.launch {
            _connectionState.value = ConnectionState.Loading
            try {
                val vibe = hardwareVibe ?: lastProximityHardwareVibe
                val created = mutableListOf<Connection>()
                var allEncountersLogged = true
                for (peer in peers) {
                    val existingLocalPair = AppDataManager.connections.value.firstOrNull { existing ->
                        existing.isOneToOnePairEdge() &&
                            existing.isInActiveConnectionsChannel() &&
                            peer.id in existing.user_ids &&
                            currentUserId in existing.user_ids
                    }
                    val request = ConnectionRequest(
                        userId1 = currentUserId,
                        userId2 = peer.id,
                        locationLat = lastProximityLat,
                        locationLng = lastProximityLng,
                        altitudeMeters = lastProximityAltitudeMeters,
                        heightCategory = sensorContext?.heightCategory,
                        exactBarometricElevationMeters = sensorContext?.exactBarometricElevationMeters,
                        exactBarometricPressureHpa = sensorContext?.exactBarometricPressureHpa,
                        contextTag = null,
                        contextTagObject = null,
                        connectionMethod = "proximity",
                        initiatorId = peer.id,
                        responderId = currentUserId,
                        noiseLevelCategory = sensorContext?.noiseLevelCategory,
                        exactNoiseLevelDb = sensorContext?.exactNoiseLevelDb,
                        luxLevel = vibe?.luxLevel?.takeIf { it.isFinite() }?.toDouble(),
                        motionVariance = vibe?.motionVariance?.takeIf { it.isFinite() }?.toDouble(),
                        compassAzimuth = vibe?.compassAzimuth?.takeIf { it.isFinite() }?.toDouble(),
                        batteryLevel = vibe?.batteryLevel?.takeIf { it in 0..100 },
                        weatherSnapshotLabel = weatherSnapshotLabel?.trim()?.takeIf { it.isNotEmpty() },
                        skipEncounterInsert = peer.encounterPersistedOnBind,
                        preflightConnectionId = peer.connectionId?.takeIf { it.isNotBlank() }
                            ?: existingLocalPair?.id,
                        preflightEncounterLogged = if (peer.encounterPersistedOnBind) true else null,
                    )
                    val result = withContext(Dispatchers.Default) {
                        repository.createConnection(request)
                    }
                    if (result.isFailure) {
                        lastProximityEncounterLoggedAggregate = true
                        _connectionState.value = ConnectionState.Error(
                            result.exceptionOrNull()?.message ?: "Failed to create connection"
                        )
                        return@launch
                    }
                    val outcome = result.getOrNull()!!
                    allEncountersLogged = allEncountersLogged && outcome.encounterLogged
                    val connection = outcome.connection
                    created.add(connection)
                    upsertProximityConnectionIfNeeded(
                        connection = connection,
                        otherUser = peer,
                        isNewConnection = peer.isNewConnection,
                    )
                }
                if (!created.any { it.isPendingSync() }) {
                    AppDataManager.refresh(force = true)
                    AppDataManager.requestInboxReload()
                }
                val profiles = peers.map { it.toUserProfile() }
                val isNewAggregate = peers.any { it.isNewConnection }
                lastProximityEncounterLoggedAggregate = true
                if (!allEncountersLogged) {
                    _connectionState.value = ConnectionState.Idle
                    _transientNotice.tryEmit(RECONNECTION_ENCOUNTER_COOLDOWN_MESSAGE)
                } else {
                    _connectionState.value = ConnectionState.TaggingContext(
                        newConnections = created,
                        targetUsers = profiles,
                        isNewConnection = isNewAggregate,
                    )
                }
            } catch (e: Exception) {
                lastProximityEncounterLoggedAggregate = true
                _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun toggleHostSelectionPeer(peerId: String) {
        val tagging = _connectionState.value as? ConnectionState.TaggingContext ?: return
        if (!tagging.requiresSelection) return
        val id = peerId.trim()
        if (id.isEmpty()) return
        val next = if (id in tagging.selectedPeerIds) {
            tagging.selectedPeerIds - id
        } else if (tagging.selectedPeerIds.size >= PROXIMITY_HOST_SELECTION_MAX_PEERS) {
            tagging.selectedPeerIds
        } else {
            tagging.selectedPeerIds + id
        }
        _connectionState.value = tagging.copy(selectedPeerIds = next)
    }

    /**
     * Promote legacy multi-peer [ConnectionState.PendingConfirmation] into the combined
     * people + tags [ConnectionState.TaggingContext] (host selection) sheet.
     */
    fun promotePendingConfirmationToHostSelection(currentUserId: String) {
        val pending = _connectionState.value as? ConnectionState.PendingConfirmation ?: return
        val peers = pending.users.filter { it.id.isNotBlank() && it.id != currentUserId }
        if (peers.size < 2) return
        val profiles = peers.map { it.toUserProfile() }.take(PROXIMITY_HOST_SELECTION_MAX_PEERS)
        _connectionState.value = ConnectionState.TaggingContext(
            newConnections = emptyList(),
            targetUsers = profiles,
            isGroup = peers.size >= 2,
            memberUserIds = (listOf(currentUserId) + profiles.map { it.id }).distinct().sorted(),
            bindEncounterPersistedPeerIds = peers
                .filter { it.encounterPersistedOnBind }
                .map { it.id }
                .toSet(),
            isNewConnection = peers.any { it.isNewConnection },
            selectableUsers = profiles,
            requiresSelection = true,
            selectedPeerIds = profiles.map { it.id }.toSet(),
        )
    }

    /**
     * Host confirms multi-peer selection (+ optional context tags).
     * Uses `POST /api/connections/proximity/confirm` when [ConnectionState.TaggingContext.pendingHandshakeId]
     * is set; otherwise falls back to legacy per-peer [confirmProximityConnection].
     */
    fun confirmHostProximitySelection(
        selectedPeerIds: List<String>,
        currentUserId: String,
        contextTag: ContextTag? = null,
        hardwareVibe: HardwareVibeSnapshot? = null,
        weatherSnapshotLabel: String? = null,
        sensorContext: ConnectionSensorContext? = null,
    ) {
        val tagging = _connectionState.value as? ConnectionState.TaggingContext
        if (tagging == null || !tagging.requiresSelection) {
            _connectionState.value = ConnectionState.Error("No proximity selection in progress")
            return
        }
        val selected = selectedPeerIds
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != currentUserId }
            .distinct()
            .take(PROXIMITY_HOST_SELECTION_MAX_PEERS)
        if (selected.isEmpty()) {
            _connectionState.value = ConnectionState.Error("Select at least one person to connect with")
            return
        }
        val pendingId = tagging.pendingHandshakeId?.trim().orEmpty()
        val contextTagIds = contextTag?.let { tag ->
            listOf(if (tag.id == "custom") tag.label else tag.id)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
        ConnectionFlowTelemetry.recordHostSelectionConfirmed(
            selectedCount = selected.size,
            candidateCount = tagging.selectableUsers.size.takeIf { it > 0 } ?: tagging.targetUsers.size,
            isGroup = selected.size >= 2 || tagging.isGroup,
            isReconnect = !tagging.isNewConnection,
        )
        if (pendingId.isNotEmpty()) {
            viewModelScope.launch {
                _connectionState.value = ConnectionState.Loading
                try {
                    val outcome = withContext(Dispatchers.Default) {
                        repository.confirmProximitySelection(
                            pendingHandshakeId = pendingId,
                            selectedMemberIds = selected,
                            contextTags = contextTagIds,
                        )
                    }.getOrElse { err ->
                        _connectionState.value = ConnectionState.Error(
                            err.message ?: "Failed to confirm proximity selection",
                        )
                        return@launch
                    }
                    activateCollaborationSessionIfPresent(outcome)
                    lastProximityEncounterLoggedAggregate = outcome.encounterLogged
                    val matchedPeers = outcome.matches
                        .filter { it.id.isNotBlank() && it.id != currentUserId }
                    val profiles = matchedPeers.map { it.toUserProfile() }.ifEmpty {
                        tagging.selectableUsers.filter { it.id in selected }
                    }
                    val connectionId = outcome.connectionId
                    val created = if (!connectionId.isNullOrBlank()) {
                        val memberIds = outcome.groupCliqueCandidateMemberIds
                            ?.takeIf { it.isNotEmpty() }
                            ?: (listOf(currentUserId) + selected).distinct().sorted()
                        val connection = syntheticProximityConnection(
                            connectionId = connectionId,
                            memberUserIds = memberIds,
                            isGroup = outcome.isGroup || memberIds.size > 2,
                        )
                        upsertProximityConnectionIfNeeded(
                            connection = connection,
                            otherUser = syntheticUserForProximitySuccess(profiles),
                            isNewConnection = outcome.isAggregateNewConnection,
                        )
                        listOf(connection)
                    } else {
                        emptyList()
                    }
                    if (created.isNotEmpty() && !created.any { it.isPendingSync() }) {
                        AppDataManager.refresh(force = true)
                        AppDataManager.requestInboxReload()
                    }
                    if (!outcome.encounterLogged) {
                        _connectionState.value = ConnectionState.Idle
                        _transientNotice.tryEmit(RECONNECTION_ENCOUNTER_COOLDOWN_MESSAGE)
                        return@launch
                    }
                    if (created.isEmpty()) {
                        _connectionState.value = ConnectionState.Error("Connection was not created")
                        return@launch
                    }
                    // Host + ≥2 selected peers (member set ≥3) on a new spark → clique autofill
                    // with the confirmed selection only (never the raw unmatched BLE candidate list).
                    if (selected.size >= 2 && outcome.isAggregateNewConnection) {
                        val selectedUsers = matchedPeers.filter { it.id in selected }.ifEmpty {
                            tagging.selectableUsers
                                .ifEmpty { tagging.targetUsers }
                                .filter { it.id in selected }
                                .map { profile ->
                                    User(
                                        id = profile.id,
                                        name = profile.displayName,
                                        image = profile.avatarUrl,
                                        createdAt = 0L,
                                    )
                                }
                        }
                        ConnectionFlowTelemetry.recordVerifiedCliqueCreated(
                            peerCount = selected.size,
                            selectedCount = selected.size,
                            candidateCount = tagging.selectableUsers.size.takeIf { it > 0 }
                                ?: tagging.targetUsers.size,
                        )
                        _verifiedCliqueFromProximity.emit(
                            VerifiedCliqueProximityIntent(
                                preselectFriendIds = selected,
                                matchedUsers = selectedUsers,
                            ),
                        )
                        _connectionState.value = ConnectionState.Idle
                        return@launch
                    }
                    // Tags already sent with confirm when present; skip second tagging sheet.
                    if (!contextTagIds.isNullOrEmpty()) {
                        val primary = created.first()
                        _connectionState.value = ConnectionState.Success(
                            primary,
                            syntheticUserForProximitySuccess(profiles),
                        )
                    } else {
                        _connectionState.value = ConnectionState.TaggingContext(
                            newConnections = created,
                            targetUsers = profiles,
                            isGroup = outcome.isGroup || profiles.size >= 2,
                            memberUserIds = created.first().user_ids,
                            bindEncounterPersistedPeerIds = matchedPeers
                                .filter { it.encounterPersistedOnBind }
                                .map { it.id }
                                .toSet(),
                            isNewConnection = outcome.isAggregateNewConnection,
                        )
                    }
                } catch (e: Exception) {
                    _connectionState.value = ConnectionState.Error(e.message ?: "Failed to confirm selection")
                }
            }
            return
        }

        // Legacy PendingConfirmation path: create 1:1 edges client-side.
        val peerUsers = tagging.selectableUsers
            .ifEmpty { tagging.targetUsers }
            .filter { it.id in selected }
            .map { profile ->
                User(
                    id = profile.id,
                    name = profile.displayName,
                    image = profile.avatarUrl,
                    createdAt = 0L,
                    isNewConnection = tagging.isNewConnection,
                    encounterPersistedOnBind = profile.id in tagging.bindEncounterPersistedPeerIds,
                )
            }
        if (selected.size >= 2 && tagging.isNewConnection) {
            viewModelScope.launch {
                ConnectionFlowTelemetry.recordVerifiedCliqueCreated(
                    peerCount = selected.size,
                    selectedCount = selected.size,
                    candidateCount = tagging.selectableUsers.size.takeIf { it > 0 }
                        ?: tagging.targetUsers.size,
                )
                _verifiedCliqueFromProximity.emit(
                    VerifiedCliqueProximityIntent(
                        preselectFriendIds = selected,
                        matchedUsers = peerUsers,
                    ),
                )
            }
        }
        confirmProximityConnection(
            peerUsers = peerUsers,
            currentUserId = currentUserId,
            hardwareVibe = hardwareVibe,
            weatherSnapshotLabel = weatherSnapshotLabel,
            sensorContext = sensorContext,
        )
    }

    /**
     * Apply the same subjective [contextTag] (and optional sensor enrichment) to every connection
     * in the current [ConnectionState.TaggingContext], then surface [ConnectionState.Success].
     *
     * @param tagging Snapshot from the UI at confirm time so a brief sensor capture cannot race
     * past a state transition that would otherwise make this call a silent no-op.
     */
    fun saveContextTags(
        tagging: ConnectionState.TaggingContext,
        contextTag: ContextTag?,
        noiseLevelCategory: NoiseLevelCategory?,
        exactNoiseLevelDb: Double?,
        heightCategory: HeightCategory?,
        exactBarometricElevationMeters: Double?,
        ambientNoiseMonitor: AmbientNoiseMonitor? = null,
        barometricHeightMonitor: BarometricHeightMonitor? = null,
        ambientNoiseOptIn: Boolean = true,
        barometricContextOptIn: Boolean = true,
    ) {
        viewModelScope.launch {
            val connections = tagging.newConnections
            val targetProfiles = tagging.targetUsers
            if (connections.isEmpty()) {
                _connectionState.value = ConnectionState.Idle
                return@launch
            }
            try {
                val sensorsMissing = noiseLevelCategory == null &&
                    exactNoiseLevelDb == null &&
                    heightCategory == null &&
                    exactBarometricElevationMeters == null
                val snapshot = if (
                    sensorsMissing &&
                    ambientNoiseMonitor != null &&
                    barometricHeightMonitor != null
                ) {
                    captureConnectionSensorContext(
                        ambientNoiseMonitor = ambientNoiseMonitor,
                        barometricHeightMonitor = barometricHeightMonitor,
                        ambientNoiseOptIn = ambientNoiseOptIn,
                        barometricContextOptIn = barometricContextOptIn,
                        latitude = lastProximityLat,
                        longitude = lastProximityLng,
                    )
                } else {
                    null
                }
                val noiseOut = noiseLevelCategory ?: snapshot?.noiseLevelCategory
                val exactDbOut = exactNoiseLevelDb ?: snapshot?.exactNoiseLevelDb
                val heightOut = heightCategory ?: snapshot?.heightCategory
                val baroOut = exactBarometricElevationMeters ?: snapshot?.exactBarometricElevationMeters
                val selfId = AppDataManager.currentUser.value?.id
                for (connection in connections) {
                    if (connection.isPendingSync()) continue
                    val patch = withContext(Dispatchers.Default) {
                        repository.updateConnectionTags(
                            connectionId = connection.id,
                            reportingUserId = selfId,
                            contextTag = contextTag,
                            noiseLevelCategory = noiseOut,
                            exactNoiseLevelDb = exactDbOut,
                            heightCategory = heightOut,
                            exactBarometricElevationMeters = baroOut,
                        )
                    }
                    if (patch.isFailure) {
                        _connectionState.value = ConnectionState.Error(
                            patch.exceptionOrNull()?.message ?: "Failed to save context"
                        )
                        return@launch
                    }
                }
                if (!connections.any { it.isPendingSync() }) {
                    AppDataManager.refresh(force = true)
                }
                if (selfId != null && targetProfiles.size >= 1) {
                    val memberUserIds = (tagging.memberUserIds.takeIf { it.isNotEmpty() }
                        ?: (listOf(selfId) + targetProfiles.map { it.id })).distinct().sorted()
                    // Only auto-create verified clique for true multi-person groups (not 1:1 DMs).
                    if (tagging.isGroup && memberUserIds.size >= 3) {
                        _connectionState.value = ConnectionState.SecuringConnection
                        val selfProfile = AppDataManager.currentUser.value?.toUserProfile()
                        val nameParts = if (selfProfile != null) {
                            (listOf(selfProfile) + targetProfiles).distinctBy { it.id }.sortedBy { it.id }
                        } else {
                            targetProfiles.distinctBy { it.id }.sortedBy { it.id }
                        }
                        val initialGroupName = nameParts.joinToString(", ") { p ->
                            p.displayName.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotEmpty() }
                                ?: p.displayName.trim().ifBlank { "Friend" }
                        }
                        val chatRepo = SupabaseChatRepository(tokenStorage = createTokenStorage())
                        val auto = VerifiedCliqueCreation.createVerifiedCliqueWithWrappedKeys(
                            chatRepository = chatRepo,
                            connections = AppDataManager.connections.value,
                            currentUserId = selfId,
                            memberUserIds = memberUserIds,
                            initialGroupName = initialGroupName,
                        )
                        val created = auto.getOrNull()
                        if (created != null) {
                            val chatId = chatRepo.resolveChatIdForGroupId(created.groupId)
                            if (chatId != null) {
                                chatRepo.cacheGroupMasterKey(chatId, created.masterKey32)
                            }
                            AppDataManager.bumpChatListRefresh()
                        }
                    }
                }
                val primary = connections.first()
                val summaryUser = syntheticUserForProximitySuccess(targetProfiles)
                val stillTagging = _connectionState.value as? ConnectionState.TaggingContext
                val sameBatch = stillTagging?.newConnections?.map { it.id }?.toSet() ==
                    connections.map { it.id }.toSet()
                if (stillTagging == null || sameBatch) {
                    _connectionState.value = ConnectionState.Success(primary, summaryUser)
                }
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun syntheticUserForProximitySuccess(profiles: List<UserProfile>): User {
        val label = when (profiles.size) {
            0 -> "Connection"
            1 -> profiles.first().displayName
            2 -> "${profiles[0].displayName} and ${profiles[1].displayName}"
            else -> "${profiles[0].displayName}, ${profiles[1].displayName} +${profiles.size - 2} more"
        }
        val primaryId = profiles.firstOrNull()?.id ?: ""
        val primaryImage = profiles.firstOrNull()?.avatarUrl
        return User(
            id = primaryId,
            name = label,
            image = primaryImage,
            createdAt = 0L,
        )
    }

    private fun syntheticProximityConnection(
        connectionId: String,
        memberUserIds: List<String>,
        isGroup: Boolean,
    ): Connection {
        val now = Clock.System.now().toEpochMilliseconds()
        return Connection(
            id = connectionId,
            created = now,
            createdUtc = kotlinx.datetime.Instant.fromEpochMilliseconds(now).toString(),
            timeOfDayUtc = null,
            expiry = now + 30L * 24L * 60L * 60L * 1000L,
            user_ids = memberUserIds.distinct().sorted(),
            status = "active",
            expiry_state = "active",
            connection_method = "proximity",
            proximity_confidence = if (lastProximityLat != null && lastProximityLng != null) 65 else 50,
            isGroup = isGroup,
        )
    }
}
