package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.PlatformHapticsPolicy
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.LocationPreferences // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.BeaconVisibilityAudience // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconInsert // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.api.BeaconAttendeeDto
import compose.project.click.click.data.api.MapBeaconPatchBody
import compose.project.click.click.data.models.parseMapBeaconMetadata // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.repository.AuthRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.ChatRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.MapBeaconRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseChatRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.BeaconRsvpPersistence // pragma: allowlist secret
import compose.project.click.click.events.EventReminderCoordinator
import compose.project.click.click.events.EventSchedule
import compose.project.click.click.events.eventScheduleMetadata
import compose.project.click.click.events.isVisibleEventBeacon
import compose.project.click.click.events.validateEventSchedule
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import io.github.jan.supabase.auth.auth
import compose.project.click.click.collaboration.CollaborationSessionManager // pragma: allowlist secret
import compose.project.click.click.ui.components.MapPin // pragma: allowlist secret
import compose.project.click.click.ui.components.MapPinKind // pragma: allowlist secret
import compose.project.click.click.ui.utils.CommunityHubPin // pragma: allowlist secret
import compose.project.click.click.ui.utils.* // pragma: allowlist secret
import compose.project.click.click.util.isValidStreamingUrl // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import compose.project.click.click.util.teardownBlocking // pragma: allowlist secret
import compose.project.click.click.utils.LocationResult // pragma: allowlist secret
import compose.project.click.click.utils.LocationService // pragma: allowlist secret
import kotlinx.serialization.json.JsonObject // pragma: allowlist secret
import kotlinx.serialization.json.buildJsonObject // pragma: allowlist secret
import kotlinx.serialization.json.put // pragma: allowlist secret
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.PostgresAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.datetime.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.min
import kotlin.random.Random

/**
 * State representing the map loading/error status
 */
sealed class MapState {
    object Loading : MapState()
    data class Success(val connections: List<Connection>) : MapState()
    data class Error(val message: String) : MapState()
}

/**
 * Represents the selected item on the map (either a cluster or individual connection)
 */
sealed class MapSelection {
    object None : MapSelection()
    data class ClusterSelected(val cluster: MapCluster) : MapSelection()
    data class ConnectionSelected(val point: ConnectionMapPoint, val otherUser: User?) : MapSelection()
    data class BeaconSelected(val beacon: MapBeacon, val distanceMeters: Double?) : MapSelection()
    data class HubSelected(
        val hub: CommunityHubPin,
        val distanceMeters: Double?,
        val canJoinGeofence: Boolean,
    ) : MapSelection()
}

data class BeaconRsvpCacheEntry(
    val attendees: List<BeaconAttendeeDto>,
    val currentUserSignedUp: Boolean,
)

/**
 * ViewModel for the Social Memory Map feature
 * 
 * Handles:
 * - Connection data loading from AppDataManager
 * - Clustering logic based on zoom level
 * - Time-based visual decay (Live/Recent/Archive)
 * - Ghost Mode privacy toggle
 * - Selected connection/cluster state for bottom sheet
 */
class MapViewModel : ViewModel() {
    companion object {
        // Session memory for map camera across map screen exits/returns.
        private var lastKnownCameraTarget: CameraTarget? = null
    }

    // Base data state
    private val _mapState = MutableStateFlow<MapState>(MapState.Loading)
    val mapState: StateFlow<MapState> = _mapState.asStateFlow()

    // Current zoom level (logical; updated from native map readbacks and programmatic moves)
    private val _zoomLevel = MutableStateFlow(10.0)
    val zoomLevel: StateFlow<Double> = _zoomLevel.asStateFlow()

    // What to render on the map (clusters or individual pins)
    private val _renderData = MutableStateFlow<MapRenderData>(MapRenderData.Clusters(emptyList()))
    val renderData: StateFlow<MapRenderData> = _renderData.asStateFlow()

    // Currently selected item (for bottom sheet)
    private val _selection = MutableStateFlow<MapSelection>(MapSelection.None)
    val selection: StateFlow<MapSelection> = _selection.asStateFlow()

    // Ghost Mode - when enabled, user location is not shared
    val ghostModeEnabled: StateFlow<Boolean> = AppDataManager.ghostModeEnabled

    // Camera target for animations
    private val _cameraTarget = MutableStateFlow<CameraTarget?>(null)
    val cameraTarget: StateFlow<CameraTarget?> = _cameraTarget.asStateFlow()

    // Stable default camera target derived from all connection locations.
    private val _defaultCameraTarget = MutableStateFlow<CameraTarget?>(lastKnownCameraTarget)
    val defaultCameraTarget: StateFlow<CameraTarget?> = _defaultCameraTarget.asStateFlow()

    // Visible bounds for viewport-based filtering in ConnectionsList
    private val _visibleBounds = MutableStateFlow<BoundingBox?>(null)
    val visibleBounds: StateFlow<BoundingBox?> = _visibleBounds.asStateFlow()

    private val _selectedLayerFilters = MutableStateFlow(defaultMapLayerFilters())
    val selectedLayerFilters: StateFlow<Set<MapLayerFilter>> = _selectedLayerFilters.asStateFlow()

    val availableLayerFilters: List<MapLayerFilter> = MapLayerFilter.entries

    private val _mapBeacons = MutableStateFlow<List<MapBeacon>>(emptyList())
    val mapBeacons: StateFlow<List<MapBeacon>> = _mapBeacons.asStateFlow()

    private val _communityHubs = MutableStateFlow<List<CommunityHubPin>>(emptyList())
    val communityHubs: StateFlow<List<CommunityHubPin>> = _communityHubs.asStateFlow()

    /** Layer-filtered beacons for the discovery feed (matches map chip filters). */
    val discoveryFeedBeacons: StateFlow<List<MapBeacon>> = combine(_mapBeacons, _selectedLayerFilters) { beacons, layers ->
        filterBeaconsForLayers(beacons, layers)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val discoveryFeedHubs: StateFlow<List<CommunityHubPin>> = combine(_communityHubs, _selectedLayerFilters) { hubs, layers ->
        if (layers.contains(MapLayerFilter.ALL) || layers.contains(MapLayerFilter.COMMUNITY_HUBS)) {
            hubs
        } else {
            emptyList()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val mapBeaconRepository = MapBeaconRepository()
    private val tokenStorage: TokenStorage = createTokenStorage()
    private val authRepository: AuthRepository by lazy { AuthRepository(tokenStorage) }

    private val _beaconInsertError = MutableStateFlow<String?>(null)
    val beaconInsertError: StateFlow<String?> = _beaconInsertError.asStateFlow()

    /** One-shot remote failure after an optimistic beacon was shown (sheet already dismissed). */
    private val _beaconDropFailureToast = MutableStateFlow<String?>(null)
    val beaconDropFailureToast: StateFlow<String?> = _beaconDropFailureToast.asStateFlow()

    /** True while a beacon drop POST is in flight (prevents duplicate soundtrack inserts). */
    private val _beaconSubmitInFlight = MutableStateFlow(false)
    val beaconSubmitInFlight: StateFlow<Boolean> = _beaconSubmitInFlight.asStateFlow()

    /** Cached RSVP state keyed by beacon id — survives tab navigation and sheet dismiss. */
    private val _beaconRsvpById = MutableStateFlow<Map<String, BeaconRsvpCacheEntry>>(emptyMap())
    val beaconRsvpById: StateFlow<Map<String, BeaconRsvpCacheEntry>> = _beaconRsvpById.asStateFlow()

    /** Beacon ids with an in-flight GET `/api/beacons/{id}/rsvp`. */
    private val _beaconRsvpLoadingIds = MutableStateFlow<Set<String>>(emptySet())
    val beaconRsvpLoadingIds: StateFlow<Set<String>> = _beaconRsvpLoadingIds.asStateFlow()

    /** Beacon ids with an optimistic POST/DELETE awaiting server confirmation. */
    private val _beaconRsvpPendingIds = MutableStateFlow<Set<String>>(emptySet())
    val beaconRsvpPendingIds: StateFlow<Set<String>> = _beaconRsvpPendingIds.asStateFlow()

    /**
     * False until startup prefetch or the first map-tab proximity fetch finishes.
     * Silent map refreshes do not reset this.
     */
    private val _discoveryProximityFetchCompleted = MutableStateFlow(false)
    val discoveryProximityFetchCompleted: StateFlow<Boolean> =
        _discoveryProximityFetchCompleted.asStateFlow()

    /** Drives the discovery feed logo pulse (initial load + user pull-to-refresh). */
    private val _discoveryFeedLoading = MutableStateFlow(false)
    val discoveryFeedLoading: StateFlow<Boolean> = _discoveryFeedLoading.asStateFlow()

    val discoveryFeedPending: StateFlow<Boolean> = combine(
        _discoveryFeedLoading,
        _discoveryProximityFetchCompleted,
        discoveryFeedBeacons,
        discoveryFeedHubs,
    ) { loading, completed, beacons, hubs ->
        loading || (!completed && beacons.isEmpty() && hubs.isEmpty())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true,
    )

    private var beaconPollJob: Job? = null
    private var discoveryProximityJob: Job? = null
    private var discoveryPrefetchRetryJob: Job? = null
    private var discoveryPrefetchAttempts = 0
    private var beaconFetchSeq: Long = 0L
    private var discoveryFetchSeq: Long = 0L

    /** Discovery feed uses a GPS-centered radius so beacons load before the map is zoomed in. */
    private val discoveryProximityRadiusMeters = 30_000.0

    private val maxDiscoveryPrefetchAttempts = 5

    private val locationService = LocationService()

    // Cluster threshold - zoom level above which individual pins are shown
    private val clusterThreshold = 12.0

    /**
     * After a cluster zoom-in, native map zoom readbacks often dip below [clusterThreshold] briefly
     * or stay inconsistent with [metersForZoom]. Until the user clearly zooms out past the
     * threshold, treat the map as "pin mode" for clustering decisions so [determineMapRenderData]
     * does not snap back to hub markers while the camera is still on the cluster.
     */
    private val _pinRenderZoomFloor = MutableStateFlow<Double?>(null)

    private fun zoomForClusteringRender(zoom: Double): Double {
        val floor = _pinRenderZoomFloor.value ?: return zoom
        return maxOf(zoom, floor)
    }

    /**
     * Zoom passed to [PlatformMap] for camera span / meters. Sits above [_zoomLevel] while
     * [_pinRenderZoomFloor] keeps pin mode so the map is not left at world scale with many
     * markers stacked on one pixel.
     */
    val mapBindingZoom: StateFlow<Double> = combine(_zoomLevel, _pinRenderZoomFloor) { z, floor ->
        floor?.let { maxOf(z, it) } ?: z
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _zoomLevel.value,
    )

    // Realtime channel for connections changes
    private var connectionsChannel: RealtimeChannel? = null

    // Chat repository for nudge messages
    private val chatRepository: ChatRepository = SupabaseChatRepository(tokenStorage = createTokenStorage())

    // Nudge result for snackbar feedback
    private val _nudgeResult = MutableStateFlow<String?>(null)
    val nudgeResult: StateFlow<String?> = _nudgeResult.asStateFlow()

    // Guards against map callback feedback immediately canceling programmatic zoom animations.
    private var pendingProgrammaticZoomTarget: Double? = null
    private var pendingProgrammaticZoomSetAtMs: Long = 0L

    private var renderDataJob: Job? = null
    // Job for incremental population of markers when initial cap is applied
    private var incrementalPopulationJob: Job? = null
    // Initial number of pins to render immediately. The rest will be added incrementally.
    private val INITIAL_PIN_CAP = 200

    private val beaconSubmitMutex = Mutex()

    init {
        observeAppData()
        subscribeToConnectionChanges()
        seedFromAppDataPrefetch()
        viewModelScope.launch {
            hydrateBeaconRsvpFromDisk()
        }
        viewModelScope.launch {
            AppDataManager.currentUser.collect { user ->
                if (user != null) {
                    hydrateBeaconRsvpFromDisk(user.id)
                    warmDiscoveryFeed()
                }
            }
        }
        viewModelScope.launch {
            AppDataManager.isDataLoaded.collect { loaded ->
                if (loaded) warmDiscoveryFeed()
            }
        }
        viewModelScope.launch {
            AppDataManager.discoveryMapPrefetchComplete.collect { done ->
                if (done) warmDiscoveryFeed()
            }
        }
    }

    /** Starts (or retries) discovery hub/beacon loading without waiting for the map tab. */
    fun warmDiscoveryFeed() {
        if (AppDataManager.currentUser.value == null) return
        prefetchDiscoveryProximityData(showPulse = false, markInitialComplete = true)
    }

    private fun scheduleDiscoveryPrefetchRetry(delayMs: Long = 2_000L) {
        if (_discoveryProximityFetchCompleted.value) return
        if (discoveryPrefetchAttempts >= maxDiscoveryPrefetchAttempts ||
            !canEverResolveProximityCenters()
        ) {
            finishDiscoveryPrefetchAttempt()
            return
        }
        discoveryPrefetchRetryJob?.cancel()
        discoveryPrefetchAttempts++
        val backoffMs = delayMs * discoveryPrefetchAttempts
        discoveryPrefetchRetryJob = viewModelScope.launch {
            delay(backoffMs)
            if (!_discoveryProximityFetchCompleted.value) {
                warmDiscoveryFeed()
            }
        }
    }

    private fun canEverResolveProximityCenters(): Boolean {
        if (locationService.hasLocationPermission()) return true
        if (AppDataManager.lastKnownDeviceLocation.value != null) return true
        if (AppDataManager.connections.value.any { it.connectionMapGeo() != null }) return true
        if (_defaultCameraTarget.value != null || lastKnownCameraTarget != null) return true
        if (_visibleBounds.value != null) return true
        return false
    }

    private fun finishDiscoveryPrefetchAttempt() {
        markDiscoveryProximityFetchCompleted()
        _discoveryFeedLoading.value = false
        discoveryPrefetchRetryJob?.cancel()
        discoveryPrefetchRetryJob = null
    }

    private fun completeDiscoveryPrefetchAfterSuccess() {
        discoveryPrefetchAttempts = 0
        finishDiscoveryPrefetchAttempt()
    }

    private suspend fun hydrateBeaconRsvpFromDisk(userId: String? = null) {
        val uid = userId?.trim()?.takeIf { it.isNotEmpty() }
            ?: SupabaseConfig.client.auth.currentUserOrNull()?.id?.trim()?.takeIf { it.isNotEmpty() }
            ?: AppDataManager.currentUser.value?.id?.trim()?.takeIf { it.isNotEmpty() }
            ?: return
        val restored = BeaconRsvpPersistence.load(tokenStorage, uid)
        if (restored.isEmpty()) return
        _beaconRsvpById.update { current -> current + restored }
    }

    private fun persistBeaconRsvpCache() {
        viewModelScope.launch {
            val uid = SupabaseConfig.client.auth.currentUserOrNull()?.id?.trim()?.takeIf { it.isNotEmpty() }
                ?: AppDataManager.currentUser.value?.id?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@launch
            BeaconRsvpPersistence.save(tokenStorage, uid, _beaconRsvpById.value)
        }
    }

    private fun updateBeaconRsvpCache(transform: (Map<String, BeaconRsvpCacheEntry>) -> Map<String, BeaconRsvpCacheEntry>) {
        _beaconRsvpById.update(transform)
        persistBeaconRsvpCache()
    }

    /**
     * Hydrate map state immediately from [AppDataManager]'s eager beacon/hub prefetch so the map is
     * already populated on first render (prefetch runs in parallel with connections at app load).
     */
    private fun seedFromAppDataPrefetch() {
        applyPrefetchedBeacons(AppDataManager.prefetchedMapBeacons.value)
        applyPrefetchedHubs(AppDataManager.prefetchedCommunityHubs.value)
        viewModelScope.launch {
            AppDataManager.prefetchedMapBeacons.collect { applyPrefetchedBeacons(it) }
        }
        viewModelScope.launch {
            AppDataManager.prefetchedCommunityHubs.collect { applyPrefetchedHubs(it) }
        }
    }

    private fun markDiscoveryProximityFetchCompleted() {
        _discoveryProximityFetchCompleted.value = true
    }

    private fun applyPrefetchedBeacons(list: List<MapBeacon>) {
        if (list.isEmpty()) return
        _mapBeacons.update { current -> mergeMapBeaconLists(current, list) }
        markDiscoveryProximityFetchCompleted()
    }

    private fun applyPrefetchedHubs(rows: List<compose.project.click.click.data.api.CommunityHubNearbyDto>) {
        if (rows.isEmpty()) return
        val incoming = rows.map { dto ->
            CommunityHubPin(
                hubId = dto.hubId,
                name = dto.name,
                latitude = dto.latitude,
                longitude = dto.longitude,
                radiusMeters = dto.radiusMeters,
                activeUserCount = dto.activeUserCount,
            )
        }
        _communityHubs.update { current -> mergeCommunityHubLists(current, incoming) }
        markDiscoveryProximityFetchCompleted()
    }

    private fun observeAppData() {
        viewModelScope.launch {
            combine(
                AppDataManager.connections,
                AppDataManager.connectedUsers,
                AppDataManager.archivedConnectionIds,
                AppDataManager.hiddenConnectionIds,
                AppDataManager.coreConnectionIds,
                AppDataManager.isDataLoaded,
                AppDataManager.isLoading,
                AppDataManager.locationPreferences,
                _zoomLevel,
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val connections = values[0] as List<Connection>
                val connectedUsers = values[1] as Map<String, User>
                val archivedIds = values[2] as Set<String>
                val hiddenIds = values[3] as Set<String>
                val coreIds = values[4] as Set<String>
                val isDataLoaded = values[5] as Boolean
                val isLoading = values[6] as Boolean
                val locationPrefs = values[7] as LocationPreferences
                val zoom = values[8] as Double
                Nonuple(
                    connections,
                    connectedUsers,
                    archivedIds,
                    hiddenIds,
                    coreIds,
                    isDataLoaded,
                    isLoading,
                    locationPrefs,
                    zoom,
                )
            }.collectLatest { (connections, connectedUsers, archivedIds, hiddenIds, coreIds, isDataLoaded, isLoading, locationPrefs, zoom) ->
                when {
                    // `archivedIds` is read so archive/unarchive recomputes the map when the connections list is unchanged.
                    isDataLoaded && (archivedIds.isNotEmpty() || archivedIds.isEmpty()) -> {
                        // Memory map: show full history (incl. per-user archived) but never removed/hidden rows.
                        val mapConnections = connections.filter { it.id !in hiddenIds }
                        _mapState.value = MapState.Success(mapConnections)
                        val mapVisibleConnections = if (locationPrefs.showOnMapEnabled) {
                            mapConnections
                        } else {
                            mapConnections.filter { it.id in coreIds }
                        }
                        ensureDefaultCameraTarget(mapVisibleConnections)
                        updateRenderData(mapVisibleConnections, zoomForClusteringRender(zoom))
                        refreshSelectedConnectionUser(connectedUsers)
                    }
                    isLoading -> {
                        _mapState.value = MapState.Loading
                    }
                    else -> {
                        _mapState.value = MapState.Success(emptyList())
                        _renderData.value = MapRenderData.Clusters(emptyList())
                    }
                }
            }
        }
        viewModelScope.launch {
            combine(
                combine(
                    _mapState,
                    AppDataManager.locationPreferences,
                    AppDataManager.hiddenConnectionIds,
                    AppDataManager.coreConnectionIds,
                    _zoomLevel,
                ) { state, prefs, hidden, coreIds, zoom ->
                    Quintuple(state, prefs, hidden, coreIds, zoom)
                },
                _mapBeacons,
                _selectedLayerFilters,
            ) { base, _, _ ->
                base
            }.collectLatest { (state, prefs, hidden, coreIds, zoom) ->
                if (state !is MapState.Success) return@collectLatest
                val mapVisible = state.connections.filter { it.id !in hidden }
                val visible = if (prefs.showOnMapEnabled) {
                    mapVisible
                } else {
                    mapVisible.filter { it.id in coreIds }
                }
                updateRenderData(visible, zoomForClusteringRender(zoom))
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
    private fun updateRenderData(connections: List<Connection>, zoom: Double) {
        renderDataJob?.cancel()
        incrementalPopulationJob?.cancel()
        val layers = _selectedLayerFilters.value
        val beaconsRaw = _mapBeacons.value
        renderDataJob = viewModelScope.launch {
            val rendered = withContext(Dispatchers.Default) {
                val connectedUsersSnapshot = AppDataManager.connectedUsers.value
                val currentUserId = AppDataManager.currentUser.value?.id
                val showConnections = layers.contains(MapLayerFilter.ALL) ||
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
                )
            }

            // If we're in IndividualPins mode and there are many points, publish only the nearest
            // INITIAL_PIN_CAP immediately and incrementally add the rest in batches. This reduces
            // initial marker creation work and improves perceived map performance.
            if (rendered is MapRenderData.IndividualPins && rendered.points.size > INITIAL_PIN_CAP) {
                val allPoints = rendered.points

                // Choose an anchor (camera center) to sort by proximity.
                val anchor = _cameraTarget.value ?: _defaultCameraTarget.value
                val (anchorLat, anchorLon) = when {
                    anchor != null -> anchor.latitude to anchor.longitude
                    allPoints.isNotEmpty() -> allPoints.first().latitude to allPoints.first().longitude
                    else -> null to null
                }

                val initialPoints = if (anchorLat != null && anchorLon != null) {
                    allPoints.sortedBy { haversineDistance(anchorLat, anchorLon, it.latitude, it.longitude) }
                        .take(INITIAL_PIN_CAP)
                } else {
                    allPoints.take(INITIAL_PIN_CAP)
                }

                _renderData.value = MapRenderData.IndividualPins(points = initialPoints, beacons = rendered.beacons)

                // Incrementally add remaining points in batches to avoid CPU/GC spikes
                incrementalPopulationJob = viewModelScope.launch {
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
     * Optional `filters` query for `/api/beacons` derived from the active layer chips.
     */
    private fun beaconTypesQueryForLayers(layers: Set<MapLayerFilter>): String? {
        if (layers.contains(MapLayerFilter.ALL)) return null
        val types = LinkedHashSet<String>()
        if (layers.contains(MapLayerFilter.SOUNDTRACKS)) types.add("soundtrack")
        if (layers.contains(MapLayerFilter.ALERTS_UTILITIES)) {
            types.add("sos")
            types.add("study")
            types.add("hazard")
            types.add("utility")
            types.add("hazard_utility")
        }
        if (layers.contains(MapLayerFilter.SOCIAL_VIBES)) {
            types.add("recreation")
            types.add("hobby")
            types.add("swag")
            types.add("capacity")
            types.add("transit")
            types.add("scavenger")
        }
        if (layers.contains(MapLayerFilter.EVENTS)) {
            types.add("event")
        }
        return if (types.isEmpty()) null else types.joinToString(",")
    }

    private fun filterBeaconsForLayers(
        beacons: List<MapBeacon>,
        layers: Set<MapLayerFilter>,
    ): List<MapBeacon> {
        if (layers.contains(MapLayerFilter.ALL)) return beacons
        val out = mutableListOf<MapBeacon>()
        for (b in beacons) {
            val include = when (b.kind) {
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
     * Computes a one-time default camera based on all valid connection coordinates.
     */
    private fun ensureDefaultCameraTarget(connections: List<Connection>) {
        if (_defaultCameraTarget.value != null) return

        val valid = connections.mapNotNull { c -> c.connectionMapGeo()?.let { g -> c to g } }

        if (valid.isEmpty()) return

        val minLat = valid.minOf { it.second.lat }
        val maxLat = valid.maxOf { it.second.lat }
        val minLon = valid.minOf { it.second.lon }
        val maxLon = valid.maxOf { it.second.lon }

        val bounds = BoundingBox(minLat = minLat, maxLat = maxLat, minLon = minLon, maxLon = maxLon)
        val targetZoom = calculateZoomForBounds(bounds).coerceIn(4.0, 16.0)

        val computedTarget = CameraTarget(
            latitude = bounds.centerLat,
            longitude = bounds.centerLon,
            zoom = targetZoom
        )
        _defaultCameraTarget.value = computedTarget
        lastKnownCameraTarget = computedTarget

        // If we don't already have an active camera move, apply this default as a one-shot initial camera.
        if (_cameraTarget.value == null) {
            _cameraTarget.value = computedTarget
        }

        // Use the computed zoom only for first initialization to match initial map framing.
        if (_zoomLevel.value == 10.0) {
            _zoomLevel.value = targetZoom
        }

        // Seed discovery feed from the map camera center (connections cluster), not GPS alone.
        // Simulators often report a default location far from test hubs; without this, hubs only
        // appear after the user expands the map and viewport bounds fire a fetch.
        prefetchDiscoveryProximityData(showPulse = false, markInitialComplete = false)
    }

    fun toggleLayerFilter(filter: MapLayerFilter) {
        _pinRenderZoomFloor.value = null
        val cur = _selectedLayerFilters.value.toMutableSet()
        if (filter == MapLayerFilter.ALL) {
            if (MapLayerFilter.ALL in cur) {
                cur.clear()
                cur.addAll(defaultMapLayerFilters())
            } else {
                cur.clear()
                cur.add(MapLayerFilter.ALL)
            }
        } else {
            cur.remove(MapLayerFilter.ALL)
            if (filter in cur) cur.remove(filter) else cur.add(filter)
            if (cur.isEmpty()) {
                cur.addAll(defaultMapLayerFilters())
            }
        }
        _selectedLayerFilters.value = cur.toSet()
        _visibleBounds.value?.let { scheduleBeaconFetchForBounds(it, debounceMs = 0L) }
        prefetchDiscoveryProximityData(showPulse = false, markInitialComplete = false)
    }

    fun clearBeaconInsertError() {
        _beaconInsertError.value = null
    }

    fun clearBeaconDropFailureToast() {
        _beaconDropFailureToast.value = null
    }

    fun onBeaconPinTapped(beaconId: String) {
        val beacon = _mapBeacons.value.firstOrNull { it.id == beaconId }
            ?: return
        _selection.value = MapSelection.BeaconSelected(beacon, distanceMeters = null)

        viewModelScope.launch(Dispatchers.Default) {
            val distance = locationService.getHighAccuracyLocation(3500L)?.let { loc ->
                haversineDistance(loc.latitude, loc.longitude, beacon.latitude, beacon.longitude)
            }
            val current = _selection.value as? MapSelection.BeaconSelected ?: return@launch
            if (current.beacon.id == beaconId) {
                _selection.value = current.copy(distanceMeters = distance)
            }
        }
    }

    /**
     * Loads RSVP attendees + signed-up state from click-web. Waits for the Supabase session so
     * cold starts (app switcher kill) do not hit the API before JWT restore and cache a false
     * "not signed up" sentinel.
     */
    fun loadBeaconRsvp(beaconId: String, forceRefresh: Boolean = false) {
        val id = beaconId.trim()
        if (id.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            if (!ensureClickWebAuthReady()) return@launch
            if (!forceRefresh && _beaconRsvpById.value.containsKey(id)) return@launch
            if (id in _beaconRsvpPendingIds.value) return@launch

            _beaconRsvpLoadingIds.update { it + id }
            try {
                mapBeaconRepository.fetchBeaconRsvp(id).fold(
                    onSuccess = { payload ->
                        if (id in _beaconRsvpPendingIds.value) return@fold
                        updateBeaconRsvpCache { current ->
                            current + (id to BeaconRsvpCacheEntry(
                                attendees = payload.attendees,
                                currentUserSignedUp = payload.currentUserSignedUp,
                            ))
                        }
                    },
                    onFailure = {
                        // Keep disk-hydrated cache on failure; do not write a false-negative entry.
                    },
                )
            } finally {
                _beaconRsvpLoadingIds.update { it - id }
            }
        }
    }

    /** Restores/refreshes Supabase session before click-web bearer calls (cold start). */
    private suspend fun ensureClickWebAuthReady(): Boolean {
        val existingToken = SupabaseConfig.client.auth.currentSessionOrNull()?.accessToken?.trim()
        if (!existingToken.isNullOrEmpty()) return true
        if (SupabaseConfig.client.auth.currentSessionOrNull()?.accessToken.isNullOrBlank()) {
            authRepository.restoreSession()
        }
        authRepository.refreshSession()
        return awaitClickWebAuthSession()
    }

    /** Blocks until click-web bearer auth is available, or times out without caching failure. */
    private suspend fun awaitClickWebAuthSession(timeoutMs: Long = 20_000L): Boolean {
        return try {
            withTimeout(timeoutMs) {
                while (true) {
                    val token = SupabaseConfig.client.auth.currentSessionOrNull()?.accessToken?.trim()
                    if (!token.isNullOrEmpty()) return@withTimeout true
                    delay(100)
                }
                @Suppress("UNREACHABLE_CODE")
                false
            }
        } catch (_: TimeoutCancellationException) {
            false
        }
    }

    private fun currentUserAsAttendee(): BeaconAttendeeDto? {
        val user = AppDataManager.currentUser.value ?: return null
        return BeaconAttendeeDto(
            userId = user.id,
            name = user.name?.trim()?.takeIf { it.isNotEmpty() } ?: "You",
            avatarUrl = user.image,
        )
    }

    private fun applyOptimisticRsvp(beaconId: String, signedUp: Boolean) {
        val userId = AppDataManager.currentUser.value?.id ?: return
        updateBeaconRsvpCache { current ->
            val prev = current[beaconId]
            if (signedUp) {
                val attendee = currentUserAsAttendee() ?: return@updateBeaconRsvpCache current
                val mergedAttendees = (prev?.attendees.orEmpty()
                    .filterNot { it.userId == attendee.userId } + attendee)
                    .distinctBy { it.userId }
                current + (beaconId to BeaconRsvpCacheEntry(
                    attendees = mergedAttendees,
                    currentUserSignedUp = true,
                ))
            } else {
                val remaining = prev?.attendees.orEmpty().filterNot { it.userId == userId }
                current + (beaconId to BeaconRsvpCacheEntry(
                    attendees = remaining,
                    currentUserSignedUp = false,
                ))
            }
        }
    }

    private fun restoreRsvpSnapshot(beaconId: String, previous: BeaconRsvpCacheEntry?) {
        updateBeaconRsvpCache { current ->
            when (previous) {
                null -> current - beaconId
                else -> current + (beaconId to previous)
            }
        }
    }

    private suspend fun resolveBeaconDropLocation(): LocationResult? {
        return locationService.getHighAccuracyLocation(4_500L)
            ?: locationService.getCurrentLocation()
            ?: AppDataManager.lastKnownDeviceLocation.value?.let { (lat, lon) ->
                LocationResult(latitude = lat, longitude = lon)
            }
    }

    fun rsvpToBeacon(beaconId: String, onFinished: (Boolean) -> Unit = {}) {
        val id = beaconId.trim()
        if (id.isEmpty() || id in _beaconRsvpPendingIds.value) return
        val previous = _beaconRsvpById.value[id]
        _beaconRsvpPendingIds.update { it + id }
        applyOptimisticRsvp(id, signedUp = true)
        PlatformHapticsPolicy.successNotification()
        viewModelScope.launch {
            if (!ensureClickWebAuthReady()) {
                restoreRsvpSnapshot(id, previous)
                _beaconRsvpPendingIds.update { it - id }
                onFinished(false)
                return@launch
            }
            val cachedLoc = AppDataManager.lastKnownDeviceLocation.value
            mapBeaconRepository.rsvpBeacon(
                beaconId = id,
                latitude = cachedLoc?.first,
                longitude = cachedLoc?.second,
            ).fold(
                onSuccess = { attendee ->
                    updateBeaconRsvpCache { current ->
                        val prev = current[id]
                        val localAttendee = currentUserAsAttendee()
                        val confirmedAttendee = attendee.copy(
                            name = attendee.name.takeIf { it.isNotBlank() } ?: localAttendee?.name ?: "You",
                            avatarUrl = localAttendee?.avatarUrl ?: attendee.avatarUrl,
                        )
                        val mergedAttendees = ((prev?.attendees.orEmpty())
                            .filterNot { it.userId == confirmedAttendee.userId } + confirmedAttendee)
                            .distinctBy { it.userId }
                        current + (id to BeaconRsvpCacheEntry(
                            attendees = mergedAttendees,
                            currentUserSignedUp = true,
                        ))
                    }
                    _beaconRsvpPendingIds.update { it - id }
                    onFinished(true)
                },
                onFailure = {
                    restoreRsvpSnapshot(id, previous)
                    _beaconRsvpPendingIds.update { it - id }
                    onFinished(false)
                },
            )
        }
    }

    /** Cancels the current user's RSVP and removes them from the cached attendee list. */
    fun cancelRsvpToBeacon(beaconId: String, onFinished: (Boolean) -> Unit = {}) {
        val id = beaconId.trim()
        if (id.isEmpty() || id in _beaconRsvpPendingIds.value) return
        val previous = _beaconRsvpById.value[id]
        _beaconRsvpPendingIds.update { it + id }
        applyOptimisticRsvp(id, signedUp = false)
        PlatformHapticsPolicy.successNotification()
        viewModelScope.launch {
            if (!ensureClickWebAuthReady()) {
                restoreRsvpSnapshot(id, previous)
                _beaconRsvpPendingIds.update { it - id }
                onFinished(false)
                return@launch
            }
            val currentUserId = AppDataManager.currentUser.value?.id
            mapBeaconRepository.cancelRsvp(id).fold(
                onSuccess = {
                    updateBeaconRsvpCache { current ->
                        val prev = current[id]
                        val remaining = prev?.attendees.orEmpty()
                            .filterNot { it.userId == currentUserId }
                        current + (id to BeaconRsvpCacheEntry(
                            attendees = remaining,
                            currentUserSignedUp = false,
                        ))
                    }
                    _beaconRsvpPendingIds.update { it - id }
                    onFinished(true)
                },
                onFailure = {
                    restoreRsvpSnapshot(id, previous)
                    _beaconRsvpPendingIds.update { it - id }
                    onFinished(false)
                },
            )
        }
    }

    fun deleteOwnedBeacon(beaconId: String, onFinished: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            mapBeaconRepository.deleteBeacon(beaconId).fold(
                onSuccess = {
                    _mapBeacons.update { list -> list.filterNot { it.id == beaconId } }
                    updateBeaconRsvpCache { it - beaconId }
                    if (_selection.value is MapSelection.BeaconSelected &&
                        (_selection.value as MapSelection.BeaconSelected).beacon.id == beaconId
                    ) {
                        _selection.value = MapSelection.None
                    }
                    PlatformHapticsPolicy.successNotification()
                    onFinished(true)
                },
                onFailure = {
                    _beaconDropFailureToast.value = it.message ?: "Could not delete beacon"
                    onFinished(false)
                },
            )
        }
    }

    fun updateOwnedBeaconDescription(
        beaconId: String,
        description: String,
        onFinished: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            val patch = MapBeaconPatchBody(
                metadata = buildJsonObject { put("description", description.trim()) },
            )
            mapBeaconRepository.updateBeacon(beaconId, patch).fold(
                onSuccess = { updated ->
                    _mapBeacons.update { list ->
                        list.map { if (it.id == beaconId) updated else it }
                    }
                    val sel = _selection.value
                    if (sel is MapSelection.BeaconSelected && sel.beacon.id == beaconId) {
                        _selection.value = sel.copy(beacon = updated)
                    }
                    PlatformHapticsPolicy.successNotification()
                    onFinished(true)
                },
                onFailure = {
                    _beaconDropFailureToast.value = it.message ?: "Could not update beacon"
                    onFinished(false)
                },
            )
        }
    }

    fun submitBeaconDrop(
        kind: MapBeaconKind,
        title: String,
        description: String? = null,
        soundtrackUrl: String? = null,
        ttlMs: Long? = null,
        showCreatorName: Boolean = false,
        visibilityAudience: BeaconVisibilityAudience = BeaconVisibilityAudience.EVERYONE,
        eventSchedule: EventSchedule? = null,
        onAcceptedLocally: () -> Unit = {},
        onRejectedEarly: () -> Unit = {},
        onRemoteFinished: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            beaconSubmitMutex.lock()
            _beaconSubmitInFlight.value = true
            try {
            _beaconInsertError.value = null
            _beaconDropFailureToast.value = null
            if (!locationService.hasLocationPermission()) {
                _beaconInsertError.value =
                    "Location is required to drop a community beacon. Enable location in Settings and try again."
                onRejectedEarly()
                onRemoteFinished(false)
                return@launch
            }
            val locationDeferred = async(Dispatchers.Default) { resolveBeaconDropLocation() }
            val trimmedTitle = title.trim()
            val trimmedDescription = description?.trim()?.takeIf { it.isNotEmpty() }
            val metadata: JsonObject? = when (kind) {
                MapBeaconKind.SOUNDTRACK -> {
                    val url = soundtrackUrl?.trim().orEmpty()
                    if (!isValidStreamingUrl(url)) {
                        _beaconInsertError.value = "Enter a valid Spotify, Apple Music, or YouTube link."
                        onRejectedEarly()
                        onRemoteFinished(false)
                        return@launch
                    }
                    buildJsonObject {
                        put("music_url", url)
                    }
                }
                MapBeaconKind.EVENT -> {
                    if (trimmedTitle.isEmpty()) {
                        _beaconInsertError.value = "Please add a title."
                        onRejectedEarly()
                        onRemoteFinished(false)
                        return@launch
                    }
                    if (trimmedTitle.length > 80) {
                        _beaconInsertError.value = "Title must be 80 characters or less."
                        onRejectedEarly()
                        onRemoteFinished(false)
                        return@launch
                    }
                    if (trimmedDescription != null && trimmedDescription.length > 500) {
                        _beaconInsertError.value = "Description must be 500 characters or less."
                        onRejectedEarly()
                        onRemoteFinished(false)
                        return@launch
                    }
                    val schedule = eventSchedule ?: run {
                        _beaconInsertError.value = "Pick event start and end times."
                        onRejectedEarly()
                        onRemoteFinished(false)
                        return@launch
                    }
                    validateEventSchedule(schedule.startEpochMs, schedule.endEpochMs)?.let { err ->
                        _beaconInsertError.value = when (err) {
                            compose.project.click.click.events.EventScheduleValidationError.EndBeforeStart ->
                                "Event end must be after start."
                            compose.project.click.click.events.EventScheduleValidationError.StartInPast ->
                                "Event start must be in the future."
                            compose.project.click.click.events.EventScheduleValidationError.DurationExceedsOneMonth ->
                                "Events can last at most 1 month."
                        }
                        onRejectedEarly()
                        onRemoteFinished(false)
                        return@launch
                    }
                    buildJsonObject {
                        put("title", trimmedTitle)
                        trimmedDescription?.let { put("description", it) }
                        eventScheduleMetadata(schedule).forEach { (k, v) -> put(k, v) }
                    }
                }
                MapBeaconKind.SOS, MapBeaconKind.HAZARD, MapBeaconKind.UTILITY, MapBeaconKind.STUDY -> {
                    if (trimmedTitle.isEmpty()) {
                        _beaconInsertError.value = "Please add a title."
                        onRejectedEarly()
                        onRemoteFinished(false)
                        return@launch
                    }
                    if (trimmedTitle.length > 80) {
                        _beaconInsertError.value = "Title must be 80 characters or less."
                        onRejectedEarly()
                        onRemoteFinished(false)
                        return@launch
                    }
                    if (trimmedDescription != null && trimmedDescription.length > 500) {
                        _beaconInsertError.value = "Description must be 500 characters or less."
                        onRejectedEarly()
                        onRemoteFinished(false)
                        return@launch
                    }
                    buildJsonObject {
                        put("title", trimmedTitle)
                        trimmedDescription?.let { put("description", it) }
                    }
                }
                else -> {
                    if (trimmedTitle.isEmpty()) {
                        _beaconInsertError.value = "Please add a title."
                        onRejectedEarly()
                        onRemoteFinished(false)
                        return@launch
                    }
                    if (trimmedTitle.length > 80) {
                        _beaconInsertError.value = "Title must be 80 characters or less."
                        onRejectedEarly()
                        onRemoteFinished(false)
                        return@launch
                    }
                    if (trimmedDescription != null && trimmedDescription.length > 500) {
                        _beaconInsertError.value = "Description must be 500 characters or less."
                        onRejectedEarly()
                        onRemoteFinished(false)
                        return@launch
                    }
                    buildJsonObject {
                        put("title", trimmedTitle)
                        trimmedDescription?.let { put("description", it) }
                    }
                }
            }
            val loc = locationDeferred.await()
                ?: run {
                    _beaconInsertError.value =
                        "Could not read GPS. Enable location and try again."
                    onRejectedEarly()
                    onRemoteFinished(false)
                    return@launch
                }
            val squadSession = CollaborationSessionManager.activeMapDropSession()
            val eventExpiresIso = eventSchedule?.endEpochMs?.let {
                kotlinx.datetime.Instant.fromEpochMilliseconds(it).toString()
            }
            val insert = MapBeaconInsert(
                kind = kind.apiValue,
                lat = loc.latitude,
                lon = loc.longitude,
                metadata = metadata,
                ttlMs = when {
                    kind == MapBeaconKind.SOUNDTRACK -> null
                    kind == MapBeaconKind.EVENT -> null
                    else -> ttlMs ?: (6L * 60L * 60_000L)
                },
                expiresAtIso = eventExpiresIso,
                showCreatorName = showCreatorName,
                visibilityAudience = visibilityAudience.apiValue,
                encounterId = squadSession?.encounterId,
            )
            val optimisticId = "optimistic:${Clock.System.now().toEpochMilliseconds()}:${Random.Default.nextInt()}"
            val optimisticBeacon = MapBeacon(
                id = optimisticId,
                kind = kind,
                latitude = loc.latitude,
                longitude = loc.longitude,
                metadata = parseMapBeaconMetadata(metadata),
                createdByUserId = AppDataManager.currentUser.value?.id,
                createdAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                expiresAtEpochMs = eventSchedule?.endEpochMs,
                sourceBeaconType = insert.kind,
                showCreatorName = showCreatorName,
            )
            _mapBeacons.value = _mapBeacons.value + optimisticBeacon
            EventReminderCoordinator.rememberBeacon(optimisticBeacon)
            PlatformHapticsPolicy.heavyImpact()
            PlatformHapticsPolicy.successNotification()
            onAcceptedLocally()

            val insertResult = mapBeaconRepository.insertBeacon(insert)
            insertResult.fold(
                onSuccess = { serverBeacon ->
                    _mapBeacons.update { current ->
                        mergeMapBeaconLists(
                            current.filter { it.id != optimisticId },
                            listOf(serverBeacon),
                        )
                    }
                    EventReminderCoordinator.rememberBeacon(serverBeacon)
                    refreshBeaconsAfterDrop(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        confirmedBeacon = serverBeacon,
                    )
                    onRemoteFinished(true)
                    PlatformHapticsPolicy.heavyImpact()
                    PlatformHapticsPolicy.successNotification()
                },
                onFailure = { e ->
                    _mapBeacons.value = _mapBeacons.value.filter { it.id != optimisticId }
                    _beaconDropFailureToast.value = e.message ?: "Could not drop beacon"
                    onRemoteFinished(false)
                },
            )
            } finally {
                _beaconSubmitInFlight.value = false
                beaconSubmitMutex.unlock()
            }
        }
    }

    // URL validation is now in compose.project.click.click.util.isValidStreamingUrl

    /**
     * Update the current zoom level
     */
    fun setZoomLevel(zoom: Double) {
        if (!zoom.isFinite()) return
        val coerced = zoom.coerceIn(2.0, 20.0)
        // Drop single-shot readouts that would snap from a city-level view to ~world scale
        // (bad spans during annotation churn / projection glitches on native maps).
        if (coerced < 5.0 && _zoomLevel.value > 8.0 && (_zoomLevel.value - coerced) > 3.0) {
            return
        }

        val pendingTarget = pendingProgrammaticZoomTarget
        if (pendingTarget != null) {
            val now = Clock.System.now().toEpochMilliseconds()
            val ageMs = now - pendingProgrammaticZoomSetAtMs
            // MapKit span → zoom can disagree with our metersForZoom ladder by ~1 level; keep the
            // guard loose so we clear pending when the map has essentially arrived.
            val reachedPendingTarget = abs(coerced - pendingTarget) <= 1.0
            if (reachedPendingTarget) {
                pendingProgrammaticZoomTarget = null
            } else if (ageMs > 1500L) {
                // Do not apply this stale reading: it often underestimates zoom on iOS and would
                // snap _zoomLevel back below [clusterThreshold], reverting to cluster markers.
                pendingProgrammaticZoomTarget = null
                return
            } else {
                return
            }
        }

        if (abs(_zoomLevel.value - coerced) <= 0.01) return
        _zoomLevel.value = coerced

        if (pendingProgrammaticZoomTarget == null && coerced < clusterThreshold - 0.05) {
            _pinRenderZoomFloor.value = null
        }

        _visibleBounds.value?.let { bounds ->
            persistCameraTarget(
                latitude = bounds.centerLat,
                longitude = bounds.centerLon,
                zoom = coerced
            )
        }
    }

    /**
     * Update visible bounds from outside (e.g., the platform map callback)
     */
    fun updateVisibleBounds(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {
        if (!minLat.isFinite() || !maxLat.isFinite() || !minLon.isFinite() || !maxLon.isFinite()) return
        val latSpan = abs(maxLat - minLat)
        val lonSpan = abs(maxLon - minLon)
        if (latSpan < 1e-7 || lonSpan < 1e-7) return
        if (latSpan > 160.0 || lonSpan > 340.0) return
        val bounds = BoundingBox(minLat, maxLat, minLon, maxLon)
        _visibleBounds.value = bounds
        persistCameraTarget(
            latitude = bounds.centerLat,
            longitude = bounds.centerLon,
            zoom = _zoomLevel.value
        )
        scheduleBeaconFetchForBounds(bounds)
    }

    private fun scheduleBeaconFetchForBounds(bounds: BoundingBox, debounceMs: Long = 400L) {
        fetchProximityLayersForBounds(bounds, debounceMs, DiscoveryFetchSlot.MapViewport)
    }

    private fun persistCameraTarget(latitude: Double, longitude: Double, zoom: Double) {
        if (!latitude.isFinite() || !longitude.isFinite() || !zoom.isFinite()) return
        val z = zoom.coerceIn(2.0, 20.0)
        // Never persist continent/world scale; it becomes the next session's "restore camera".
        if (z < 4.0) return

        val candidate = CameraTarget(
            latitude = latitude,
            longitude = longitude,
            zoom = z
        )

        val previous = lastKnownCameraTarget
        val changed = previous == null ||
            abs(previous.latitude - candidate.latitude) > 0.000001 ||
            abs(previous.longitude - candidate.longitude) > 0.000001 ||
            abs(previous.zoom - candidate.zoom) > 0.01

        if (changed) {
            lastKnownCameraTarget = candidate
        }
    }

    /**
     * Estimate visible bounds from zoom level and camera target.
     * This is used as a fallback when the platform map doesn't report bounds.
     */
    private fun estimateVisibleBounds() {
        fun validConnections(): List<Connection> {
            val state = _mapState.value
            if (state !is MapState.Success) return emptyList()
            return state.connections.filter {
                val g = it.connectionMapGeo()
                g != null && g.lat.isFinite() && g.lon.isFinite() && !(g.lat == 0.0 && g.lon == 0.0)
            }
        }

        val center = _cameraTarget.value
        val centerLat = center?.latitude ?: run {
            val connections = validConnections()
            if (connections.isNotEmpty()) {
                connections.mapNotNull { it.connectionMapGeo()?.lat }.average()
            } else return
        }
        val centerLon = center?.longitude ?: run {
            val connections = validConnections()
            if (connections.isNotEmpty()) {
                connections.mapNotNull { it.connectionMapGeo()?.lon }.average()
            } else return
        }

        // Estimate viewport span based on zoom level
        // At zoom 10, ~30 miles visible; at zoom 16, ~0.5 miles
        val latSpan = 180.0 / 2.0.pow(_zoomLevel.value - 1)
        val lonSpan = 360.0 / 2.0.pow(_zoomLevel.value - 1)

        _visibleBounds.value = BoundingBox(
            minLat = centerLat - latSpan / 2,
            maxLat = centerLat + latSpan / 2,
            minLon = centerLon - lonSpan / 2,
            maxLon = centerLon + lonSpan / 2
        )
    }

    private fun anchorLatLonForProgrammaticCamera(): Pair<Double, Double>? {
        lastKnownCameraTarget?.let { return it.latitude to it.longitude }
        _visibleBounds.value?.let { return it.centerLat to it.centerLon }
        _defaultCameraTarget.value?.let { return it.latitude to it.longitude }
        val state = _mapState.value
        if (state is MapState.Success) {
            val geo = state.connections.firstNotNullOfOrNull { it.connectionMapGeo() }
            if (geo != null) return geo.lat to geo.lon
        }
        return null
    }

    /**
     * Zoom in
     */
    fun zoomIn() {
        val target = minOf(_zoomLevel.value + 1.0, 20.0)
        pendingProgrammaticZoomTarget = target
        pendingProgrammaticZoomSetAtMs = Clock.System.now().toEpochMilliseconds()
        anchorLatLonForProgrammaticCamera()?.let { (lat, lon) ->
            _cameraTarget.value = CameraTarget(latitude = lat, longitude = lon, zoom = target)
        }
        _zoomLevel.value = target
    }

    /**
     * Zoom out
     */
    fun zoomOut() {
        val target = maxOf(_zoomLevel.value - 1.0, 2.0)
        if (target < clusterThreshold - 0.25) {
            _pinRenderZoomFloor.value = null
        }
        pendingProgrammaticZoomTarget = target
        pendingProgrammaticZoomSetAtMs = Clock.System.now().toEpochMilliseconds()
        anchorLatLonForProgrammaticCamera()?.let { (lat, lon) ->
            _cameraTarget.value = CameraTarget(latitude = lat, longitude = lon, zoom = target)
        }
        _zoomLevel.value = target
    }

    /**
     * Resolves a tapped cluster marker to [MapCluster] using the latest [renderData] snapshot
     * inside the ViewModel (avoids races with Compose where [renderData] already flipped to pins).
     */
    fun onClusterTappedFromMap(clusterId: String) {
        fun findCluster(): MapCluster? =
            (_renderData.value as? MapRenderData.Clusters)?.clusters?.find { it.id == clusterId }

        findCluster()?.let {
            onClusterTapped(it)
            return
        }
        // [updateRenderData] runs off the main thread; a tap can land between zoom update and publish.
        viewModelScope.launch {
            delay(64)
            findCluster()?.let { onClusterTapped(it) }
        }
    }

    /**
     * Called when a cluster (hub) is tapped
     * Zooms in to reveal individual pins
     */
    fun onClusterTapped(cluster: MapCluster) {
        _selection.value = MapSelection.ClusterSelected(cluster)
        
        // Calculate zoom level to fit the cluster bounds
        val bounds = cluster.boundingBox
        val targetZoom = maxOf(clusterThreshold + 1, calculateZoomForBounds(bounds))
        _pinRenderZoomFloor.value = maxOf(clusterThreshold + 0.25, targetZoom)
        
        // Animate camera to cluster center with appropriate zoom
        _cameraTarget.value = CameraTarget(
            latitude = bounds.centerLat,
            longitude = bounds.centerLon,
            zoom = targetZoom
        )
        
        // Update zoom level to trigger rendering change
        pendingProgrammaticZoomTarget = targetZoom
        pendingProgrammaticZoomSetAtMs = Clock.System.now().toEpochMilliseconds()
        _zoomLevel.value = targetZoom
    }

    /**
     * Called when an individual connection pin is tapped
     * Opens the connection bottom sheet
     */
    fun onConnectionTapped(point: ConnectionMapPoint) {
        viewModelScope.launch {
            // Find the other user in this connection
            val currentUserId = AppDataManager.currentUser.value?.id
            val otherUserId = point.connection.user_ids.find { it != currentUserId }
            val otherUser = otherUserId?.let { AppDataManager.getConnectedUser(it) }
            
            _selection.value = MapSelection.ConnectionSelected(point, otherUser)
        }
    }

    fun onMapPinTapped(pin: MapPin) {
        if (pin.kind == MapPinKind.COMMUNITY_HUB || pin.id.startsWith("hub:")) {
            val raw = pin.id.removePrefix("hub:")
            val hub = _communityHubs.value.firstOrNull { it.hubId == raw }
                ?: return
            _selection.value = MapSelection.HubSelected(
                hub = hub,
                distanceMeters = null,
                canJoinGeofence = false,
            )
            viewModelScope.launch(Dispatchers.Default) {
                val distance = locationService.getHighAccuracyLocation(4500L)?.let { loc ->
                    haversineDistance(loc.latitude, loc.longitude, hub.latitude, hub.longitude)
                }
                val canJoin = distance != null && distance <= 200.0
                val current = _selection.value as? MapSelection.HubSelected ?: return@launch
                if (current.hub.hubId == raw) {
                    _selection.value = current.copy(
                        distanceMeters = distance,
                        canJoinGeofence = canJoin,
                    )
                }
            }
            return
        }
        if (pin.id.startsWith("beacon:")) {
            val raw = pin.id.removePrefix("beacon:")
            onBeaconPinTapped(raw)
        } else {
            val state = _renderData.value
            val point = when (state) {
                is MapRenderData.IndividualPins ->
                    state.points.firstOrNull { it.connection.id == pin.id }
                is MapRenderData.Clusters ->
                    state.clusters.flatMap { it.points }.firstOrNull { it.connection.id == pin.id }
            }
            if (point != null) onConnectionTapped(point)
        }
    }

    private fun refreshSelectedConnectionUser(connectedUsers: Map<String, User>) {
        val selected = _selection.value as? MapSelection.ConnectionSelected ?: return
        val currentUserId = AppDataManager.currentUser.value?.id
        val otherUserId = selected.point.connection.user_ids.find { it != currentUserId } ?: return
        val refreshedUser = connectedUsers[otherUserId] ?: return
        if (refreshedUser != selected.otherUser) {
            _selection.value = selected.copy(otherUser = refreshedUser)
        }
    }

    /**
     * Clear current selection
     */
    fun clearSelection() {
        _selection.value = MapSelection.None
    }

    /**
     * Toggle Ghost Mode on/off
     */
    fun toggleGhostMode() {
        AppDataManager.toggleGhostMode()
    }

    /**
     * Clear camera target after animation completes
     */
    fun onCameraAnimationComplete() {
        pendingProgrammaticZoomTarget = null
        val target = _cameraTarget.value
        _cameraTarget.value = null
        // Re-assert zoom from the programmatic target so map readouts during the animation
        // cannot leave _zoomLevel out of sync with pin-vs-cluster mode.
        if (target != null) {
            val z = target.zoom.coerceIn(2.0, 20.0)
            if (abs(_zoomLevel.value - z) > 0.02) {
                _zoomLevel.value = z
            }
        }
    }

    /**
     * Called whenever the map screen is entered so we can restore the last viewport.
     */
    fun onMapScreenEntered() {
        if (_cameraTarget.value == null) {
            val raw = lastKnownCameraTarget ?: _defaultCameraTarget.value
            if (raw != null) {
                val safeZoom = raw.zoom.coerceIn(4.0, 20.0)
                val target = if (abs(raw.zoom - safeZoom) > 0.01) {
                    CameraTarget(latitude = raw.latitude, longitude = raw.longitude, zoom = safeZoom)
                } else {
                    raw
                }
                _cameraTarget.value = target
                if (abs(_zoomLevel.value - target.zoom) > 0.01) {
                    _zoomLevel.value = target.zoom
                }
            }
        }
        ensureDiscoveryFeedLoaded()
    }

    /**
     * User-initiated discovery feed reload (pull-to-refresh or header button).
     */
    fun refreshDiscoveryFeed() {
        prefetchDiscoveryProximityData(showPulse = true, markInitialComplete = true)
    }

    /**
     * Silent refresh after the user opens the expanded map (viewport / more detail).
     */
    fun refreshDiscoveryFromMapInteraction() {
        prefetchDiscoveryProximityData(showPulse = false, markInitialComplete = false)
    }

    /**
     * Loads discovery hubs/beacons on first map visit if startup prefetch has not finished yet.
     */
    private fun ensureDiscoveryFeedLoaded() {
        if (_discoveryProximityFetchCompleted.value) {
            refreshDiscoveryFromMapInteraction()
            return
        }
        if (AppDataManager.discoveryMapPrefetchComplete.value) {
            markDiscoveryProximityFetchCompleted()
            refreshDiscoveryFromMapInteraction()
            return
        }
        prefetchDiscoveryProximityData(showPulse = true, markInitialComplete = true)
    }

    fun prefetchDiscoveryProximityData(
        showPulse: Boolean = false,
        markInitialComplete: Boolean = true,
    ) {
        if (AppDataManager.currentUser.value == null) {
            if (markInitialComplete) markDiscoveryProximityFetchCompleted()
            return
        }
        discoveryProximityJob?.cancel()
        val seq = ++discoveryFetchSeq
        if (showPulse) _discoveryFeedLoading.value = true
        discoveryProximityJob = viewModelScope.launch {
            var fetchRan = false
            try {
            val centers = resolveDiscoveryProximityCenters()
            if (centers.isEmpty()) return@launch
            if (seq != discoveryFetchSeq) return@launch

            val layers = _selectedLayerFilters.value
            val wantHubs = layersWantHubFetch(layers)
            val wantBeacons = layersWantBeaconFetch(layers)
            if (!wantHubs && !wantBeacons) return@launch
            fetchRan = true

            coroutineScope {
                if (wantHubs) {
                    val hubRows = centers.map { (lat, lon) ->
                        async {
                            val bounds = boundsAroundPoint(lat, lon, discoveryProximityRadiusMeters)
                            mapBeaconRepository.fetchNearbyCommunityHubs(
                                minLat = bounds.minLat,
                                maxLat = bounds.maxLat,
                                minLon = bounds.minLon,
                                maxLon = bounds.maxLon,
                            ).getOrNull().orEmpty()
                        }
                    }.awaitAll().flatten()
                    val incoming = hubRows.map { dto ->
                        CommunityHubPin(
                            hubId = dto.hubId,
                            name = dto.name,
                            latitude = dto.latitude,
                            longitude = dto.longitude,
                            radiusMeters = dto.radiusMeters,
                            activeUserCount = dto.activeUserCount,
                        )
                    }
                    if (incoming.isNotEmpty()) {
                        _communityHubs.update { current -> mergeCommunityHubLists(current, incoming) }
                    }
                }
                if (wantBeacons) {
                    val beaconRows = centers.map { (lat, lon) ->
                        async {
                            val bounds = boundsAroundPoint(lat, lon, discoveryProximityRadiusMeters)
                            mapBeaconRepository.fetchLocalBeacons(
                                minLat = bounds.minLat,
                                maxLat = bounds.maxLat,
                                minLon = bounds.minLon,
                                maxLon = bounds.maxLon,
                                beaconTypeFilters = beaconTypesQueryForLayers(layers),
                            ).getOrNull().orEmpty()
                        }
                    }.awaitAll().flatten()
                    if (beaconRows.isNotEmpty()) {
                        _mapBeacons.update { current -> mergeMapBeaconLists(current, beaconRows) }
                    }
                }
            }
            } finally {
                if (seq == discoveryFetchSeq) {
                    if (showPulse) _discoveryFeedLoading.value = false
                    if (markInitialComplete) {
                        val hasFeedData = _mapBeacons.value.isNotEmpty() || _communityHubs.value.isNotEmpty()
                        when {
                            fetchRan || hasFeedData -> completeDiscoveryPrefetchAfterSuccess()
                            !canEverResolveProximityCenters() -> finishDiscoveryPrefetchAttempt()
                            discoveryPrefetchAttempts >= maxDiscoveryPrefetchAttempts ->
                                finishDiscoveryPrefetchAttempt()
                            else -> scheduleDiscoveryPrefetchRetry()
                        }
                    }
                }
            }
        }
    }

    /**
     * GPS plus map camera anchors for discovery prefetch. When the two are far apart (common on
     * simulators with a default location), both are queried so the feed is not empty until the
     * user expands the map.
     */
    private suspend fun resolveDiscoveryProximityCenters(): List<Pair<Double, Double>> {
        val raw = mutableListOf<Pair<Double, Double>>()

        AppDataManager.lastKnownDeviceLocation.value?.let { (lat, lon) ->
            raw += lat to lon
        }

        val connectionGeos = AppDataManager.connections.value.mapNotNull { it.connectionMapGeo() }
        if (connectionGeos.isNotEmpty()) {
            raw += connectionGeos.map { it.lat }.average() to connectionGeos.map { it.lon }.average()
        }

        listOfNotNull(
            _cameraTarget.value?.let { it.latitude to it.longitude },
            lastKnownCameraTarget?.let { it.latitude to it.longitude },
            _defaultCameraTarget.value?.let { it.latitude to it.longitude },
        ).forEach { raw += it }

        _visibleBounds.value?.let { bounds ->
            raw += bounds.centerLat to bounds.centerLon
        }

        if (raw.isEmpty()) {
            if (_visibleBounds.value == null) {
                estimateVisibleBounds()
            }
            _visibleBounds.value?.let { bounds ->
                raw += bounds.centerLat to bounds.centerLon
            }
        }

        if (raw.isEmpty() && locationService.hasLocationPermission()) {
            val gps = locationService.getCurrentLocation()
                ?: locationService.getHighAccuracyLocation(1_500L)
            if (gps != null) {
                raw += gps.latitude to gps.longitude
            }
        }

        return dedupeProximityCenters(raw)
    }

    /** Skip redundant fetches when GPS and map camera are essentially the same point. */
    private fun dedupeProximityCenters(centers: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (centers.isEmpty()) return emptyList()
        val out = mutableListOf<Pair<Double, Double>>()
        for ((lat, lon) in centers) {
            val duplicate = out.any { (existingLat, existingLon) ->
                haversineDistance(existingLat, existingLon, lat, lon) < 2_000.0
            }
            if (!duplicate) out += lat to lon
        }
        return out
    }

    private suspend fun refreshBeaconsAfterDrop(
        latitude: Double,
        longitude: Double,
        confirmedBeacon: MapBeacon,
    ) {
        val bounds = _visibleBounds.value
            ?: boundsAroundPoint(latitude, longitude, discoveryProximityRadiusMeters)
        mapBeaconRepository.fetchLocalBeacons(
            minLat = bounds.minLat,
            maxLat = bounds.maxLat,
            minLon = bounds.minLon,
            maxLon = bounds.maxLon,
            beaconTypeFilters = beaconTypesQueryForLayers(_selectedLayerFilters.value),
        ).onSuccess { list ->
            _mapBeacons.update { current ->
                mergeMapBeaconLists(
                    mergeMapBeaconLists(current, listOf(confirmedBeacon)),
                    list,
                )
            }
        }
    }

    private fun boundsAroundPoint(lat: Double, lon: Double, radiusMeters: Double): BoundingBox {
        val latDelta = radiusMeters / 111_320.0
        val lonScale = kotlin.math.cos(lat * kotlin.math.PI / 180.0).coerceAtLeast(0.2)
        val lonDelta = radiusMeters / (111_320.0 * lonScale)
        return BoundingBox(
            minLat = (lat - latDelta).coerceIn(-90.0, 90.0),
            maxLat = (lat + latDelta).coerceIn(-90.0, 90.0),
            minLon = (lon - lonDelta).coerceIn(-180.0, 180.0),
            maxLon = (lon + lonDelta).coerceIn(-180.0, 180.0),
        )
    }

    private enum class DiscoveryFetchSlot { MapViewport, Discovery }

    private fun fetchProximityLayersForBounds(
        bounds: BoundingBox,
        debounceMs: Long,
        jobSlot: DiscoveryFetchSlot,
    ) {
        if (AppDataManager.currentUser.value == null) return

        val seq = when (jobSlot) {
            DiscoveryFetchSlot.MapViewport -> {
                beaconPollJob?.cancel()
                ++beaconFetchSeq
            }
            DiscoveryFetchSlot.Discovery -> ++discoveryFetchSeq
        }

        val job = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            when (jobSlot) {
                DiscoveryFetchSlot.MapViewport -> if (seq != beaconFetchSeq) return@launch
                DiscoveryFetchSlot.Discovery -> if (seq != discoveryFetchSeq) return@launch
            }
            val layers = _selectedLayerFilters.value
            val wantHubs = layersWantHubFetch(layers)
            val wantBeacons = layersWantBeaconFetch(layers)
            if (!wantHubs && !wantBeacons) return@launch

            coroutineScope {
                val hubsDeferred = if (wantHubs) {
                    async {
                        mapBeaconRepository.fetchNearbyCommunityHubs(
                            minLat = bounds.minLat,
                            maxLat = bounds.maxLat,
                            minLon = bounds.minLon,
                            maxLon = bounds.maxLon,
                        )
                    }
                } else {
                    if (jobSlot != DiscoveryFetchSlot.Discovery) {
                        _communityHubs.value = emptyList()
                    }
                    null
                }
                val beaconsDeferred = if (wantBeacons) {
                    async {
                        mapBeaconRepository.fetchLocalBeacons(
                            minLat = bounds.minLat,
                            maxLat = bounds.maxLat,
                            minLon = bounds.minLon,
                            maxLon = bounds.maxLon,
                            beaconTypeFilters = beaconTypesQueryForLayers(layers),
                        )
                    }
                } else {
                    null
                }

                hubsDeferred?.await()?.onSuccess { rows ->
                    val incoming = rows.map { dto ->
                        CommunityHubPin(
                            hubId = dto.hubId,
                            name = dto.name,
                            latitude = dto.latitude,
                            longitude = dto.longitude,
                            radiusMeters = dto.radiusMeters,
                            activeUserCount = dto.activeUserCount,
                        )
                    }
                    _communityHubs.update { current -> mergeCommunityHubLists(current, incoming) }
                }
                beaconsDeferred?.await()?.onSuccess { list ->
                    _mapBeacons.update { current -> mergeMapBeaconLists(current, list) }
                }
            }
        }

        when (jobSlot) {
            DiscoveryFetchSlot.MapViewport -> beaconPollJob = job
            DiscoveryFetchSlot.Discovery -> {
                discoveryProximityJob?.cancel()
                discoveryProximityJob = job
            }
        }
    }

    /**
     * Load connections if not already loaded
     */
    fun loadConnections() {
        if (!AppDataManager.isDataLoaded.value) {
            AppDataManager.initializeData()
        }
    }

    /**
     * Force refresh connections
     */
    fun refresh() {
        AppDataManager.refresh(force = true)
    }

    /**
     * Get statistics about connections on the map
     */
    fun getMapStats(): MapStats {
        val state = _mapState.value
        if (state !is MapState.Success) return MapStats(0, 0, 0, 0)
        
        val connections = state.connections
        val points = connections.mapNotNull { 
            try { it.toMapPoint() } catch (e: Exception) { null }
        }
        
        return MapStats(
            totalConnections = connections.size,
            liveCount = points.count { it.timeState == TimeState.LIVE },
            recentCount = points.count { it.timeState == TimeState.RECENT },
            archiveCount = points.count { it.timeState == TimeState.ARCHIVE }
        )
    }
    
    /**
     * Subscribe to real-time changes on the connections table.
     * Triggers an AppDataManager refresh on any change so map pins stay current.
     */
    private fun subscribeToConnectionChanges() {
        viewModelScope.launch {
            try {
                val channel = SupabaseConfig.client.channel("map:connections")
                connectionsChannel = channel

                merge(
                    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "connections"
                    },
                    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "connection_archives"
                    },
                    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "connection_hidden"
                    },
                ).onEach {
                    AppDataManager.refresh(force = true)
                }.launchIn(this)

                channel.subscribe()
            } catch (e: Exception) {
                println("MapViewModel: Error subscribing to connections: ${e.redactedRestMessage()}")
            }
        }
    }
    
    override fun onCleared() {
        // Grab the channel ref before super.onCleared() kills viewModelScope.
        val channel = connectionsChannel
        connectionsChannel = null
        super.onCleared()
        if (channel != null) {
            teardownBlocking { channel.unsubscribe() }
        }
        mapBeaconRepository.close()
    }

    /**
     * Send a nudge to a connection.
     * This sends a special emoji message ("👋") to the connection's chat.
     */
    fun sendNudge(connectionId: String, otherUserName: String) {
        val currentUser = AppDataManager.currentUser.value ?: return
        val connection = (mapState.value as? MapState.Success)
            ?.connections?.firstOrNull { it.id == connectionId } ?: return
        val chatId = connection.chat.id ?: return

        viewModelScope.launch {
            val currentName = currentUser.name ?: "Someone"
            val msg = chatRepository.sendMessage(
                chatId = chatId,
                userId = currentUser.id,
                content = "👋 $currentName nudged you!"
            )
            _nudgeResult.value = if (msg != null) {
                "Nudge sent to $otherUserName!"
            } else {
                "Failed to send nudge"
            }
        }
    }

    fun clearNudgeResult() {
        _nudgeResult.value = null
    }
}

/**
 * Camera target for map animations
 */
data class CameraTarget(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double
)

/**
 * Map statistics
 */
data class MapStats(
    val totalConnections: Int,
    val liveCount: Int,
    val recentCount: Int,
    val archiveCount: Int
)

/**
 * Helper for combining 4 flows
 */
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

/**
 * Helper for combining 5 flows
 */
private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

private data class Sextuple<A, B, C, D, E, F>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F
)

private data class Septuple<A, B, C, D, E, F, G>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F,
    val seventh: G
)

private data class Octuple<A, B, C, D, E, F, G, H>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F,
    val seventh: G,
    val eighth: H,
)

private data class Nonuple<A, B, C, D, E, F, G, H, I>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F,
    val seventh: G,
    val eighth: H,
    val ninth: I,
)
