@file:Suppress("ktlint:standard:max-line-length")

package compose.project.click.click.data // pragma: allowlist secret

import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.realtime.RealtimeCoordinator // pragma: allowlist secret
import compose.project.click.click.util.chatMediaDispatcher // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

internal fun AppDataManager.applyInboxFeedChatActivity(
    connectionId: String,
    lastMessagePreview: Message,
) {
    if (connectionId.isBlank()) return
    val updated =
        _inboxFeedChats.value.map { row ->
            if (row.connection.id != connectionId) return@map row
            val existing = row.lastMessage
            val shouldReplacePreview =
                existing == null ||
                    lastMessagePreview.id == existing.id ||
                    lastMessagePreview.timeCreated > existing.timeCreated ||
                    (
                        lastMessagePreview.timeCreated == existing.timeCreated &&
                            lastMessagePreview.id >= existing.id
                    )
            if (!shouldReplacePreview) {
                return@map row.copy(
                    connection =
                        row.connection.copy(
                            last_message_at =
                                listOfNotNull(
                                    row.connection.last_message_at,
                                    lastMessagePreview.timeCreated,
                                ).maxOrNull(),
                        ),
                )
            }
            row.copy(
                lastMessage = lastMessagePreview,
                connection =
                    row.connection.copy(
                        last_message_at =
                            listOfNotNull(
                                row.connection.last_message_at,
                                lastMessagePreview.timeCreated,
                            ).maxOrNull(),
                        chat = row.connection.chat.copy(messages = listOf(lastMessagePreview)),
                    ),
            )
        }
    if (updated != _inboxFeedChats.value) {
        _inboxFeedChats.value =
            updated.sortedByDescending {
                it.lastMessage?.timeCreated ?: it.connection.last_message_at ?: it.connection.created
            }
    }
}

internal fun AppDataManager.startSilentChatPrefetch(userId: String) {
    if (_ghostModeEnabled.value || userId.isBlank()) return
    if (!networkConnectivityMonitor.isOnline.value) return
    chatPrefetchJob?.cancel()
    chatPrefetchJob =
        scope.launch(chatMediaDispatcher) {
            runCatching {
                val direct = async { chatRepository.fetchDirectUserChatsWithDetails(userId) }
                val archived = async { chatRepository.fetchArchivedUserChatsWithDetails(userId) }
                val groups =
                    async {
                        runCatching { chatRepository.fetchGroupUserChatsWithDetails(userId) }
                            .getOrElse { emptyList() }
                    }
                val directRows =
                    (direct.await() + archived.await())
                        .distinctBy { it.connection.id }
                        .sortedByDescending { it.lastMessage?.timeCreated ?: it.connection.last_message_at ?: it.connection.created }
                        .take(CHAT_PREFETCH_LIMIT)
                val groupRows =
                    groups
                        .await()
                        .distinctBy { it.connection.id }
                        .sortedByDescending { it.lastMessage?.timeCreated ?: it.connection.last_message_at ?: it.connection.created }
                val rows = (directRows + groupRows).distinctBy { it.connection.id }
                val groupMemberProfileIds =
                    groupRows
                        .flatMap { it.groupMemberUsers.map { member -> member.id } }
                        .filter { it != userId }
                startBackgroundProfilePrefetch(
                    viewerUserId = userId,
                    peerUserIds = _connectedUsers.value.keys.toList() + groupMemberProfileIds,
                )
                for (chat in rows) {
                    val chatId = chat.chat.id ?: continue
                    if (chat.groupClique == null) {
                        chatRepository.cacheEncryptionKeys(chatId, chat.connection.id, chat.connection.user_ids)
                    }
                    val limit =
                        if (chat.groupClique != null) {
                            GROUP_CHAT_PREFETCH_MAX_MESSAGES
                        } else {
                            CHAT_PREFETCH_MAX_MESSAGES
                        }
                    val decryptedMessages =
                        chatRepository.fetchMessagesForChat(
                            chatId = chatId,
                            viewerUserId = userId,
                            limit = limit,
                        ) ?: continue
                    val messages = chatRepository.vaultEncryptedMediaMessages(chatId, userId, decryptedMessages)
                    val participants = chatRepository.fetchChatParticipants(chatId)
                    val reactions =
                        runCatching {
                            chatRepository.fetchReactionsForChat(chatId, messages.map { it.id })
                        }.getOrElse { emptyList() }
                    withContext(Dispatchers.Default) {
                        val existing = cachedChatThreadFor(chat.connection.id)
                        val mergedMessages =
                            if (existing != null && existing.messages.isNotEmpty()) {
                                val byId = existing.messages.associateBy { it.id }.toMutableMap()
                                for (message in messages) {
                                    byId[message.id] = message
                                }
                                byId.values.sortedBy { it.timeCreated }.takeLast(CHAT_PREFETCH_MAX_MESSAGES)
                            } else {
                                messages
                            }
                        cacheChatThread(
                            connectionId = chat.connection.id,
                            chatId = chatId,
                            messages = mergedMessages,
                            participants = participants,
                            reactions = reactions,
                        )
                    }
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                println("AppDataManager: silent chat prefetch failed: ${e.redactedRestMessage()}")
            }.onSuccess {
                silentChatPrefetchCompleted = true
            }
        }
}

internal fun AppDataManager.startBackgroundProfilePrefetch(
    viewerUserId: String,
    peerUserIds: List<String>,
) {
    if (_ghostModeEnabled.value || viewerUserId.isBlank()) return
    val knownProfileIds =
        peerUserIds +
            _connections.value.flatMap { it.user_ids } +
            _connectedUsers.value.keys +
            _inboxFeedChats.value.flatMap { row ->
                listOf(row.otherUser.id) + row.groupMemberUsers.map { it.id } + row.connection.user_ids
            }
    val ids =
        knownProfileIds
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != viewerUserId }
            .distinct()
            .toList()
    if (ids.isEmpty()) return

    queuedProfilePrefetchIds = queuedProfilePrefetchIds + ids
    if (profilePrefetchJob?.isActive == true) return

    profilePrefetchJob =
        scope.launch(chatMediaDispatcher) {
            while (true) {
                val batch = queuedProfilePrefetchIds.toList()
                if (batch.isEmpty()) break
                queuedProfilePrefetchIds = emptySet()

                val concurrency = Semaphore(8)
                coroutineScope {
                    batch
                        .map { peerId ->
                            async {
                                concurrency.withPermit {
                                    runCatching {
                                        supabaseRepository.refreshUserPublicProfile(viewerUserId, peerId)
                                    }.onFailure { e ->
                                        println("AppDataManager: profile prefetch failed for $peerId: ${e.redactedRestMessage()}")
                                    }
                                }
                            }
                        }.awaitAll()
                }
                schedulePersistSnapshot()
            }
        }
}

internal fun AppDataManager.startRealtimeCoordinatorSync(userId: String) {
    if (_ghostModeEnabled.value || userId.isBlank()) return
    aggressiveBackgroundChatSyncJob?.cancel()
    realtimeCoordinatorJob?.cancel()
    realtimeCoordinatorJob =
        scope.launch {
            RealtimeCoordinator.ensureStarted(userId)
            RealtimeCoordinator.messageInserts.collect { event ->
                val vaulted =
                    runCatching {
                        chatRepository
                            .vaultEncryptedMediaMessages(event.chatId, userId, listOf(event.message))
                            .firstOrNull()
                    }.getOrNull() ?: event.message
                updateConnectionChatActivity(event.connectionId, vaulted.timeCreated, vaulted)
                updateInboxFeedChatActivity(event.connectionId, vaulted)

                val cached = cachedChatThreadFor(event.connectionId)
                if (cached != null) {
                    val messages =
                        (cached.messages.filterNot { it.id == vaulted.id } + vaulted)
                            .sortedBy { it.timeCreated }
                            .takeLast(CHAT_PREFETCH_MAX_MESSAGES)
                    cacheChatThread(
                        connectionId = cached.connectionId,
                        chatId = cached.chatId,
                        messages = messages,
                        participants = cached.participants,
                        reactions = cached.reactions,
                    )
                } else {
                    val participants =
                        runCatching { chatRepository.fetchChatParticipants(event.chatId) }
                            .getOrElse { emptyList() }
                    cacheChatThread(
                        connectionId = event.connectionId,
                        chatId = event.chatId,
                        messages = listOf(vaulted),
                        participants = participants,
                    )
                }
                schedulePersistSnapshot()
            }
        }
    scope.launch {
        RealtimeCoordinator.connectionJunctionChanged.collect {
            refreshInboxFromCoordinator(force = false)
        }
    }
}

/** Lightweight inbox refresh after Realtime junction change (avoids full loadAllData). */
internal fun AppDataManager.refreshInboxFromCoordinator(force: Boolean) {
    if (_ghostModeEnabled.value) return
    val userId = _currentUser.value?.id ?: return
    scope.launch {
        runCatching {
            val snapshot = supabaseRepository.fetchUserConnectionsSnapshot(userId, runStaleSweep = false)
            applyFetchedConnectionSnapshot(snapshot)
            lastRefreshTime = Clock.System.now().toEpochMilliseconds()
        }.onFailure { e ->
            if (force) {
                refresh(force = true)
            } else {
                println("AppDataManager: coordinator inbox refresh failed: ${e.redactedRestMessage()}")
            }
        }
    }
}

internal fun AppDataManager.startAggressiveBackgroundChatSync(userId: String) {
    startRealtimeCoordinatorSync(userId)
}

/**
 * Eagerly fetch nearby beacons + community hubs around the device location, off the main
 * startup path. Runs concurrently with the connections snapshot fetch (see [loadAllData]).
 */
internal fun AppDataManager.startBeaconPrefetch() {
    if (_ghostModeEnabled.value) return
    if (beaconPrefetchJob?.isActive == true) return
    _discoveryMapPrefetchComplete.value = false
    beaconPrefetchJob =
        scope.launch {
            try {
                runCatching {
                    // Cold start: session may not be hydrated yet when Home requests prefetch.
                    if (SupabaseConfig.client.auth
                            .currentSessionOrNull()
                            ?.accessToken
                            .isNullOrBlank()
                    ) {
                        runCatching { ClickWebAuthCoordinator.ensureReady(authRepository) }
                    }
                    // GPS may not be ready the instant the app cold-starts. Retry a few times so the
                    // discovery feed is seeded with hubs + beacons without waiting for the user to
                    // open (and acquire bounds from) the expanded map.
                    // GPS may not be ready (or unset on simulator). Try a few short attempts, then
                    // fall through to connection / cache anchors instead of blocking for ~30s.
                    var loc: compose.project.click.click.utils.LocationResult? = null // pragma: allowlist secret
                    if (locationService.hasLocationPermission()) {
                        var attempt = 0
                        while (attempt < 2 && currentCoroutineContext().isActive) {
                            loc = locationService.getCurrentLocation()
                                ?: locationService.getHighAccuracyLocation(1_500L)
                            if (loc != null) break
                            attempt++
                            if (attempt < 2) {
                                delay(1_000L)
                            }
                        }
                    }
                    // When GPS is still unavailable (common on simulators with Location=None), seed
                    // from connection centroids or cached beacon/hub/bookmark coords so Nearby still
                    // hydrates without waiting on a live fix.
                    val resolvedLatLon: Pair<Double, Double>? =
                        loc?.let { it.latitude to it.longitude }
                            ?: run {
                                val geos = _connections.value.mapNotNull { it.connectionMapGeo() }
                                if (geos.isEmpty()) {
                                    null
                                } else {
                                    geos.map { it.lat }.average() to geos.map { it.lon }.average()
                                }
                            }
                            ?: run {
                                val coords =
                                    buildList {
                                        _prefetchedMapBeacons.value.forEach { b ->
                                            if (
                                                b.latitude.isFinite() &&
                                                b.longitude.isFinite() &&
                                                !(b.latitude == 0.0 && b.longitude == 0.0)
                                            ) {
                                                add(b.latitude to b.longitude)
                                            }
                                        }
                                        _prefetchedCommunityHubs.value.forEach { h ->
                                            if (
                                                h.latitude.isFinite() &&
                                                h.longitude.isFinite() &&
                                                !(h.latitude == 0.0 && h.longitude == 0.0)
                                            ) {
                                                add(h.latitude to h.longitude)
                                            }
                                        }
                                        _cachedEventBookmarks.value.forEach { bm ->
                                            val lat = bm.latitude ?: return@forEach
                                            val lon = bm.longitude ?: return@forEach
                                            if (lat.isFinite() && lon.isFinite() && !(lat == 0.0 && lon == 0.0)) {
                                                add(lat to lon)
                                            }
                                        }
                                    }
                                if (coords.isEmpty()) {
                                    null
                                } else {
                                    coords.map { it.first }.average() to coords.map { it.second }.average()
                                }
                            }
                    val resolved = resolvedLatLon ?: return@runCatching
                    // Only treat a real GPS fix as last-known device location; cache centroids are
                    // discovery anchors only.
                    if (loc != null) {
                        _lastKnownDeviceLocation.value = loc.latitude to loc.longitude
                    } else if (_lastKnownDeviceLocation.value == null) {
                        _lastKnownDeviceLocation.value = resolved
                    }
                    val centerLat = resolved.first
                    val centerLon = resolved.second
                    val latDelta = BEACON_PREFETCH_RADIUS_METERS / 111_320.0
                    val lonScale = kotlin.math.cos(centerLat * kotlin.math.PI / 180.0).coerceAtLeast(0.2)
                    val lonDelta = BEACON_PREFETCH_RADIUS_METERS / (111_320.0 * lonScale)
                    val minLat = (centerLat - latDelta).coerceIn(-90.0, 90.0)
                    val maxLat = (centerLat + latDelta).coerceIn(-90.0, 90.0)
                    val minLon = (centerLon - lonDelta).coerceIn(-180.0, 180.0)
                    val maxLon = (centerLon + lonDelta).coerceIn(-180.0, 180.0)

                    coroutineScope {
                        val beaconsDeferred =
                            async {
                                mapBeaconRepository.fetchLocalBeacons(minLat, maxLat, minLon, maxLon)
                            }
                        val hubsDeferred =
                            async {
                                mapBeaconRepository.fetchNearbyCommunityHubs(minLat, maxLat, minLon, maxLon)
                            }
                        beaconsDeferred.await().onSuccess { list ->
                            if (list.isNotEmpty()) {
                                mergeCachedMapBeacons(list)
                            }
                        }
                        hubsDeferred.await().onSuccess { rows ->
                            if (rows.isNotEmpty()) {
                                mergeCachedCommunityHubsFromDto(rows)
                            }
                        }
                    }
                }.onFailure { e ->
                    if (e is CancellationException) throw e
                    println("AppDataManager: beacon prefetch failed: ${e.redactedRestMessage()}")
                }
            } finally {
                _discoveryMapPrefetchComplete.value = true
            }
        }
}
