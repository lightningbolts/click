@file:Suppress("ktlint:standard:max-line-length")

package compose.project.click.click.data // pragma: allowlist secret

import compose.project.click.click.data.api.CommunityHubNearbyDto // pragma: allowlist secret
import compose.project.click.click.data.models.CachedAppSnapshot // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.LocationPreferences // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.UserAvailability // pragma: allowlist secret
import compose.project.click.click.data.models.isOneToOnePairEdge // pragma: allowlist secret
import compose.project.click.click.data.models.isResolvedDisplayName // pragma: allowlist secret
import compose.project.click.click.data.realtime.RealtimeCoordinator // pragma: allowlist secret
import compose.project.click.click.data.repository.NotificationPreferences // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.notifications.NotificationRuntimeState // pragma: allowlist secret
import compose.project.click.click.ui.utils.CommunityHubPin // pragma: allowlist secret
import compose.project.click.click.ui.utils.mergeCommunityHubLists // pragma: allowlist secret
import compose.project.click.click.util.ViewerAvailabilityBubblesCache // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** Local SSOT merge for community hubs; persisted in [CachedAppSnapshot] for offline cold start. */
internal fun AppDataManager.mergeCachedCommunityHubsFromDtoImpl(incoming: List<CommunityHubNearbyDto>) {
    if (incoming.isEmpty()) return
    val existingPins =
        _prefetchedCommunityHubs.value.map { dto ->
            CommunityHubPin(
                hubId = dto.hubId,
                name = dto.name,
                latitude = dto.latitude,
                longitude = dto.longitude,
                radiusMeters = dto.radiusMeters,
                activeUserCount = dto.activeUserCount,
                reportedDistanceMeters = dto.distanceMeters,
            )
        }
    val incomingPins =
        incoming.map { dto ->
            CommunityHubPin(
                hubId = dto.hubId,
                name = dto.name,
                latitude = dto.latitude,
                longitude = dto.longitude,
                radiusMeters = dto.radiusMeters,
                activeUserCount = dto.activeUserCount,
                reportedDistanceMeters = dto.distanceMeters,
            )
        }
    val merged = mergeCommunityHubLists(existingPins, incomingPins)
    _prefetchedCommunityHubs.value =
        merged.map { pin ->
            CommunityHubNearbyDto(
                hubId = pin.hubId,
                name = pin.name,
                latitude = pin.latitude,
                longitude = pin.longitude,
                radiusMeters = pin.radiusMeters,
                activeUserCount = pin.activeUserCount,
                distanceMeters = pin.reportedDistanceMeters ?: 0.0,
            )
        }
    scope.launch { persistSnapshot() }
}

/**
 * Clear all data (on logout)
 */
internal suspend fun AppDataManager.clearDataImpl() {
    loadAllDataJob?.cancel()
    loadAllDataJob = null
    chatPrefetchJob?.cancel()
    chatPrefetchJob = null
    aggressiveBackgroundChatSyncJob?.cancel()
    aggressiveBackgroundChatSyncJob = null
    realtimeCoordinatorJob?.cancel()
    realtimeCoordinatorJob = null
    RealtimeCoordinator.stop()
    beaconPrefetchedThisSession = false
    discoveryPrefetchEmptyRetryUsed = false
    silentChatPrefetchCompleted = false
    lastSyncedInboxVersion = 0L
    profilePrefetchJob?.cancel()
    profilePrefetchJob = null
    beaconPrefetchJob?.cancel()
    beaconPrefetchJob = null
    _prefetchedMapBeacons.value = emptyList()
    _prefetchedCommunityHubs.value = emptyList()
    _cachedEventBookmarks.value = emptyList()
    _discoveryMapPrefetchComplete.value = false
    _lastKnownDeviceLocation.value = null
    queuedProfilePrefetchIds = emptySet()
    persistSnapshotJob?.cancel()
    persistSnapshotJob = null
    SupabaseRepository.resetStaleConnectionSweepSchedule()
    ViewerAvailabilityBubblesCache.clear()
    // R0.5: clearSessionCaches disposes all ephemeral channels AND zero-fills
    // group master keys AND stops global presence, so this single call
    // replaces the old stopGlobalPresence() + leaks derived keys into the
    // next signed-in user of the same device.
    runCatching { chatRepository.clearSessionCaches() }
        .onFailure { e -> println("AppDataManager: chat session cache clear failed: ${e.redactedRestMessage()}") }
    presenceHeartbeatJob?.cancel()
    presenceHeartbeatJob = null
    _currentUser.value = null
    _userInterestTags.value = emptyList()
    _connections.value = emptyList()
    _archivedConnectionIds.value = emptySet()
    _hiddenConnectionIds.value = emptySet()
    _coreConnectionIds.value = emptySet()
    _connectedUsers.value = emptyMap()
    _cachedChatThreads.value = emptyMap()
    _cachedHubThreads.value = emptyMap()
    _inboxFeedChats.value = emptyList()
    groupInboxHydratedThisSession = false
    supabaseRepository.clearCachedUserPublicProfiles()
    supabaseRepository.clearCachedProfileTimelines()
    _userAvailability.value = null
    _isDataLoaded.value = false
    _isLoading.value = false
    _error.value = null
    _notificationPreferences.value = NotificationPreferences()
    _locationPreferences.value = LocationPreferences()
    _pendingConnectionsCount.value = 0
    NotificationRuntimeState.setNotificationPreferences(
        messageEnabled = true,
        callEnabled = true,
        eventReminderEnabled = true,
        availabilityMatchEnabled = true,
        hubMessageEnabled = true,
    )
}

/**
 * Update the current user's first and last name in auth metadata and [public.users].
 */
internal fun AppDataManager.updateProfileNameImpl(
    firstName: String,
    lastName: String,
) {
    val user =
        _currentUser.value ?: run {
            println("updateProfileName: No current user")
            return
        }

    val f = firstName.trim()
    val l = lastName.trim()
    if (f.isEmpty()) {
        println("updateProfileName: First name is required")
        return
    }

    val display = listOf(f, l).filter { it.isNotEmpty() }.joinToString(" ")
    println("updateProfileName: Changing profile to '$display' for user ${user.id}")

    val previousUser = user
    val updatedUser =
        user.copy(
            name = display,
            firstName = f,
            lastName = l.ifEmpty { null },
        )
    _currentUser.value = updatedUser

    scope.launch {
        try {
            val authResult = authRepository.updateUserProfileNames(f, l)
            if (authResult.isFailure) {
                println("updateProfileName: Warning - failed to update auth metadata")
            }

            val dbResult = supabaseRepository.updateUserProfileNames(user.id, f, l)
            if (dbResult.isFailure) {
                println("updateProfileName: Failed to update names in database: ${dbResult.exceptionOrNull()?.message}")
                _currentUser.value = previousUser
                val msg =
                    dbResult
                        .exceptionOrNull()
                        ?.message
                        ?.trim()
                        .orEmpty()
                        .ifBlank { "Couldn't update your profile. Please try again." }
                _transientUserMessages.emit(msg)
            } else {
                println("updateProfileName: Successfully updated profile to: $display")
                schedulePersistSnapshot()
            }
        } catch (e: Exception) {
            println("updateProfileName: Error updating profile: ${e.redactedRestMessage()}")
            e.printStackTrace()
            _currentUser.value = previousUser
            _transientUserMessages.emit(
                e.message
                    ?.trim()
                    .orEmpty()
                    .ifBlank { "Couldn't update your profile. Please try again." },
            )
        }
    }
}

/**
 * Update connections list (after making a new connection).
 * Upserts by id; for 1:1 pair edges, replaces any older active pair-edge for the same peer
 * so reconnects do not grow duplicate DM rows.
 */
internal fun AppDataManager.addConnectionImpl(
    connection: Connection,
    otherUser: User? = null,
) {
    val currentUserId = _currentUser.value?.id
    var list = _connections.value
    if (connection.isOneToOnePairEdge() &&
        connection.isInActiveConnectionsChannel() &&
        !currentUserId.isNullOrBlank()
    ) {
        val peerId = connection.user_ids.firstOrNull { it != currentUserId }
        if (!peerId.isNullOrBlank()) {
            // Retire every other active-channel pair-edge for this peer (pending/active/kept),
            // including edges whose status string is blank/legacy-null treated as active.
            list =
                list.filterNot { existing ->
                    if (existing.id == connection.id) return@filterNot false
                    if (!existing.isOneToOnePairEdge()) return@filterNot false
                    if (!existing.isInActiveConnectionsChannel()) return@filterNot false
                    existing.user_ids.firstOrNull { it != currentUserId } == peerId
                }
        }
    }
    publishConnections(list + connection)

    val otherUserId =
        currentUserId?.let { currentId ->
            connection.user_ids.firstOrNull { it != currentId }
        }

    if (otherUser != null && otherUserId != null && otherUser.id == otherUserId) {
        val existingUser = _connectedUsers.value[otherUser.id]
        val preferredUser =
            when {
                isResolvedDisplayName(otherUser.name) -> otherUser
                existingUser != null -> existingUser
                else -> otherUser
            }
        _connectedUsers.value = _connectedUsers.value + (otherUser.id to preferredUser)
    }

    if (currentUserId != null && otherUserId != null) {
        if (_connectedUsers.value[otherUserId] == null) {
            _connectedUsers.value = _connectedUsers.value + (
                otherUserId to User(id = otherUserId, name = "Connection", createdAt = 0L)
            )
        }

        scope.launch {
            refreshConnectedUsers(_connections.value, currentUserId)
        }
    }

    seedJunctionCacheFromMemory()
    scope.launch {
        schedulePersistSnapshot()
    }
}

/**
 * Toggle free this week status
 * This method:
 * 1. Updates local state for immediate UI feedback
 * 2. Saves to local storage immediately (persists even if app is killed)
 * 3. Syncs with backend in background
 */
internal fun AppDataManager.toggleFreeThisWeekImpl() {
    val user =
        _currentUser.value ?: run {
            println("toggleFreeThisWeek: No current user")
            return
        }
    val current = _userAvailability.value
    val newStatus = !(current?.isFreeThisWeek ?: false)

    println("toggleFreeThisWeek: Toggling from ${current?.isFreeThisWeek} to $newStatus for user ${user.id}")

    val updated =
        current?.copy(
            isFreeThisWeek = newStatus,
            lastUpdated = Clock.System.now().toEpochMilliseconds(),
        ) ?: UserAvailability(
            userId = user.id,
            isFreeThisWeek = newStatus,
            lastUpdated = Clock.System.now().toEpochMilliseconds(),
        )

    // Update local state first for immediate UI feedback
    _userAvailability.value = updated

    // Save to local storage immediately (this is fast and synchronous-ish)
    // This ensures the value persists even if the app is killed before network call completes
    scope.launch(Dispatchers.Default) {
        try {
            tokenStorage.saveFreeThisWeek(newStatus)
            println("toggleFreeThisWeek: Saved to local storage: $newStatus")
        } catch (e: Exception) {
            println("toggleFreeThisWeek: Error saving to local storage: ${e.redactedRestMessage()}")
        }
    }

    // Sync with backend
    scope.launch(Dispatchers.Default) {
        try {
            val result = supabaseRepository.setFreeThisWeek(user.id, newStatus)
            println("toggleFreeThisWeek: Supabase update result: $result")
            // Note: We don't rollback on failure since local storage has the truth
            // Next app launch will attempt to sync again
        } catch (e: Exception) {
            println("toggleFreeThisWeek: Error updating Supabase: ${e.redactedRestMessage()}")
            e.printStackTrace()
            // Don't rollback - keep local state as truth, will sync later
        }
    }
}
