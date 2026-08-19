@file:Suppress(
    "ktlint:standard:backing-property-naming",
    "ktlint:standard:property-naming",
)

package compose.project.click.click.viewmodel // pragma: allowlist secret

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.collaboration.CollaborationSessionManager // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.ClickWebAuthCoordinator // pragma: allowlist secret
import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.BeaconAttendeeDto // pragma: allowlist secret
import compose.project.click.click.data.api.BeaconEngagementHttpException // pragma: allowlist secret
import compose.project.click.click.data.api.EngagementTelemetryBody // pragma: allowlist secret
import compose.project.click.click.data.api.MapBeaconPatchBody // pragma: allowlist secret
import compose.project.click.click.data.models.BeaconVisibilityAudience // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconInsert // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.parseEpochMs // pragma: allowlist secret
import compose.project.click.click.data.models.parseMapBeaconMetadata // pragma: allowlist secret
import compose.project.click.click.data.models.visibleMapConnections // pragma: allowlist secret
import compose.project.click.click.data.repository.AuthRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.ChatRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.MapBeaconRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseChatRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.BeaconEngagementPersistence // pragma: allowlist secret
import compose.project.click.click.data.storage.BeaconRsvpPersistence // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.events.EVENT_CATEGORIES_METADATA_KEY // pragma: allowlist secret
import compose.project.click.click.events.EVENT_CATEGORY_OPTIONS // pragma: allowlist secret
import compose.project.click.click.events.EVENT_CHECK_IN_RADIUS_METADATA_KEY // pragma: allowlist secret
import compose.project.click.click.events.EVENT_VENUE_SCALE_METADATA_KEY // pragma: allowlist secret
import compose.project.click.click.events.EventReminderCoordinator // pragma: allowlist secret
import compose.project.click.click.events.EventSchedule // pragma: allowlist secret
import compose.project.click.click.events.EventVenueScale // pragma: allowlist secret
import compose.project.click.click.events.beaconCheckInFailureMessage // pragma: allowlist secret
import compose.project.click.click.events.eventSchedule // pragma: allowlist secret
import compose.project.click.click.events.eventScheduleMetadata // pragma: allowlist secret
import compose.project.click.click.events.isVisibleEventBeacon // pragma: allowlist secret
import compose.project.click.click.events.mergeEventScheduleIntoRaw // pragma: allowlist secret
import compose.project.click.click.events.resolveEventCheckInRadiusMeters // pragma: allowlist secret
import compose.project.click.click.events.validateEventSchedule // pragma: allowlist secret
import compose.project.click.click.getPlatform // pragma: allowlist secret
import compose.project.click.click.ui.components.MapPin // pragma: allowlist secret
import compose.project.click.click.ui.components.MapPinKind // pragma: allowlist secret
import compose.project.click.click.ui.components.mapBeaconKindToLayerFilter // pragma: allowlist secret
import compose.project.click.click.ui.utils.BoundingBox // pragma: allowlist secret
import compose.project.click.click.ui.utils.CommunityHubPin // pragma: allowlist secret
import compose.project.click.click.ui.utils.ConnectionMapPoint // pragma: allowlist secret
import compose.project.click.click.ui.utils.MapCluster // pragma: allowlist secret
import compose.project.click.click.ui.utils.MapRenderData // pragma: allowlist secret
import compose.project.click.click.ui.utils.TimeState // pragma: allowlist secret
import compose.project.click.click.ui.utils.calculateZoomForBounds // pragma: allowlist secret
import compose.project.click.click.ui.utils.determineMapRenderData // pragma: allowlist secret
import compose.project.click.click.ui.utils.displayDynamicTitle // pragma: allowlist secret
import compose.project.click.click.ui.utils.hasUsableMapCoordinates // pragma: allowlist secret
import compose.project.click.click.ui.utils.haversineDistance // pragma: allowlist secret
import compose.project.click.click.ui.utils.mapPeerDisplayNameForPin // pragma: allowlist secret
import compose.project.click.click.ui.utils.mergeCommunityHubLists // pragma: allowlist secret
import compose.project.click.click.ui.utils.mergeMapBeaconLists // pragma: allowlist secret
import compose.project.click.click.ui.utils.overlappingMapPins // pragma: allowlist secret
import compose.project.click.click.ui.utils.resolveBeaconQuickDistanceMeters // pragma: allowlist secret
import compose.project.click.click.ui.utils.toMapPoint // pragma: allowlist secret
import compose.project.click.click.util.compressOutgoingChatImageForUpload // pragma: allowlist secret
import compose.project.click.click.util.isValidStreamingUrl // pragma: allowlist secret
import compose.project.click.click.util.teardownBlocking // pragma: allowlist secret
import compose.project.click.click.utils.EVENT_FORMATTED_ADDRESS_METADATA_KEY // pragma: allowlist secret
import compose.project.click.click.utils.EVENT_LOCATION_NAME_METADATA_KEY // pragma: allowlist secret
import compose.project.click.click.utils.GeocodedPlace // pragma: allowlist secret
import compose.project.click.click.utils.HUB_GATEKEEPER_HIGH_ACCURACY_TIMEOUT_MS // pragma: allowlist secret
import compose.project.click.click.utils.LocationResult // pragma: allowlist secret
import compose.project.click.click.utils.LocationService // pragma: allowlist secret
import compose.project.click.click.utils.resolveHubGatekeeperLocation // pragma: allowlist secret
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject // pragma: allowlist secret
import kotlinx.serialization.json.JsonPrimitive // pragma: allowlist secret
import kotlinx.serialization.json.add // pragma: allowlist secret
import kotlinx.serialization.json.buildJsonObject // pragma: allowlist secret
import kotlinx.serialization.json.put // pragma: allowlist secret
import kotlinx.serialization.json.putJsonArray // pragma: allowlist secret
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Hydrate map state immediately from [AppDataManager]'s eager beacon/hub prefetch so the map is
 * already populated on first render (prefetch runs in parallel with connections at app load).
 */
internal fun MapViewModel.seedFromAppDataPrefetch() {
    applyPrefetchedBeacons(AppDataManager.prefetchedMapBeacons.value)
    applyPrefetchedHubs(AppDataManager.prefetchedCommunityHubs.value)
    seedEventPinsFromCachedBookmarks(AppDataManager.cachedEventBookmarks.value)
    viewModelScope.launch {
        AppDataManager.prefetchedMapBeacons.collect { applyPrefetchedBeacons(it) }
    }
    viewModelScope.launch {
        AppDataManager.prefetchedCommunityHubs.collect { applyPrefetchedHubs(it) }
    }
    viewModelScope.launch {
        AppDataManager.cachedEventBookmarks.collect { seedEventPinsFromCachedBookmarks(it) }
    }
}

/**
 * Saved-event bookmarks carry denormalized lat/lng. After null-island cache purge, use them
 * so event pins still paint until proximity returns the same rows.
 * Only fills **missing** ids / rescues null-island — never replaces a richer proximity row.
 */
internal fun MapViewModel.seedEventPinsFromCachedBookmarks(bookmarks: List<compose.project.click.click.data.api.EventBookmarkItemDto>) {
    if (bookmarks.isEmpty()) return
    _mapBeacons.update { current ->
        val byId = current.associateBy { it.id }
        val seeds =
            bookmarks.mapNotNull { bookmark ->
                val lat = bookmark.latitude ?: return@mapNotNull null
                val lng = bookmark.longitude ?: return@mapNotNull null
                if (!lat.isFinite() || !lng.isFinite() || (lat == 0.0 && lng == 0.0)) {
                    return@mapNotNull null
                }
                val existing = byId[bookmark.beaconId]
                if (existing != null && existing.hasUsableMapCoordinates()) {
                    // Already have a real pin — do not overwrite host/posted/creator fields.
                    return@mapNotNull null
                }
                val scheduleRaw =
                    buildJsonObject {
                        bookmark.title?.takeIf { it.isNotBlank() }?.let { put("title", it) }
                        bookmark.eventStartAt?.takeIf { it.isNotBlank() }?.let { put("event_start_at", it) }
                        bookmark.eventEndAt?.takeIf { it.isNotBlank() }?.let { put("event_end_at", it) }
                    }
                MapBeacon(
                    id = bookmark.beaconId,
                    kind = MapBeaconKind.EVENT,
                    latitude = lat,
                    longitude = lng,
                    metadata = parseMapBeaconMetadata(scheduleRaw),
                    expiresAtEpochMs = bookmark.expiresAt?.let { parseEpochMs(it) },
                    sourceBeaconType = "event",
                )
            }
        if (seeds.isEmpty()) current else mergeMapBeaconLists(current, seeds)
    }
}

internal fun MapViewModel.applyPrefetchedBeacons(list: List<MapBeacon>) {
    // Always run through merge so in-memory null-island pins from a prior bad GET are purged
    // even when the incoming prefetch list is empty after cache heal.
    _mapBeacons.update { current -> mergeMapBeaconLists(current, list) }
    if (list.isEmpty()) return
    AppDataManager.mergeCachedMapBeacons(list)
    markDiscoveryProximityFetchCompleted()
    hydrateEventEngagementFromServer()
}

internal fun MapViewModel.applyPrefetchedHubs(rows: List<compose.project.click.click.data.api.CommunityHubNearbyDto>) {
    if (rows.isEmpty()) return
    val incoming =
        rows.map { dto ->
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
    _communityHubs.update { current -> mergeCommunityHubLists(current, filterDismissedCommunityHubs(incoming)) }
    AppDataManager.mergeCachedCommunityHubsFromDto(rows)
    markDiscoveryProximityFetchCompleted()
}

internal fun MapViewModel.filterDismissedCommunityHubs(hubs: List<CommunityHubPin>): List<CommunityHubPin> {
    val dismissed = AppDataManager.dismissedCommunityHubIds.value
    if (dismissed.isEmpty()) return hubs
    return hubs.filterNot { it.hubId in dismissed }
}

internal fun MapViewModel.observeAppData() {
    viewModelScope.launch {
        combine(
            AppDataManager.connections,
            AppDataManager.connectedUsers,
            AppDataManager.archivedConnectionIds,
            AppDataManager.hiddenConnectionIds,
            AppDataManager.isDataLoaded,
            AppDataManager.isLoading,
            _zoomLevel,
            _mapBeacons,
            _selectedLayerFilters,
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val connections = values[0] as List<Connection>
            val connectedUsers = values[1] as Map<String, User>
            val archivedIds = values[2] as Set<String>
            val hiddenIds = values[3] as Set<String>
            val isDataLoaded = values[4] as Boolean
            val isLoading = values[5] as Boolean
            val zoom = values[6] as Double
            Nonuple(
                connections,
                connectedUsers,
                archivedIds,
                hiddenIds,
                isDataLoaded,
                isLoading,
                zoom,
                values[7],
                values[8],
            )
        }.collectLatest { (connections, connectedUsers, archivedIds, hiddenIds, isDataLoaded, isLoading, zoom, _, _) ->
            when {
                // `archivedIds` is read so archive/unarchive recomputes the map when the connections list is unchanged.
                // `_mapBeacons` / `_selectedLayerFilters` are combined so pin render refreshes when layers change.
                isDataLoaded && (archivedIds.isNotEmpty() || archivedIds.isEmpty()) -> {
                    val viewerId = AppDataManager.currentUser.value?.id
                    val mapConnections = visibleMapConnections(connections, hiddenIds, viewerId)
                    _mapState.value = MapState.Success(mapConnections)
                    ensureDefaultCameraTarget(mapConnections)
                    updateRenderData(mapConnections, zoomForClusteringRender(zoom))
                    refreshSelectedConnectionUser(connectedUsers)
                }
                isLoading -> {
                    _mapState.value = MapState.Loading
                }
                connections.isNotEmpty() -> {
                    val viewerId = AppDataManager.currentUser.value?.id
                    val mapConnections = visibleMapConnections(connections, hiddenIds, viewerId)
                    _mapState.value = MapState.Success(mapConnections)
                }
                else -> {
                    _mapState.value = MapState.Success(emptyList())
                    _renderData.value = MapRenderData.Clusters(emptyList())
                }
            }
        }
    }
}

/**
 * Update render data based on connections, zoom level, and active filter.
 *
 * R1.4: cluster/pin computation can iterate hundreds of `Connection`s and call
 * `haversineDistance` across every pair below the cluster-threshold zoom. Keep this
 * off the Main dispatcher so scrolling / pinch-to-zoom gestures never block the UI
 * thread. The resulting [MapRenderData] is immutable and safe to publish to a
 * [MutableStateFlow] from any dispatcher.
 */
internal fun MapViewModel.updateRenderData(
    connections: List<Connection>,
    zoom: Double,
) {
    renderDataJob?.cancel()
    incrementalPopulationJob?.cancel()
    val layers = _selectedLayerFilters.value
    val beaconsRaw = _mapBeacons.value
    renderDataJob =
        viewModelScope.launch {
            val rendered =
                withContext(Dispatchers.Default) {
                    val connectedUsersSnapshot = AppDataManager.connectedUsers.value
                    val currentUserId = AppDataManager.currentUser.value?.id
                    val showConnections =
                        layers.contains(MapLayerFilter.ALL) ||
                            layers.contains(MapLayerFilter.MY_CONNECTIONS)
                    val filteredConnections = if (showConnections) connections else emptyList()
                    val filteredBeacons = filterBeaconsForLayers(beaconsRaw, layers)
                    determineMapRenderData(
                        connections = filteredConnections,
                        beacons = filteredBeacons,
                        zoomLevel = zoom,
                        clusterThreshold = clusterThreshold,
                        connectionPeerDisplayName = { conn ->
                            mapPeerDisplayNameForPin(conn, currentUserId, connectedUsersSnapshot)
                        },
                        viewerUserId = currentUserId,
                    )
                }

            // If we're in IndividualPins mode and there are many points, publish only the nearest
            // INITIAL_PIN_CAP immediately and incrementally add the rest in batches. This reduces
            // initial marker creation work and improves perceived map performance.
            if (rendered is MapRenderData.IndividualPins && rendered.points.size > INITIAL_PIN_CAP) {
                val allPoints = rendered.points

                // Choose an anchor (camera center) to sort by proximity.
                val anchor = _cameraTarget.value ?: _defaultCameraTarget.value
                val (anchorLat, anchorLon) =
                    when {
                        anchor != null -> anchor.latitude to anchor.longitude
                        allPoints.isNotEmpty() -> allPoints.first().latitude to allPoints.first().longitude
                        else -> null to null
                    }

                val initialPoints =
                    if (anchorLat != null && anchorLon != null) {
                        allPoints
                            .sortedBy { haversineDistance(anchorLat, anchorLon, it.latitude, it.longitude) }
                            .take(INITIAL_PIN_CAP)
                    } else {
                        allPoints.take(INITIAL_PIN_CAP)
                    }

                _renderData.value = MapRenderData.IndividualPins(points = initialPoints, beacons = rendered.beacons)

                // Incrementally add remaining points in batches to avoid CPU/GC spikes
                incrementalPopulationJob =
                    viewModelScope.launch {
                        withContext(Dispatchers.Default) {
                            val remaining = allPoints - initialPoints
                            val batchSize = 100
                            var current = initialPoints.toMutableList()
                            var i = 0
                            while (i < remaining.size) {
                                val end = min(i + batchSize, remaining.size)
                                val batch = remaining.subList(i, end)
                                // Small delay yields frame time back to the UI thread
                                delay(50)
                                current.addAll(batch)
                                _renderData.value = MapRenderData.IndividualPins(points = current.toList(), beacons = rendered.beacons)
                                i = end
                            }
                        }
                    }
            } else {
                _renderData.value = rendered
            }
        }
}

/**
 * Optional `filters` query for `/api/beacons`.
 *
 * Always null: fetch all beacon kinds for the radius and filter by layer on-device.
 * Server-side type filters caused soundtracks/alerts to vanish when a prior Events-only
 * preset (or partial chip set) drove the fetch — toggling Soundtracks back on could not
 * recover pins already outside the last RPC result set.
 */
internal fun MapViewModel.beaconTypesQueryForLayers(
    @Suppress("UNUSED_PARAMETER") layers: Set<MapLayerFilter>,
): String? = null


internal fun MapViewModel.filterBeaconsForLayers(
    beacons: List<MapBeacon>,
    layers: Set<MapLayerFilter>,
): List<MapBeacon> {
    // Always apply event schedule visibility so map pins match the discovery feed
    // (feed also uses isActiveForDiscoveryFeed → isVisibleEventBeacon for EVENT).
    if (layers.contains(MapLayerFilter.ALL)) {
        return beacons.filter { it.isVisibleEventBeacon() }
    }
    val out = mutableListOf<MapBeacon>()
    for (b in beacons) {
        val include =
            when (b.kind) {
                MapBeaconKind.SOUNDTRACK -> layers.contains(MapLayerFilter.SOUNDTRACKS)
                MapBeaconKind.SOS, MapBeaconKind.HAZARD, MapBeaconKind.UTILITY, MapBeaconKind.STUDY ->
                    layers.contains(MapLayerFilter.ALERTS_UTILITIES)
                MapBeaconKind.EVENT -> layers.contains(MapLayerFilter.EVENTS)
                MapBeaconKind.SOCIAL_VIBE, MapBeaconKind.OTHER ->
                    layers.contains(MapLayerFilter.SOCIAL_VIBES)
            }
        if (include && b.isVisibleEventBeacon()) out.add(b)
    }
    return out
}

/**
 * Load connections if not already loaded
 */
internal fun MapViewModel.loadConnectionsImpl() {
    if (!AppDataManager.isDataLoaded.value) {
        AppDataManager.initializeData()
    }
}

/**
 * Force refresh connections
 */
internal fun MapViewModel.refreshImpl() {
    AppDataManager.refresh(force = true)
}

/**
 * Get statistics about connections on the map
 */
internal fun MapViewModel.getMapStatsImpl(): MapStats {
    val state = _mapState.value
    if (state !is MapState.Success) return MapStats(0, 0, 0, 0)

    val connections = state.connections
    val points =
        connections.mapNotNull {
            try {
                it.toMapPoint()
            } catch (e: Exception) {
                null
            }
        }

    return MapStats(
        totalConnections = connections.size,
        liveCount = points.count { it.timeState == TimeState.LIVE },
        recentCount = points.count { it.timeState == TimeState.RECENT },
        archiveCount = points.count { it.timeState == TimeState.ARCHIVE },
    )
}

/**
 * Connection junction updates handled by [RealtimeCoordinator] → [AppDataManager].
 */
internal fun MapViewModel.subscribeToConnectionChanges() {
    // Intentionally empty — map reads AppDataManager.connections.
}

/**
 * Send a nudge to a connection.
 * This sends a special emoji message ("👋") to the connection's chat.
 */
internal fun MapViewModel.sendNudgeImpl(
    connectionId: String,
    otherUserName: String,
) {
    val currentUser = AppDataManager.currentUser.value ?: return
    val connection =
        (mapState.value as? MapState.Success)
            ?.connections
            ?.firstOrNull { it.id == connectionId } ?: return
    val chatId = connection.chat.id ?: return

    viewModelScope.launch {
        val currentName = currentUser.name ?: "Someone"
        val msg =
            chatRepository.sendMessage(
                chatId = chatId,
                userId = currentUser.id,
                content = "👋 $currentName nudged you!",
            )
        _nudgeResult.value =
            if (msg != null) {
                "Nudge sent to $otherUserName!"
            } else {
                "Failed to send nudge"
            }
    }
}
