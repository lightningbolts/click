package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.ConnectionRequest // pragma: allowlist secret
import compose.project.click.click.data.models.ContextTag // pragma: allowlist secret
import compose.project.click.click.data.models.HeightCategory // pragma: allowlist secret
import compose.project.click.click.data.models.NoiseLevelCategory // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.isOneToOnePairEdge // pragma: allowlist secret
import compose.project.click.click.data.models.isPendingSync // pragma: allowlist secret
import compose.project.click.click.data.models.toUserProfile // pragma: allowlist secret
import compose.project.click.click.data.repository.PROXIMITY_HOST_SELECTION_MAX_PEERS // pragma: allowlist secret
import compose.project.click.click.sensors.AmbientNoiseMonitor // pragma: allowlist secret
import compose.project.click.click.sensors.BarometricHeightMonitor // pragma: allowlist secret
import compose.project.click.click.sensors.ConnectionSensorContext // pragma: allowlist secret
import compose.project.click.click.sensors.HardwareVibeMonitor // pragma: allowlist secret
import compose.project.click.click.sensors.HardwareVibeSnapshot // pragma: allowlist secret
import compose.project.click.click.sensors.buildEncounterSensorJson // pragma: allowlist secret
import compose.project.click.click.sensors.captureConnectionSensorContext // pragma: allowlist secret
import compose.project.click.click.telemetry.ConnectionFlowTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reconnection encounter: POST `/api/connections/encounter` with ambient sensor snapshot + last proximity GPS.
 */
internal fun ConnectionViewModel.saveReconnectEncounterImpl(
    tagging: ConnectionState.TaggingContext,
    currentUserId: String,
    ambientNoiseMonitor: AmbientNoiseMonitor? = null,
    barometricHeightMonitor: BarometricHeightMonitor? = null,
    ambientNoiseOptIn: Boolean = false,
    barometricContextOptIn: Boolean = false,
) {
    if (currentUserId.isBlank()) return
    val peersNeedingInsert =
        reconnectEncounterPeersNeedingInsert(
            targetUsers = tagging.targetUsers,
            currentUserId = currentUserId,
            bindEncounterPersistedPeerIds = tagging.bindEncounterPersistedPeerIds,
        )
    val validPeerCount =
        tagging.targetUsers
            .filter { it.id.isNotBlank() && it.id != currentUserId }
            .distinctBy { it.id }
            .size
    if (validPeerCount == 0) return
    viewModelScope.launch {
        _connectionState.value = tagging.copy(encounterSubmitting = true)
        try {
            val snapshot =
                if (ambientNoiseMonitor != null && barometricHeightMonitor != null) {
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
            val sensorJson =
                buildEncounterSensorJson(
                    context = snapshot,
                    hardwareVibe = lastProximityHardwareVibe,
                    latitude = lastProximityLat,
                    longitude = lastProximityLng,
                ).takeUnless { it.isEmpty() }
            if (peersNeedingInsert.isEmpty()) {
                for (connection in tagging.newConnections) {
                    if (connection.isPendingSync()) continue
                    val patch =
                        withContext(Dispatchers.Default) {
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
                        _connectionState.value =
                            ConnectionState.Error(
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
                AppDataManager.notifyProximityConnectionChanged(
                    peerUserIds = tagging.targetUsers.map { it.id },
                    connectionIds = tagging.newConnections.map { it.id },
                )
                _connectionState.value = ConnectionState.Idle
                return@launch
            }
            for (peer in peersNeedingInsert) {
                val result =
                    withContext(Dispatchers.Default) {
                        repository.postConnectionEncounter(
                            userId = currentUserId,
                            peerId = peer.id,
                            sensorData = sensorJson,
                        )
                    }
                if (result.isFailure) {
                    _connectionState.value =
                        ConnectionState.Error(
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
            AppDataManager.notifyProximityConnectionChanged(
                peerUserIds = tagging.targetUsers.map { it.id },
                connectionIds = tagging.newConnections.map { it.id },
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
internal fun ConnectionViewModel.confirmProximityConnectionImpl(
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
                val existingLocalPair =
                    AppDataManager.connections.value.firstOrNull { existing ->
                        existing.isOneToOnePairEdge() &&
                            existing.isInActiveConnectionsChannel() &&
                            peer.id in existing.user_ids &&
                            currentUserId in existing.user_ids
                    }
                val request =
                    ConnectionRequest(
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
                        preflightConnectionId =
                            peer.connectionId?.takeIf { it.isNotBlank() }
                                ?: existingLocalPair?.id,
                        preflightEncounterLogged = if (peer.encounterPersistedOnBind) true else null,
                    )
                val result =
                    withContext(Dispatchers.Default) {
                        repository.createConnection(request)
                    }
                if (result.isFailure) {
                    lastProximityEncounterLoggedAggregate = true
                    _connectionState.value =
                        ConnectionState.Error(
                            result.exceptionOrNull()?.message ?: "Failed to create connection",
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
            if (peers.size >= 2) {
                prepareGroupAfterTap(
                    currentUserId = currentUserId,
                    groupMemberIds = emptyList(),
                    peers = peers.toGroupAfterTapPeers(),
                )
            }
            if (!allEncountersLogged) {
                _connectionState.value = ConnectionState.Idle
                _transientNotice.tryEmit(ConnectionViewModel.RECONNECTION_ENCOUNTER_COOLDOWN_MESSAGE)
            } else {
                _connectionState.value =
                    ConnectionState.TaggingContext(
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

internal fun ConnectionViewModel.toggleHostSelectionPeerImpl(peerId: String) {
    val tagging = _connectionState.value as? ConnectionState.TaggingContext ?: return
    if (!tagging.requiresSelection) return
    val id = peerId.trim()
    if (id.isEmpty()) return
    val next =
        if (id in tagging.selectedPeerIds) {
            tagging.selectedPeerIds - id
        } else if (tagging.selectedPeerIds.size >= PROXIMITY_HOST_SELECTION_MAX_PEERS) {
            tagging.selectedPeerIds
        } else {
            tagging.selectedPeerIds + id
        }
    _connectionState.value = tagging.copy(selectedPeerIds = next)
    syncHostSelectionExclusions(tagging, next)
}

internal fun ConnectionViewModel.updateHostSelectionImpl(selectedPeerIds: Set<String>) {
    val tagging = _connectionState.value as? ConnectionState.TaggingContext ?: return
    if (!tagging.requiresSelection) return
    syncHostSelectionExclusions(tagging, selectedPeerIds)
}

/** Removed = candidates not selected; saved per change so whichever phone confirms honours it. */
internal fun ConnectionViewModel.syncHostSelectionExclusions(
    tagging: ConnectionState.TaggingContext,
    selected: Set<String>,
) {
    val candidates =
        tagging.selectableUsers
            .ifEmpty { tagging.targetUsers }
            .map { it.id }
            .toSet()
    val excluded = proximityExcludedMemberIds(candidates, selected)
    hostSelectionCurrent = selected
    hostSelectionExcluded = excluded
    val pendingId = tagging.pendingHandshakeId?.trim()?.takeIf { it.isNotEmpty() } ?: return
    saveProximityExclusions(pendingId, excluded)
}

/** The ids a review removed: every candidate that is not selected. */
internal fun proximityExcludedMemberIds(
    candidateIds: Set<String>,
    selectedIds: Set<String>,
): Set<String> = candidateIds.filterNot { it in selectedIds }.toSet()

/** Chains `/proximity/selection` saves so they land in order; failures are best effort (iOS `try?`). */
internal fun ConnectionViewModel.saveProximityExclusions(
    pendingHandshakeId: String,
    excludedIds: Set<String>,
) {
    val previous = proximityExclusionsJob
    proximityExclusionsJob =
        viewModelScope.launch {
            previous?.join()
            withContext(Dispatchers.Default) {
                repository.saveProximitySelectionExclusions(pendingHandshakeId, excludedIds)
            }
        }
}

/**
 * Promote legacy multi-peer [ConnectionState.PendingConfirmation] into the combined
 * people + tags [ConnectionState.TaggingContext] (host selection) sheet.
 */
internal fun ConnectionViewModel.promotePendingConfirmationToHostSelectionImpl(currentUserId: String) {
    val pending = _connectionState.value as? ConnectionState.PendingConfirmation ?: return
    val peers = pending.users.filter { it.id.isNotBlank() && it.id != currentUserId }
    if (peers.size < 2) return
    val profiles = peers.map { it.toUserProfile() }.take(PROXIMITY_HOST_SELECTION_MAX_PEERS)
    _connectionState.value =
        ConnectionState.TaggingContext(
            newConnections = emptyList(),
            targetUsers = profiles,
            isGroup = peers.size >= 2,
            memberUserIds = (listOf(currentUserId) + profiles.map { it.id }).distinct().sorted(),
            bindEncounterPersistedPeerIds =
                peers
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
internal fun ConnectionViewModel.confirmHostProximitySelectionImpl(
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
    val selected =
        selectedPeerIds
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != currentUserId }
            .distinct()
            .take(PROXIMITY_HOST_SELECTION_MAX_PEERS)
    if (selected.isEmpty()) {
        _connectionState.value = ConnectionState.Error("Select at least one person to connect with")
        return
    }
    val pendingId = tagging.pendingHandshakeId?.trim().orEmpty()
    val contextTagIds =
        contextTag?.let { tag ->
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
                val outcome =
                    withContext(Dispatchers.Default) {
                        repository.confirmProximitySelection(
                            pendingHandshakeId = pendingId,
                            selectedMemberIds = selected,
                            contextTags = contextTagIds,
                        )
                    }.getOrElse { err ->
                        // G4-5: another phone in the same tap may have confirmed first; its match includes us.
                        val jwt =
                            runCatching {
                                compose.project.click.click.data.auth.EnsureFreshAccessToken
                                    .get(repository.tokenStorage)
                            }.getOrNull() // pragma: allowlist secret
                        val recovered =
                            jwt?.takeIf { it.isNotBlank() }?.let {
                                withContext(Dispatchers.Default) { repository.recoverPendingProximityHandshake(it, pendingId) }.getOrNull()
                            }
                        if (recovered is compose.project.click.click.data.repository.BindProximityHandshakeResult.InstantMatch) {
                            finishAfterOtherConfirmed(recovered.outcome, currentUserId)
                            return@launch
                        }
                        _connectionState.value =
                            ConnectionState.Error(
                                err.message ?: "Failed to confirm proximity selection",
                            )
                        return@launch
                    }
                activateCollaborationSessionIfPresent(outcome)
                lastProximityEncounterLoggedAggregate = outcome.encounterLogged
                val matchedPeers =
                    outcome.matches
                        .filter { it.id.isNotBlank() && it.id != currentUserId }
                val profiles =
                    matchedPeers.map { it.toUserProfile() }.ifEmpty {
                        tagging.selectableUsers.filter { it.id in selected }
                    }
                val connectionId = outcome.connectionId
                val created =
                    if (!connectionId.isNullOrBlank()) {
                        val memberIds =
                            outcome.groupCliqueCandidateMemberIds
                                ?.takeIf { it.isNotEmpty() }
                                ?: (listOf(currentUserId) + selected).distinct().sorted()
                        val connection =
                            syntheticProximityConnection(
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
                    _transientNotice.tryEmit(ConnectionViewModel.RECONNECTION_ENCOUNTER_COOLDOWN_MESSAGE)
                    return@launch
                }
                if (created.isEmpty()) {
                    _connectionState.value = ConnectionState.Error("Connection was not created")
                    return@launch
                }
                // Host + ≥2 selected peers (member set ≥3): find or create the group chat on every
                // phone (lowest member ID creates; others wait, then create) — new and existing groups.
                if (selected.size >= 2) {
                    val selectedUsers =
                        matchedPeers.filter { it.id in selected }.ifEmpty {
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
                    if (outcome.isAggregateNewConnection) {
                        ConnectionFlowTelemetry.recordVerifiedCliqueCreated(
                            peerCount = selected.size,
                            selectedCount = selected.size,
                            candidateCount =
                                tagging.selectableUsers.size.takeIf { it > 0 }
                                    ?: tagging.targetUsers.size,
                        )
                    }
                    prepareGroupAfterTap(
                        currentUserId = currentUserId,
                        groupMemberIds = created.first().user_ids,
                        peers = selectedUsers.toGroupAfterTapPeers(),
                    )
                }
                // Tags already sent with confirm when present; skip second tagging sheet.
                if (!contextTagIds.isNullOrEmpty()) {
                    val primary = created.first()
                    // G0-15: confirm tags only the group connection; each pair timeline gets them too.
                    if (selected.size >= 2 && contextTag != null) {
                        val members = primary.user_ids
                        ConnectionBackgroundScope.launch {
                            val peers = members.filter { it != currentUserId }
                            val pairs =
                                runCatching { repository.fetchPairConnectionIds(currentUserId, peers) }.getOrNull()
                                    ?: return@launch
                            for (pairId in pairs.values.distinct()) {
                                repository.updateConnectionTags(
                                    connectionId = pairId,
                                    reportingUserId = currentUserId,
                                    contextTag = contextTag,
                                    noiseLevelCategory = sensorContext?.noiseLevelCategory,
                                    exactNoiseLevelDb = sensorContext?.exactNoiseLevelDb,
                                    heightCategory = null,
                                    exactBarometricElevationMeters = null,
                                )
                            }
                        }
                    }
                    _connectionState.value =
                        ConnectionState.Success(
                            primary,
                            syntheticUserForProximitySuccess(profiles),
                        )
                } else {
                    _connectionState.value =
                        ConnectionState.TaggingContext(
                            newConnections = created,
                            targetUsers = profiles,
                            isGroup = outcome.isGroup || profiles.size >= 2,
                            memberUserIds = created.first().user_ids,
                            bindEncounterPersistedPeerIds =
                                matchedPeers
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
    val peerUsers =
        tagging.selectableUsers
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
        ConnectionFlowTelemetry.recordVerifiedCliqueCreated(
            peerCount = selected.size,
            selectedCount = selected.size,
            candidateCount =
                tagging.selectableUsers.size.takeIf { it > 0 }
                    ?: tagging.targetUsers.size,
        )
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
internal fun ConnectionViewModel.saveContextTagsImpl(
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
) {
    viewModelScope.launch {
        val connections = tagging.newConnections
        val targetProfiles = tagging.targetUsers
        if (connections.isEmpty()) {
            _connectionState.value = ConnectionState.Idle
            return@launch
        }
        try {
            val sensorsMissing =
                noiseLevelCategory == null &&
                    exactNoiseLevelDb == null &&
                    heightCategory == null &&
                    exactBarometricElevationMeters == null
            val snapshot =
                if (
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
            val directIds = connections.filterNot { it.isPendingSync() }.map { it.id }
            // G0-15: a group's tags and noise also go to the viewer's pair connection with each
            // member. A failed lookup fails the save visibly (sheet stays open) and isn't cached.
            val targetIds =
                if (tagging.isGroup && selfId != null) {
                    val members =
                        (tagging.memberUserIds + targetProfiles.map { it.id } + selfId).distinct()
                    val pairs =
                        runCatching {
                            resolveGroupPairConnectionIds(selfId, members)
                        }.getOrElse { err ->
                            if (err is kotlinx.coroutines.CancellationException) throw err
                            // Noise is best effort: it still reaches the Click's own connections.
                            if (noiseOut != null || exactDbOut != null) {
                                ConnectionBackgroundScope.launch {
                                    for (id in directIds) {
                                        repository.updateConnectionTags(
                                            connectionId = id,
                                            reportingUserId = selfId,
                                            contextTag = null,
                                            noiseLevelCategory = noiseOut,
                                            exactNoiseLevelDb = exactDbOut,
                                            heightCategory = heightOut,
                                            exactBarometricElevationMeters = baroOut,
                                        )
                                    }
                                }
                            }
                            _connectionState.value = tagging.copy(tagSaveError = GROUP_TAG_TARGETS_FAILED_MESSAGE)
                            return@launch
                        }
                    groupTagTargetIds(directIds, pairs)
                } else {
                    directIds
                }
            for ((index, connectionId) in targetIds.withIndex()) {
                val patch =
                    withContext(Dispatchers.Default) {
                        repository.updateConnectionTags(
                            connectionId = connectionId,
                            reportingUserId = selfId,
                            contextTag = contextTag,
                            noiseLevelCategory = noiseOut,
                            exactNoiseLevelDb = exactDbOut,
                            heightCategory = heightOut,
                            exactBarometricElevationMeters = baroOut,
                        )
                    }
                // A member's own row can predate this tap (merged into an earlier hangout).
                if (patch.isFailure && index > 0 && isNoActiveEncounterFailure(patch.exceptionOrNull())) continue
                if (patch.isFailure) {
                    _connectionState.value =
                        ConnectionState.Error(
                            patch.exceptionOrNull()?.message ?: "Failed to save context",
                        )
                    return@launch
                }
            }
            if (!connections.any { it.isPendingSync() }) {
                AppDataManager.refresh(force = true)
            }
            if (selfId != null && tagging.isGroup) {
                // Idempotent: the group result already started this; it no-ops while in flight.
                prepareGroupAfterTap(
                    currentUserId = selfId,
                    groupMemberIds = tagging.memberUserIds,
                    peers = targetProfiles.profilesToGroupAfterTapPeers(),
                )
            }
            val primary = connections.first()
            val summaryUser = syntheticUserForProximitySuccess(targetProfiles)
            val stillTagging = _connectionState.value as? ConnectionState.TaggingContext
            val sameBatch =
                stillTagging?.newConnections?.map { it.id }?.toSet() ==
                    connections.map { it.id }.toSet()
            if (stillTagging == null || sameBatch) {
                _connectionState.value = ConnectionState.Success(primary, summaryUser)
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
        }
    }
}

/**
 * Connect with a user via QR code scan or confirmed proximity match.
 *
 * @param connectionMethod "qr", "proximity", or legacy "nfc"
 * @param initiatorId When null, derived for qr / proximity / nfc from [scannedUserId] / [currentUserId].
 */
internal fun ConnectionViewModel.connectWithUserImpl(
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

            val resolvedInitiator =
                initiatorId ?: when (connectionMethod) {
                    "qr" -> scannedUserId
                    "proximity", "nfc" -> scannedUserId
                    else -> null
                }
            val resolvedResponder =
                responderId ?: when (connectionMethod) {
                    "qr" -> currentUserId
                    "proximity", "nfc" -> currentUserId
                    else -> null
                }

            // G4-1..3: QR / link — this phone's fix at the recognition moment, with its quality
            // keys and sensor_observation (venue codes never send this phone's location).
            if (connectionMethod == "qr") ConnectionFlowTelemetry.recordQrStarted(captureSession?.method ?: "link")
            val captured =
                if (connectionMethod == "qr") {
                    runCatching {
                        captureConnectContext(
                            method = captureSession?.method ?: "link",
                            momentMs =
                                qrScanMomentMs ?: kotlinx.datetime.Clock.System
                                    .now()
                                    .toEpochMilliseconds(),
                            includeLocation = venueId.isNullOrBlank() && AppDataManager.shouldCaptureLocationAtTap(),
                            cold = captureSession?.isRunning != true,
                        )
                    }.getOrNull()
                } else {
                    null
                }
            qrScanMomentMs = null
            // The scan is over; the follow-up (if any) runs its own sensors.
            if (connectionMethod == "qr") stopConnectionCapture()
            val capturedFix = captured?.fix
            val locLat = capturedFix?.latitude ?: latitude ?: lastProximityLat
            val locLng = capturedFix?.longitude ?: longitude ?: lastProximityLng
            val locAlt = altitudeMeters ?: lastProximityAltitudeMeters
            val qrHardwareVibe =
                when (connectionMethod) {
                    "qr" ->
                        hardwareVibeOverride ?: withContext(Dispatchers.Default) {
                            runCatching { HardwareVibeMonitor().takeSnapshot() }.getOrNull()
                        }
                    else -> null
                }
            val requestHardwareVibe = qrHardwareVibe ?: lastProximityHardwareVibe

            val request =
                ConnectionRequest(
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
                    observationKeys = captured?.observationKeys,
                )

            val result =
                withContext(Dispatchers.Default) {
                    repository.createConnection(request)
                }

            if (result.isSuccess) {
                // G4-7: tighten the scan's fix while still; confirmed once the result state lands.
                captured?.let { beginEncounterFollowUp(it) }
                val outcome = result.getOrNull()!!
                val connection = outcome.connection
                val encounterLogged = outcome.encounterLogged
                if (connectionMethod == "qr") {
                    if (encounterLogged) {
                        ConnectionFlowTelemetry.recordQrSucceeded(isReconnect = null, captureQuality = lastCaptureQuality)
                    } else {
                        ConnectionFlowTelemetry.recordQrRateLimited(captureQuality = lastCaptureQuality)
                    }
                }
                activateCollaborationSessionIfPresent(outcome)
                val connectedUserId = connection.user_ids.firstOrNull { it != currentUserId } ?: scannedUserId
                val connectedUser =
                    withContext(Dispatchers.Default) {
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
                        _transientNotice.tryEmit(ConnectionViewModel.RECONNECTION_ENCOUNTER_COOLDOWN_MESSAGE)
                        if (connection.isPendingSync()) {
                            _connectionState.value = ConnectionState.Success(connection, connectedUser)
                        } else if (
                            connectionMethod == "qr" &&
                            contextTagObject == null &&
                            contextTag.isNullOrBlank()
                        ) {
                            _connectionState.value =
                                ConnectionState.TaggingContext(
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
                        _connectionState.value =
                            ConnectionState.TaggingContext(
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
                if (connectionMethod == "qr") {
                    ConnectionFlowTelemetry.recordQrFailed(ConnectionFlowTelemetry.qrFailureReason(error), lastCaptureQuality)
                }
                _connectionState.value = ConnectionState.Error(error)
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
        }
    }
}
