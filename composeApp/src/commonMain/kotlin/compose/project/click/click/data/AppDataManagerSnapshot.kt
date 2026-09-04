@file:Suppress("ktlint:standard:max-line-length")

package compose.project.click.click.data // pragma: allowlist secret

import compose.project.click.click.data.api.CommunityHubNearbyDto // pragma: allowlist secret
import compose.project.click.click.data.api.toEventBookmarkItemDto // pragma: allowlist secret
import compose.project.click.click.data.api.toStoredEventBookmark // pragma: allowlist secret
import compose.project.click.click.data.models.CachedAppSnapshot // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.HomeLayoutMode // pragma: allowlist secret
import compose.project.click.click.data.models.StoredCommunityHubPin // pragma: allowlist secret
import compose.project.click.click.data.models.richerConnectionEncounters // pragma: allowlist secret
import compose.project.click.click.data.models.shouldPreserveLocalConnectionJunctions // pragma: allowlist secret
import compose.project.click.click.data.models.toMapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.toStoredMapBeacon // pragma: allowlist secret
import compose.project.click.click.data.repository.NotificationPreferences // pragma: allowlist secret
import compose.project.click.click.data.repository.UserConnectionsSnapshot // pragma: allowlist secret
import compose.project.click.click.notifications.NotificationRuntimeState // pragma: allowlist secret
import compose.project.click.click.util.dedupeOneToOneChatsByPeer // pragma: allowlist secret
import compose.project.click.click.util.dedupeOneToOneConnectionsByPeer // pragma: allowlist secret
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Apply server connection snapshot. Preserves locally cached rows when the server
 * returns none but cold-start restore already had data (offline network failure).
 * Empty junction sets under an empty connection fetch are treated as RLS/auth poison
 * and must not wipe optimistic / offline archive + core pins.
 */
internal fun AppDataManager.applyFetchedConnectionSnapshot(snapshot: UserConnectionsSnapshot) {
    val localConnections = _connections.value
    val preserveJunctions =
        shouldPreserveLocalConnectionJunctions(
            localConnectionCount = localConnections.size,
            snapshotConnectionCount = snapshot.connections.size,
            snapshotArchivedCount = snapshot.archivedConnectionIds.size,
            snapshotHiddenCount = snapshot.hiddenConnectionIds.size,
        )
    val merged =
        when {
            snapshot.connections.isNotEmpty() -> {
                val localById = localConnections.associateBy { it.id }
                snapshot.connections.map { server ->
                    mergeConnectionSnapshotWithLocal(localById[server.id], server)
                }
            }
            localConnections.isNotEmpty() -> localConnections
            else -> snapshot.connections
        }
    publishConnections(merged)
    if (!preserveJunctions) {
        _archivedConnectionIds.value = snapshot.archivedConnectionIds
        _hiddenConnectionIds.value = snapshot.hiddenConnectionIds
    }
    val serverCore = snapshot.coreConnectionIds
    _coreConnectionIds.value =
        when {
            preserveJunctions -> _coreConnectionIds.value
            snapshot.coreConnectionIdsAuthoritative && serverCore.isEmpty() && _coreConnectionIds.value.isNotEmpty() ->
                _coreConnectionIds.value
            snapshot.coreConnectionIdsAuthoritative -> serverCore
            serverCore.isNotEmpty() -> serverCore
            else -> _coreConnectionIds.value
        }
    seedJunctionCacheFromMemory()
    notifyInboxVersionSynced()
}

/**
 * Write [connections] into SSOT after collapsing duplicate 1:1 rows for the same peer.
 */
internal fun AppDataManager.publishConnections(connections: List<Connection>) {
    val viewerId = _currentUser.value?.id.orEmpty()
    _connections.value = dedupeOneToOneConnectionsByPeer(viewerId, connections)
}

/**
 * Preserve locally patched preview text when a junction refresh returns fresher
 * [Connection.last_message_at] without embedded [Chat.messages].
 */
internal fun AppDataManager.mergeConnectionSnapshotWithLocal(
    local: Connection?,
    server: Connection,
): Connection {
    if (local == null) return server
    val localPreview = local.chat.messages.lastOrNull()
    val serverPreview = server.chat.messages.lastOrNull()
    val mergedAt = listOfNotNull(local.last_message_at, server.last_message_at).maxOrNull()
    val bestPreview =
        when {
            localPreview == null -> serverPreview
            serverPreview == null -> localPreview
            localPreview.timeCreated >= serverPreview.timeCreated -> localPreview
            else -> serverPreview
        }
    val previewMsg =
        bestPreview?.takeIf { msg ->
            mergedAt == null || msg.timeCreated >= mergedAt
        }
    val mergedEncounters =
        richerConnectionEncounters(
            local.connectionEncounters,
            server.connectionEncounters,
        )
    return server.copy(
        last_message_at = mergedAt,
        chat =
            server.chat.copy(
                messages = previewMsg?.let { listOf(it) } ?: emptyList(),
            ),
        connectionEncounters = mergedEncounters,
    )
}

internal fun AppDataManager.seedJunctionCacheFromMemory() {
    val userId = _currentUser.value?.id ?: return
    chatRepository.seedConnectionJunctionCache(
        userId = userId,
        connections = _connections.value,
        archivedConnectionIds = _archivedConnectionIds.value,
        hiddenConnectionIds = _hiddenConnectionIds.value,
    )
}

internal fun AppDataManager.schedulePersistSnapshot() {
    persistSnapshotJob?.cancel()
    persistSnapshotJob =
        scope.launch {
            delay(PERSIST_SNAPSHOT_DEBOUNCE_MS)
            persistSnapshot()
        }
}

internal suspend fun AppDataManager.restoreCachedSnapshot(): Boolean =
    snapshotRestoreMutex.withLock {
        _hubAccessStateRestored.value = false
        try {
            restoreCachedSnapshotUnlocked()
        } finally {
            _hubAccessStateRestored.value = true
        }
    }

private suspend fun AppDataManager.restoreCachedSnapshotUnlocked(): Boolean {
    val snapshotJson = tokenStorage.getCachedAppSnapshot()
    if (snapshotJson.isNullOrBlank()) return false

    return runCatching {
        val snapshot = json.decodeFromString<CachedAppSnapshot>(snapshotJson)
        _currentUser.value = snapshot.currentUser
        _userInterestTags.value = snapshot.currentUser?.tags.orEmpty()
        _connections.value =
            dedupeOneToOneConnectionsByPeer(
                viewerUserId = snapshot.currentUser?.id.orEmpty(),
                connections = snapshot.connections,
            )
        _connectedUsers.value = snapshot.connectedUsers.associateBy { it.id }
        _locationPreferences.value = snapshot.locationPreferences
        _archivedConnectionIds.value = snapshot.archivedConnectionIds
        _hiddenConnectionIds.value = snapshot.hiddenConnectionIds
        _coreConnectionIds.value = snapshot.coreConnectionIds
        _revokedHubIds.value = boundedHubAccessRevocationIds(snapshot.revokedHubIds)
        _cachedChatThreads.value = snapshot.cachedChatThreads.associateBy { it.connectionId }
        _cachedHubThreads.value = snapshot.cachedHubThreads.associateBy { it.hubId }
        _inboxFeedChats.value = dedupeOneToOneChatsByPeer(snapshot.inboxFeedChats)
        if (snapshot.cachedMapBeacons.isNotEmpty()) {
            // Drop null-island rows poisoned by GET /api/beacons/{id} location-parse fallback.
            val restored =
                snapshot.cachedMapBeacons
                    .map { it.toMapBeacon() }
                    .filter { beacon ->
                        beacon.latitude.isFinite() &&
                            beacon.longitude.isFinite() &&
                            !(beacon.latitude == 0.0 && beacon.longitude == 0.0)
                    }
            _prefetchedMapBeacons.value = restored
            if (restored.size < snapshot.cachedMapBeacons.size) {
                schedulePersistSnapshot()
            }
        }
        if (snapshot.cachedCommunityHubs.isNotEmpty()) {
            _prefetchedCommunityHubs.value =
                snapshot.cachedCommunityHubs.map { hub ->
                    CommunityHubNearbyDto(
                        hubId = hub.hubId,
                        name = hub.name,
                        latitude = hub.latitude,
                        longitude = hub.longitude,
                        radiusMeters = hub.radiusMeters,
                        activeUserCount = hub.activeUserCount,
                        distanceMeters = hub.reportedDistanceMeters ?: 0.0,
                    )
                }
        }
        if (snapshot.cachedEventBookmarks.isNotEmpty()) {
            _cachedEventBookmarks.value =
                snapshot.cachedEventBookmarks.map { it.toEventBookmarkItemDto() }
        }
        supabaseRepository.seedCachedUserPublicProfiles(snapshot.cachedUserPublicProfiles)
        supabaseRepository.seedCachedProfileTimelines(snapshot.cachedProfileTimelines)
        applyRestoredSnapshotFreshness(snapshot)
        _isDataLoaded.value =
            snapshot.currentUser != null ||
            snapshot.connections.isNotEmpty() ||
            snapshot.inboxFeedChats.isNotEmpty()
        seedJunctionCacheFromMemory()
        snapshot
    }.onFailure {
        println("AppDataManager: Failed to restore cached snapshot: ${it.message}")
    }.isSuccess
}

internal fun AppDataManager.applyRestoredSnapshotFreshness(snapshot: CachedAppSnapshot) {
    val hasCache =
        snapshot.inboxFeedChats.isNotEmpty() ||
            snapshot.connections.isNotEmpty() ||
            snapshot.cachedChatThreads.isNotEmpty()
    if (!hasCache) return
    lastRefreshTime = Clock.System.now().toEpochMilliseconds()
    notifyInboxVersionSynced()
}

internal suspend fun AppDataManager.persistSnapshot() {
    val snapshot =
        CachedAppSnapshot(
            currentUser = _currentUser.value,
            connections = _connections.value,
            connectedUsers = _connectedUsers.value.values.toList(),
            locationPreferences = _locationPreferences.value,
            archivedConnectionIds = _archivedConnectionIds.value,
            hiddenConnectionIds = _hiddenConnectionIds.value,
            coreConnectionIds = _coreConnectionIds.value,
            cachedChatThreads = _cachedChatThreads.value.values.toList(),
            cachedHubThreads = _cachedHubThreads.value.values.toList(),
            revokedHubIds = _revokedHubIds.value,
            cachedUserPublicProfiles = supabaseRepository.snapshotCachedUserPublicProfiles(),
            cachedProfileTimelines = supabaseRepository.snapshotCachedProfileTimelines(),
            inboxFeedChats = _inboxFeedChats.value,
            cachedMapBeacons = _prefetchedMapBeacons.value.map { it.toStoredMapBeacon() },
            cachedCommunityHubs =
                _prefetchedCommunityHubs.value.map { dto ->
                    StoredCommunityHubPin(
                        hubId = dto.hubId,
                        name = dto.name,
                        latitude = dto.latitude,
                        longitude = dto.longitude,
                        radiusMeters = dto.radiusMeters,
                        activeUserCount = dto.activeUserCount,
                        reportedDistanceMeters = dto.distanceMeters,
                    )
                },
            cachedEventBookmarks = _cachedEventBookmarks.value.map { it.toStoredEventBookmark() },
            snapshotSavedAtMs = Clock.System.now().toEpochMilliseconds(),
        )
    runCatching {
        tokenStorage.saveCachedAppSnapshot(json.encodeToString(snapshot))
    }.onFailure {
        println("AppDataManager: Failed to persist cached snapshot: ${it.message}")
    }
}

internal fun AppDataManager.persistActiveHubs() {
    scope.launch {
        val json =
            if (_activeHubs.value.isEmpty()) {
                null
            } else {
                Json.encodeToString(_activeHubs.value)
            }
        runCatching { tokenStorage.saveActiveHubs(json) }
            .onFailure { println("AppDataManager: Failed to persist active hubs: ${it.message}") }
    }
}

internal suspend fun AppDataManager.restoreActiveHubs() {
    val json = tokenStorage.getActiveHubs() ?: return
    runCatching {
        _activeHubs.value = Json.decodeFromString<List<ActiveHubEntry>>(json)
    }.onFailure {
        println("AppDataManager: Failed to restore active hubs: ${it.message}")
        tokenStorage.saveActiveHubs(null)
    }
}

internal fun AppDataManager.restoreHomeLayoutMode() {
    scope.launch {
        _homeLayoutMode.value = HomeLayoutMode.fromStored(tokenStorage.getHomeLayoutMode())
    }
}

internal fun AppDataManager.updateNotificationPreferences(preferences: NotificationPreferences) {
    val userId = _currentUser.value?.id ?: return
    val previousPreferences = _notificationPreferences.value
    _notificationPreferences.value = preferences
    NotificationRuntimeState.setNotificationPreferences(
        messageEnabled = preferences.messagePushEnabled,
        callEnabled = preferences.callPushEnabled,
        eventReminderEnabled = preferences.eventReminderPushEnabled,
        availabilityMatchEnabled = preferences.availabilityMatchPushEnabled,
        hubMessageEnabled = preferences.hubMessagePushEnabled,
        eventTeaserEnabled = preferences.eventTeaserPushEnabled,
        reconnectNudgeEnabled = preferences.reconnectNudgePushEnabled,
    )

    scope.launch {
        tokenStorage.saveMessageNotificationsEnabled(preferences.messagePushEnabled)
        tokenStorage.saveCallNotificationsEnabled(preferences.callPushEnabled)
        val saveResult = notificationPreferencesRepository.savePreferences(userId, preferences)
        if (saveResult.isFailure) {
            _notificationPreferences.value = previousPreferences
            NotificationRuntimeState.setNotificationPreferences(
                messageEnabled = previousPreferences.messagePushEnabled,
                callEnabled = previousPreferences.callPushEnabled,
                eventReminderEnabled = previousPreferences.eventReminderPushEnabled,
                availabilityMatchEnabled = previousPreferences.availabilityMatchPushEnabled,
                hubMessageEnabled = previousPreferences.hubMessagePushEnabled,
                eventTeaserEnabled = previousPreferences.eventTeaserPushEnabled,
                reconnectNudgeEnabled = previousPreferences.reconnectNudgePushEnabled,
            )
            tokenStorage.saveMessageNotificationsEnabled(previousPreferences.messagePushEnabled)
            tokenStorage.saveCallNotificationsEnabled(previousPreferences.callPushEnabled)
            val msg =
                saveResult
                    .exceptionOrNull()
                    ?.message
                    ?.trim()
                    .orEmpty()
                    .ifBlank { "Couldn't save notification settings. Please try again." }
            _transientUserMessages.emit(msg)
            return@launch
        }

        if (preferences.messagePushEnabled || preferences.callPushEnabled) {
            runCatching { pushNotificationService.requestPermission() }
                .onFailure { println("AppDataManager: Push permission request failed after settings update: ${it.message}") }
            runCatching { pushNotificationService.registerToken(userId) }
                .onFailure { println("AppDataManager: Push token registration failed after settings update: ${it.message}") }
        }
    }
}
