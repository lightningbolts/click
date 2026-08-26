@file:Suppress("ktlint:standard:max-line-length")

package compose.project.click.click.data // pragma: allowlist secret

import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.isResolvedDisplayName // pragma: allowlist secret
import compose.project.click.click.data.repository.ProximityHandshakeRecoveryPayload // pragma: allowlist secret
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

internal fun AppDataManager.startPresenceHeartbeat(userId: String) {
    if (presenceHeartbeatJob?.isActive == true) return

    presenceHeartbeatJob =
        scope.launch {
            while (_currentUser.value?.id == userId) {
                if (!_ghostModeEnabled.value) {
                    val now = Clock.System.now().toEpochMilliseconds()
                    val jwt =
                        compose.project.click.click.data.auth.EnsureFreshAccessToken.get( // pragma: allowlist secret
                            tokenStorage = tokenStorage,
                            authRepository = authRepository,
                        )
                    if (jwt.isNullOrBlank()) {
                        println("AppDataManager: Skipping last_polled — no fresh JWT")
                    } else {
                        supabaseRepository.updateUserLastPolled(userId, now)
                        _currentUser.value = _currentUser.value?.copy(lastPolled = now)
                    }
                }
                delay(PRESENCE_HEARTBEAT_MS)
            }
        }
}

internal suspend fun AppDataManager.refreshConnectedUsers(
    connections: List<Connection>,
    currentUserId: String,
) {
    val otherUserIds =
        connections
            .flatMap { it.user_ids }
            .filter { it != currentUserId }
            .distinct()

    if (otherUserIds.isEmpty()) {
        _connectedUsers.value = emptyMap()
        return
    }

    val users = supabaseRepository.fetchUsersByIds(otherUserIds)
    val existingUsers = _connectedUsers.value
    val usersById = users.associateBy { it.id }

    _connectedUsers.value =
        otherUserIds.associateWith { userId ->
            val fetchedUser = usersById[userId]
            val existingUser = existingUsers[userId]

            when {
                fetchedUser != null && isResolvedDisplayName(fetchedUser.name) -> fetchedUser
                existingUser != null && isResolvedDisplayName(existingUser.name) -> existingUser
                fetchedUser != null -> fetchedUser
                existingUser != null -> existingUser
                else -> User(id = userId, name = "Connection", createdAt = 0L)
            }
        }

    // If any users are still unresolved after the fetch (e.g. Supabase cold start caused the
    // RPC to fail silently), schedule quick background retries so the UI updates within seconds
    // rather than waiting for the 30-second presence heartbeat.
    scheduleUnresolvedUserRetry()
}

/**
 * If the connected-users map still has any "Connection" placeholder names, retry name
 * resolution in the background at 2 s, then 8 s intervals so a cold-start RPC failure
 * doesn't leave placeholder names visible for a full heartbeat cycle (30 s).
 */
internal fun AppDataManager.scheduleUnresolvedUserRetry() {
    val unresolvedIds =
        _connectedUsers.value
            .entries
            .filter { !isResolvedDisplayName(it.value.name) }
            .map { it.key }

    if (unresolvedIds.isEmpty()) return

    scope.launch {
        // More aggressive retry schedule to bridge the gap until the 30-s heartbeat.
        for (delayMs in listOf(2_000L, 5_000L, 12_000L, 25_000L)) {
            delay(delayMs)
            // Stop if nothing left to resolve
            val stillUnresolved =
                _connectedUsers.value
                    .entries
                    .filter { !isResolvedDisplayName(it.value.name) }
                    .map { it.key }

            if (stillUnresolved.isEmpty()) break

            val retried = supabaseRepository.fetchUsersByIds(stillUnresolved)
            val currentMap = _connectedUsers.value.toMutableMap()
            var anyResolved = false
            retried.forEach { user ->
                if (isResolvedDisplayName(user.name)) {
                    currentMap[user.id] = user
                    anyResolved = true
                }
            }
            if (anyResolved) {
                _connectedUsers.value = currentMap
            }
        }
    }
}

internal fun AppDataManager.mapStartupErrorMessage(rawMessage: String): String {
    val trimmed = rawMessage.trim()
    val normalized = trimmed.lowercase()
    return when {
        normalized.contains("401") ||
            normalized.contains("403") ||
            normalized.contains("unauthorized") ||
            normalized.contains("not authorized") ||
            normalized.contains("invalid jwt") ->
            "Your session expired. Please sign in again to resume sync."
        trimmed.isBlank() -> "No internet connection"
        else -> trimmed
    }
}

internal fun Throwable.isAuthorizationFailure(): Boolean {
    val normalized = message?.lowercase().orEmpty()
    return normalized.contains("401") ||
        normalized.contains("403") ||
        normalized.contains("unauthorized") ||
        normalized.contains("not authorized") ||
        normalized.contains("invalid jwt") ||
        normalized.contains("jwt expired")
}

internal fun AppDataManager.pausePendingSyncForAuth() {
    if (!pendingSyncPausedForAuth) {
        println("AppDataManager: Pending sync paused until a valid auth session is restored.")
    }
    pendingSyncPausedForAuth = true
}

internal fun AppDataManager.resumePendingSyncIfPaused() {
    if (pendingSyncPausedForAuth) {
        println("AppDataManager: Pending sync resumed after auth recovery.")
    }
    pendingSyncPausedForAuth = false
}

internal suspend fun AppDataManager.resolveJwtForPendingSync(forceRefresh: Boolean = false): String? =
    compose.project.click.click.data.auth.EnsureFreshAccessToken // pragma: allowlist secret
        .get(
            tokenStorage = tokenStorage,
            authRepository = authRepository,
            forceRefresh = forceRefresh,
        ).also { jwt ->
            if (jwt.isNullOrBlank()) {
                println("AppDataManager: Session refresh for pending sync failed: no usable JWT")
            }
        }

internal fun AppDataManager.startNetworkConnectivityObserver() {
    if (networkConnectivityJob?.isActive == true) return
    networkConnectivityMonitor.start()
    var wasOnline = networkConnectivityMonitor.isOnline.value
    networkConnectivityJob =
        scope.launch {
            networkConnectivityMonitor.isOnline.collect { online ->
                if (online && !wasOnline) {
                    // Same JWT + Realtime recovery as foreground — offline→online alone used to
                    // leave stale sockets / expired JWTs until the user signed out.
                    recoverSessionAndRealtime(reason = "network_reconnect", forceDataRefresh = true)
                    runCatching { flushPendingProximityHandshakesFromBackgroundWorker() }
                        .onFailure {
                            println("AppDataManager: Encounter queue drain on reconnect failed: ${it.message}")
                        }
                    runCatching { connectionRepository.syncPendingConnections() }
                    val userId = _currentUser.value?.id
                    if (!userId.isNullOrBlank() && !silentChatPrefetchCompleted) {
                        startSilentChatPrefetch(userId)
                    }
                }
                wasOnline = online
            }
        }
}

internal fun AppDataManager.startPendingConnectionSync() {
    if (pendingSyncJob?.isActive == true) return

    pendingSyncJob =
        scope.launch {
            while (true) {
                delay(PENDING_SYNC_RETRY_MS)
                val currentUserId = _currentUser.value?.id
                if (currentUserId.isNullOrBlank()) continue

                val jwt = resolveJwtForPendingSync()
                if (jwt.isNullOrBlank()) {
                    pausePendingSyncForAuth()
                    refreshPendingConnectionCount()
                    continue
                }

                runCatching {
                    connectionRepository.syncPendingConnections()
                    var proximity = connectionRepository.syncPendingProximityHandshakes(jwt)
                    if (proximity.authorizationFailed) {
                        val refreshedJwt = resolveJwtForPendingSync(forceRefresh = true)
                        if (refreshedJwt.isNullOrBlank()) {
                            pausePendingSyncForAuth()
                            return@runCatching
                        }
                        proximity = connectionRepository.syncPendingProximityHandshakes(refreshedJwt)
                        if (proximity.authorizationFailed) {
                            pausePendingSyncForAuth()
                            return@runCatching
                        }
                    }

                    resumePendingSyncIfPaused()

                    val recovered = proximity.recoveredUsers
                    if (!recovered.isNullOrEmpty()) {
                        _proximityHandshakeRecovered.emit(
                            ProximityHandshakeRecoveryPayload(
                                users = recovered,
                                encounterLogged = proximity.recoveredEncounterLogged,
                                groupCliqueCandidateMemberIds = proximity.groupCliqueCandidateMemberIds,
                                pendingHandshakeId = proximity.pendingHandshakeId,
                                isAggregateNewConnection = proximity.isAggregateNewConnection,
                            ),
                        )
                    }
                }.onFailure {
                    if (it.isAuthorizationFailure()) {
                        pausePendingSyncForAuth()
                    } else {
                        println("AppDataManager: Pending sync attempt failed: ${it.message}")
                    }
                }
                refreshPendingConnectionCount()
            }
        }
}
