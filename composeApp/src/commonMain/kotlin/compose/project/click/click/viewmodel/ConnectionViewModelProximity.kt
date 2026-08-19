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

internal fun ConnectionViewModel.handleInstantProximityOutcome(outcome: BindProximityHandshakeOutcome, currentUserId: String) {
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
        _transientNotice.tryEmit(ConnectionViewModel.RECONNECTION_ENCOUNTER_COOLDOWN_MESSAGE)
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
        AppDataManager.notifyProximityConnectionChanged(
            peerUserIds = others,
            connectionIds = listOfNotNull(outcome.connectionId),
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
        AppDataManager.notifyProximityConnectionChanged(
            peerUserIds = listOf(peer.id),
            connectionIds = listOf(outcome.connectionId),
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

internal fun ConnectionViewModel.upsertProximityConnectionIfNeeded(
    connection: Connection,
    otherUser: User?,
    isNewConnection: Boolean,
) {
    val alreadyPresent = AppDataManager.connections.value.any { it.id == connection.id }
    if (!isNewConnection && alreadyPresent) return
    AppDataManager.addConnection(connection, otherUser)
}

internal fun ConnectionViewModel.handleAwaitingHostSelection(
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

internal fun ConnectionViewModel.startPendingProximityRecovery(
    pendingHandshakeId: String,
    bearerJwt: String,
    currentUserId: String,
) {
    val pendingId = pendingHandshakeId.trim()
    if (pendingId.isEmpty() || bearerJwt.isBlank()) return
    viewModelScope.launch {
        repeat(ConnectionViewModel.PENDING_MATCH_RECOVERY_ATTEMPTS) {
            delay(ConnectionViewModel.PENDING_MATCH_RECOVERY_DELAY_MS)
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

internal fun ConnectionViewModel.shouldBlockForRateLimit(users: List<User>, aggregateEncounterLogged: Boolean): Boolean {
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
internal fun ConnectionViewModel.startTapProximityHandshakeImpl(
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
    if (nowMs - lastTapProximityStartedAtMs < ConnectionViewModel.TAP_PROXIMITY_DEBOUNCE_MS) {
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
                ConnectionViewModel.SIMULATOR_MOCK_MY_TOKEN
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
                        heardTokens = ConnectionViewModel.SIMULATOR_MOCK_HEARD_TOKENS,
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
                                message = ConnectionViewModel.PROXIMITY_PENDING_MATCH_MESSAGE,
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
                            message = ConnectionViewModel.PROXIMITY_OFFLINE_SYNC_MESSAGE,
                        )
                    } else {
                        ConnectionFlowTelemetry.recordFailed(reason = "bind_failed")
                        _connectionState.value = ConnectionState.Error(e.message ?: "Proximity handshake failed")
                    }
                }
            }
        } catch (e: ProximityHardwarePermissionException) {
            ConnectionFlowTelemetry.recordFailed(reason = "hardware_permissions")
            _connectionState.value = ConnectionState.Error(ConnectionViewModel.HARDWARE_PERMISSIONS_MISSING_MESSAGE)
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
internal fun ConnectionViewModel.onProximityHandshakeRecoveredFromBackgroundImpl(
    payload: ProximityHandshakeRecoveryPayload,
    currentUserId: String,
) {
    val users = payload.users
    if (users.isEmpty()) return
    lastProximityEncounterLoggedAggregate = payload.encounterLogged
    if (shouldBlockForRateLimit(users, payload.encounterLogged)) {
        _transientNotice.tryEmit(ConnectionViewModel.RECONNECTION_ENCOUNTER_COOLDOWN_MESSAGE)
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
internal fun ConnectionViewModel.tryFlushPendingProximityHandshakesImpl(jwt: String, currentUserId: String) {
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
                    message = ConnectionViewModel.PROXIMITY_PENDING_MATCH_MESSAGE,
                )
            !r.recoveredUsers.isNullOrEmpty() -> {
                lastProximityEncounterLoggedAggregate = r.recoveredEncounterLogged
                if (shouldBlockForRateLimit(r.recoveredUsers, r.recoveredEncounterLogged)) {
                    _transientNotice.tryEmit(ConnectionViewModel.RECONNECTION_ENCOUNTER_COOLDOWN_MESSAGE)
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
                    message = ConnectionViewModel.PROXIMITY_OFFLINE_SYNC_MESSAGE,
                )
            else ->
                _connectionState.value = ConnectionState.Idle
        }
    }
}

internal fun ConnectionViewModel.syntheticUserForProximitySuccess(profiles: List<UserProfile>): User {
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

internal fun ConnectionViewModel.syntheticProximityConnection(
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
