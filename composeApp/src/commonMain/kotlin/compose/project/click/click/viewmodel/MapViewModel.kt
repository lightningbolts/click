package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.PlatformHapticsPolicy
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.collapseOneToOneConnectionsByPeer // pragma: allowlist secret
import compose.project.click.click.data.models.LocationPreferences // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.BeaconVisibilityAudience // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconInsert // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.api.BeaconAttendeeDto
import compose.project.click.click.data.api.MapBeaconPatchBody
import compose.project.click.click.data.models.parseMapBeaconMetadata // pragma: allowlist secret
import compose.project.click.click.data.models.parseEpochMs
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.repository.AuthRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.ChatRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.MapBeaconRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseChatRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.BeaconRsvpPersistence // pragma: allowlist secret
import compose.project.click.click.data.storage.BeaconEngagementPersistence
import compose.project.click.click.data.api.BeaconEngagementHttpException
import compose.project.click.click.data.api.EngagementTelemetryBody
import compose.project.click.click.getPlatform
import compose.project.click.click.events.beaconCheckInFailureMessage
import compose.project.click.click.events.EventVenueScale
import compose.project.click.click.events.EVENT_VENUE_SCALE_METADATA_KEY
import compose.project.click.click.events.EVENT_CHECK_IN_RADIUS_METADATA_KEY
import compose.project.click.click.events.resolveEventCheckInRadiusMeters
import compose.project.click.click.events.EventReminderCoordinator
import compose.project.click.click.events.EventSchedule
import compose.project.click.click.events.eventSchedule
import compose.project.click.click.events.eventScheduleMetadata
import compose.project.click.click.events.mergeEventScheduleIntoRaw
import compose.project.click.click.events.isVisibleEventBeacon
import compose.project.click.click.events.validateEventSchedule
import compose.project.click.click.ui.components.mapBeaconKindToLayerFilter
import compose.project.click.click.ui.utils.mergeMapBeaconLists
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
import compose.project.click.click.utils.HUB_GATEKEEPER_HIGH_ACCURACY_TIMEOUT_MS
import compose.project.click.click.utils.LocationResult // pragma: allowlist secret
import compose.project.click.click.utils.LocationService // pragma: allowlist secret
import compose.project.click.click.utils.resolveHubGatekeeperLocation
import kotlinx.serialization.json.JsonObject // pragma: allowlist secret
import kotlinx.serialization.json.buildJsonObject // pragma: allowlist secret
import kotlinx.serialization.json.put // pragma: allowlist secret
import kotlinx.serialization.json.putJsonArray // pragma: allowlist secret
import kotlinx.serialization.json.add // pragma: allowlist secret
import compose.project.click.click.events.EVENT_CATEGORY_OPTIONS // pragma: allowlist secret
import compose.project.click.click.events.EVENT_CATEGORIES_METADATA_KEY // pragma: allowlist secret
import compose.project.click.click.utils.EVENT_FORMATTED_ADDRESS_METADATA_KEY
import compose.project.click.click.utils.EVENT_LOCATION_NAME_METADATA_KEY
import compose.project.click.click.utils.GeocodedPlace
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
import kotlinx.coroutines.sync.withLock
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
        /** `null` while proximity is still being resolved. */
        val canJoinGeofence: Boolean?,
    ) : MapSelection()
    /**
     * Two or more pins share nearly the same on-screen hit target. User picks which one to open.
     */
    data class OverlappingPinsSelected(val pins: List<MapPin>) : MapSelection()
}

data class BeaconRsvpCacheEntry(
    val attendees: List<BeaconAttendeeDto>,
    val currentUserSignedUp: Boolean,
)

data class BeaconDirectoryCacheEntry(
    val attendees: List<compose.project.click.click.events.DirectoryAttendee>,
    val currentUserSignedUp: Boolean,
    val currentUserCheckedIn: Boolean,
    val mutualsSectionUnlocked: Boolean,
)

data class BeaconEngagementCacheEntry(
    val bookmarked: Boolean = false,
    val checkedIn: Boolean = false,
    val checkedInAt: String? = null,
    val checkInCount: Int = 0,
    /**
     * Client-kept early check-in after HTTP 409 (event not live yet). Survives force-refresh
     * and process death until the server reports checkedIn or the user checks out.
     */
    val localEarlyCheckIn: Boolean = false,
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
        private const val DEFAULT_USER_MAP_ZOOM = 14.0
    }

    private var seededDeviceCameraThisSession = false

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

    private val _beaconDirectoryById = MutableStateFlow<Map<String, BeaconDirectoryCacheEntry>>(emptyMap())
    val beaconDirectoryById: StateFlow<Map<String, BeaconDirectoryCacheEntry>> = _beaconDirectoryById.asStateFlow()

    private val _beaconDirectoryLoadingIds = MutableStateFlow<Set<String>>(emptySet())
    val beaconDirectoryLoadingIds: StateFlow<Set<String>> = _beaconDirectoryLoadingIds.asStateFlow()

    /** Beacon ids with an in-flight GET `/api/beacons/{id}/rsvp`. */
    private val _beaconRsvpLoadingIds = MutableStateFlow<Set<String>>(emptySet())
    val beaconRsvpLoadingIds: StateFlow<Set<String>> = _beaconRsvpLoadingIds.asStateFlow()

    /** Beacon ids with an optimistic POST/DELETE awaiting server confirmation. */
    private val _beaconRsvpPendingIds = MutableStateFlow<Set<String>>(emptySet())
    val beaconRsvpPendingIds: StateFlow<Set<String>> = _beaconRsvpPendingIds.asStateFlow()

    private val _beaconEngagementById =
        MutableStateFlow<Map<String, BeaconEngagementCacheEntry>>(emptyMap())
    val beaconEngagementById: StateFlow<Map<String, BeaconEngagementCacheEntry>> =
        _beaconEngagementById.asStateFlow()

    private val _beaconEngagementPendingIds = MutableStateFlow<Set<String>>(emptySet())
    val beaconEngagementPendingIds: StateFlow<Set<String>> =
        _beaconEngagementPendingIds.asStateFlow()

    /**
     * Beacon ids the user early-checked-in (HTTP 409). Survives force-refresh races that can
     * briefly see checkedIn=true with localEarlyCheckIn=false before the 409 write lands.
     */
    private val earlyCheckInBeaconIds = mutableSetOf<String>()
    private val engagementPersistMutex = Mutex()
    private var engagementPersistGeneration = 0

    private val _engagementSnackbar = MutableStateFlow<String?>(null)
    val engagementSnackbar: StateFlow<String?> = _engagementSnackbar.asStateFlow()

    fun clearEngagementSnackbar() {
        _engagementSnackbar.value = null
    }

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
        combine(
            _discoveryFeedLoading,
            _discoveryProximityFetchCompleted,
            discoveryFeedBeacons,
            discoveryFeedHubs,
        ) { loading, completed, beacons, hubs ->
            Quadruple(loading, completed, beacons, hubs)
        },
        combine(
            AppDataManager.prefetchedMapBeacons,
            AppDataManager.prefetchedCommunityHubs,
        ) { prefetchedBeacons, prefetchedHubs ->
            prefetchedBeacons to prefetchedHubs
        },
    ) { base, prefetched ->
        val (loading, completed, beacons, hubs) = base
        val (prefetchedBeacons, prefetchedHubs) = prefetched
        val hasLocalFeedData = beacons.isNotEmpty() ||
            hubs.isNotEmpty() ||
            prefetchedBeacons.isNotEmpty() ||
            prefetchedHubs.isNotEmpty()
        loading || (!completed && !hasLocalFeedData)
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
    private val discoveryProximityRadiusMeters = 50_000.0

    private val maxDiscoveryPrefetchAttempts = 5

    private val locationService = LocationService()

    // Cluster threshold - zoom level above which individual pins are shown
    private val clusterThreshold = 12.0

    /**
     * Must zoom out past this before leaving individual-pin mode. Prevents cluster↔pin flicker
     * when native zoom readbacks chatter around [clusterThreshold] during pinch.
     */
    private val pinModeExitBelow = clusterThreshold - 0.75

    /**
     * After a cluster zoom-in, native map zoom readbacks often dip below [clusterThreshold] briefly
     * or stay inconsistent with [metersForZoom]. Until the user clearly zooms out past the
     * threshold, treat the map as "pin mode" for clustering decisions so [determineMapRenderData]
     * does not snap back to hub markers while the camera is still on the cluster.
     */
    private val _pinRenderZoomFloor = MutableStateFlow<Double?>(null)

    /** Sticky pin mode from user pinch (enter ≥ threshold, exit only below [pinModeExitBelow]). */
    private val _stickyPinMode = MutableStateFlow(false)

    private fun zoomForClusteringRender(zoom: Double): Double {
        val floor = _pinRenderZoomFloor.value
        if (floor != null) return maxOf(zoom, floor)
        if (_stickyPinMode.value && zoom >= pinModeExitBelow) {
            return maxOf(zoom, clusterThreshold)
        }
        return zoom
    }

    private fun updateStickyPinModeForZoom(zoom: Double) {
        when {
            zoom >= clusterThreshold -> _stickyPinMode.value = true
            zoom < pinModeExitBelow -> {
                _stickyPinMode.value = false
                _pinRenderZoomFloor.value = null
            }
        }
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
            hydrateBeaconEngagementFromDisk()
        }
        viewModelScope.launch {
            AppDataManager.currentUser.collect { user ->
                if (user != null) {
                    hydrateBeaconRsvpFromDisk(user.id)
                    hydrateBeaconEngagementFromDisk(user.id)
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
        viewModelScope.launch {
            AppDataManager.dismissedCommunityHubIds.collect { dismissed ->
                if (dismissed.isEmpty()) return@collect
                _communityHubs.update { hubs -> hubs.filterNot { it.hubId in dismissed } }
                val selected = _selection.value as? MapSelection.HubSelected ?: return@collect
                if (selected.hub.hubId in dismissed) {
                    _selection.value = MapSelection.None
                }
            }
        }
    }

    /** Starts (or retries) discovery hub/beacon loading when map is opened. */
    fun warmDiscoveryFeed() {
        if (AppDataManager.currentUser.value == null) return
        AppDataManager.requestMapDiscoveryPrefetch()
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

    private suspend fun hydrateBeaconEngagementFromDisk(userId: String? = null) {
        val uid = userId?.trim()?.takeIf { it.isNotEmpty() }
            ?: SupabaseConfig.client.auth.currentUserOrNull()?.id?.trim()?.takeIf { it.isNotEmpty() }
            ?: AppDataManager.currentUser.value?.id?.trim()?.takeIf { it.isNotEmpty() }
            ?: return
        val restored = BeaconEngagementPersistence.load(tokenStorage, uid)
        if (restored.isEmpty()) return
        restored.forEach { (id, entry) ->
            if (entry.localEarlyCheckIn) {
                earlyCheckInBeaconIds += id
            }
        }
        _beaconEngagementById.update { current ->
            // Prefer disk early/checked-in over a stale in-memory "not checked in" from a racing fetch.
            current + restored.mapValues { (id, disk) ->
                val mem = current[id]
                if (disk.localEarlyCheckIn || disk.checkedIn) {
                    disk
                } else if (mem?.localEarlyCheckIn == true || id in earlyCheckInBeaconIds) {
                    mem?.copy(checkedIn = true, localEarlyCheckIn = true) ?: disk.copy(
                        checkedIn = true,
                        localEarlyCheckIn = true,
                    )
                } else {
                    disk
                }
            }
        }
    }

    private fun persistBeaconEngagementCache() {
        val generation = ++engagementPersistGeneration
        viewModelScope.launch {
            engagementPersistMutex.withLock {
                if (generation != engagementPersistGeneration) return@withLock
                val uid = SupabaseConfig.client.auth.currentUserOrNull()?.id?.trim()?.takeIf { it.isNotEmpty() }
                    ?: AppDataManager.currentUser.value?.id?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@withLock
                BeaconEngagementPersistence.save(tokenStorage, uid, _beaconEngagementById.value)
            }
        }
    }

    private fun updateBeaconEngagementCache(
        persistDisk: Boolean = true,
        transform: (Map<String, BeaconEngagementCacheEntry>) -> Map<String, BeaconEngagementCacheEntry>,
    ) {
        _beaconEngagementById.update(transform)
        if (persistDisk) persistBeaconEngagementCache()
    }

    private fun mergeEngagementFromServer(
        existing: BeaconEngagementCacheEntry?,
        beaconId: String,
        bookmarked: Boolean,
        checkedIn: Boolean,
        checkedInAt: String?,
        checkInCount: Int,
        preferServer: Boolean = false,
    ): BeaconEngagementCacheEntry {
        // On force refresh, trust the server so a device-local early check-in (or poisoned
        // far-away optimistic state) cannot override a real server row across kills/devices.
        val keepEarly = !preferServer && !checkedIn && (
            existing?.localEarlyCheckIn == true ||
                beaconId in earlyCheckInBeaconIds
            )
        if (preferServer && !checkedIn) {
            earlyCheckInBeaconIds -= beaconId
        }
        return BeaconEngagementCacheEntry(
            bookmarked = bookmarked,
            checkedIn = checkedIn || keepEarly,
            checkedInAt = checkedInAt ?: existing?.checkedInAt,
            checkInCount = checkInCount,
            localEarlyCheckIn = keepEarly,
        )
    }

    private fun engagementTelemetry(
        latitude: Double? = null,
        longitude: Double? = null,
        surface: String? = null,
        bookmarked: Boolean? = null,
    ): EngagementTelemetryBody {
        val platform = getPlatform().name.lowercase().let { name ->
            when {
                name.contains("android") -> "android"
                name.contains("ios") || name.contains("iphone") -> "ios"
                else -> name.take(32)
            }
        }
        return EngagementTelemetryBody(
            latitude = latitude,
            longitude = longitude,
            source = "mobile",
            platform = platform,
            surface = surface,
            bookmarked = bookmarked,
        )
    }

    /**
     * Hydrate map state immediately from [AppDataManager]'s eager beacon/hub prefetch so the map is
     * already populated on first render (prefetch runs in parallel with connections at app load).
     */
    private fun seedFromAppDataPrefetch() {
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
    private fun seedEventPinsFromCachedBookmarks(
        bookmarks: List<compose.project.click.click.data.api.EventBookmarkItemDto>,
    ) {
        if (bookmarks.isEmpty()) return
        _mapBeacons.update { current ->
            val byId = current.associateBy { it.id }
            val seeds = bookmarks.mapNotNull { bookmark ->
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
                val scheduleRaw = buildJsonObject {
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

    private fun markDiscoveryProximityFetchCompleted() {
        _discoveryProximityFetchCompleted.value = true
    }

    private fun applyPrefetchedBeacons(list: List<MapBeacon>) {
        // Always run through merge so in-memory null-island pins from a prior bad GET are purged
        // even when the incoming prefetch list is empty after cache heal.
        _mapBeacons.update { current -> mergeMapBeaconLists(current, list) }
        if (list.isEmpty()) return
        AppDataManager.mergeCachedMapBeacons(list)
        markDiscoveryProximityFetchCompleted()
        hydrateEventEngagementFromServer()
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
                reportedDistanceMeters = dto.distanceMeters,
            )
        }
        _communityHubs.update { current -> mergeCommunityHubLists(current, filterDismissedCommunityHubs(incoming)) }
        AppDataManager.mergeCachedCommunityHubsFromDto(rows)
        markDiscoveryProximityFetchCompleted()
    }

    private fun filterDismissedCommunityHubs(hubs: List<CommunityHubPin>): List<CommunityHubPin> {
        val dismissed = AppDataManager.dismissedCommunityHubIds.value
        if (dismissed.isEmpty()) return hubs
        return hubs.filterNot { it.hubId in dismissed }
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
                        // Collapse duplicate 1:1 edges so the same peer is not drawn twice.
                        val viewerId = AppDataManager.currentUser.value?.id
                        val mapConnections = collapseOneToOneConnectionsByPeer(
                            connections.filter { it.id !in hiddenIds },
                            viewerId,
                        )
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
                    connections.isNotEmpty() -> {
                        val viewerId = AppDataManager.currentUser.value?.id
                        val mapConnections = collapseOneToOneConnectionsByPeer(
                            connections.filter { it.id !in hiddenIds },
                            viewerId,
                        )
                        _mapState.value = MapState.Success(mapConnections)
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
     * Optional `filters` query for `/api/beacons`.
     *
     * Always null: fetch all beacon kinds for the radius and filter by layer on-device.
     * Server-side type filters caused soundtracks/alerts to vanish when a prior Events-only
     * preset (or partial chip set) drove the fetch — toggling Soundtracks back on could not
     * recover pins already outside the last RPC result set.
     */
    private fun beaconTypesQueryForLayers(@Suppress("UNUSED_PARAMETER") layers: Set<MapLayerFilter>): String? {
        return null
    }

    private fun filterBeaconsForLayers(
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
     * Computes a one-time default camera. Prefers the user's GPS fix; falls back to connection bounds.
     */
    private fun ensureDefaultCameraTarget(connections: List<Connection>) {
        if (_defaultCameraTarget.value != null) return

        deviceLocationCameraTarget()?.let { userTarget ->
            _defaultCameraTarget.value = userTarget
            if (_cameraTarget.value == null) {
                _cameraTarget.value = userTarget
            }
            if (_zoomLevel.value == 10.0) {
                _zoomLevel.value = userTarget.zoom
            }
            prefetchDiscoveryProximityData(showPulse = false, markInitialComplete = false)
            return
        }

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

    /**
     * Applies the first GPS fix of a map session so PiP preview and the expanded map frame the user.
     */
    fun updateMapDeviceLocation(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || !longitude.isFinite()) return
        if (latitude == 0.0 && longitude == 0.0) return
        AppDataManager.noteDeviceLocation(latitude, longitude)
        if (AppDataManager.ghostModeEnabled.value) return
        if (!locationService.hasLocationPermission()) return

        val target = CameraTarget(
            latitude = latitude,
            longitude = longitude,
            zoom = DEFAULT_USER_MAP_ZOOM,
        )

        if (_defaultCameraTarget.value == null) {
            _defaultCameraTarget.value = target
        }

        if (!seededDeviceCameraThisSession) {
            seededDeviceCameraThisSession = true
            // Do not clobber an active programmatic target (Home Featured Event / search → beacon).
            if (_cameraTarget.value == null && pendingProgrammaticZoomTarget == null) {
                _cameraTarget.value = target
                if (_zoomLevel.value <= 10.01) {
                    _zoomLevel.value = DEFAULT_USER_MAP_ZOOM
                }
            }
        }
    }

    private fun deviceLocationCameraTarget(): CameraTarget? {
        if (!locationService.hasLocationPermission()) return null
        val loc = AppDataManager.lastKnownDeviceLocation.value ?: return null
        return CameraTarget(
            latitude = loc.first,
            longitude = loc.second,
            zoom = DEFAULT_USER_MAP_ZOOM,
        )
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

    /** Home explore tile: focus map on a single layer preset (not a toggle). */
    fun applyHomeLayerPreset(filter: MapLayerFilter) {
        _pinRenderZoomFloor.value = null
        _selectedLayerFilters.value = when (filter) {
            MapLayerFilter.ALL -> defaultMapLayerFilters()
            else -> setOf(filter)
        }
        _visibleBounds.value?.let { scheduleBeaconFetchForBounds(it, debounceMs = 0L) }
        prefetchDiscoveryProximityData(showPulse = false, markInitialComplete = false)
    }

    fun clearBeaconInsertError() {
        _beaconInsertError.value = null
    }

    fun clearBeaconDropFailureToast() {
        _beaconDropFailureToast.value = null
    }

    fun onBeaconPinTapped(beaconId: String, seedDistanceMeters: Double? = null) {
        val beacon = _mapBeacons.value.firstOrNull { it.id == beaconId }
            ?: return
        val quickDistance = resolveBeaconQuickDistanceMeters(
            seedDistanceMeters = seedDistanceMeters,
            beaconLat = beacon.latitude,
            beaconLon = beacon.longitude,
            cachedUserLatLon = AppDataManager.lastKnownDeviceLocation.value,
        )
        _selection.value = MapSelection.BeaconSelected(beacon, distanceMeters = quickDistance)

        viewModelScope.launch(Dispatchers.Default) {
            val loc = resolveFastMapLocation() ?: return@launch
            val distance = haversineDistance(loc.latitude, loc.longitude, beacon.latitude, beacon.longitude)
            val current = _selection.value as? MapSelection.BeaconSelected ?: return@launch
            if (current.beacon.id == beaconId) {
                _selection.value = current.copy(distanceMeters = distance)
            }
        }
    }

    /** Beacon ids that already received a successful detail GET this session. */
    private val eventDetailHydratedIds = mutableSetOf<String>()
    private val soundtrackDetailHydratedIds = mutableSetOf<String>()

    /**
     * Detail-sheet only: fetch the full beacon when schedule, Posted (`created_at`), or creator
     * attribution is missing — and at least once per id when the sheet opens, so bookmark /
     * proximity stubs that already have a schedule still pick up Host + Posted.
     *
     * [seed] is required when opening from Home with a synthetic/bookmark beacon that is not yet
     * in [_mapBeacons] or [MapSelection].
     */
    fun ensureEventBeaconDetail(beaconId: String, seed: MapBeacon? = null) {
        val id = beaconId.trim()
        if (id.isEmpty()) return
        val current = _mapBeacons.value.firstOrNull { it.id == id }
            ?: (_selection.value as? MapSelection.BeaconSelected)?.beacon?.takeIf { it.id == id }
            ?: seed?.takeIf { it.id == id && it.kind == MapBeaconKind.EVENT }
            ?: return
        if (current.kind != MapBeaconKind.EVENT) return
        val needsSchedule = current.eventSchedule() == null
        val needsPosted = current.createdAtEpochMs == null
        val needsCreator = current.createdByUserId.isNullOrBlank()
        val needsHostName = current.creatorDisplayName.isNullOrBlank()
        val alreadyHydrated = id in eventDetailHydratedIds
        if (alreadyHydrated && !needsSchedule && !needsPosted && !needsCreator && !needsHostName) {
            return
        }
        // First open always hits the network once so Host / Posted can't stay blank forever.
        if (alreadyHydrated && !needsSchedule && !needsPosted && !needsCreator) {
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            hydrateBeaconDetailFromNetwork(id, current)
        }
    }

    /**
     * Hydrates soundtrack preview/art/URLs for the detail sheet. Disk cache historically stripped
     * these fields, which left play controls missing until a fresh proximity fetch.
     */
    fun ensureSoundtrackBeaconDetail(beaconId: String, seed: MapBeacon? = null) {
        val id = beaconId.trim()
        if (id.isEmpty()) return
        val current = _mapBeacons.value.firstOrNull { it.id == id }
            ?: (_selection.value as? MapSelection.BeaconSelected)?.beacon?.takeIf { it.id == id }
            ?: seed?.takeIf { it.id == id && it.kind == MapBeaconKind.SOUNDTRACK }
            ?: return
        if (current.kind != MapBeaconKind.SOUNDTRACK) return
        val needsPreview = current.metadata.previewUrl.isNullOrBlank()
        val needsArt = current.metadata.albumArtUrl.isNullOrBlank()
        val needsTrack = current.metadata.trackName.isNullOrBlank() && current.metadata.title.isNullOrBlank()
        val alreadyHydrated = id in soundtrackDetailHydratedIds
        if (alreadyHydrated && !needsPreview && !needsArt && !needsTrack) return
        viewModelScope.launch(Dispatchers.Default) {
            hydrateBeaconDetailFromNetwork(id, current)
        }
    }

    private suspend fun hydrateBeaconDetailFromNetwork(id: String, current: MapBeacon) {
        mapBeaconRepository.fetchBeacon(id).fold(
            onSuccess = { full ->
                fun MapBeacon.withHydratedDetail(): MapBeacon {
                    val schedule = eventSchedule() ?: full.eventSchedule()
                    val keepCoords = hasUsableMapCoordinates()
                    fun String?.orHydrated(other: String?): String? =
                        this?.takeIf { it.isNotBlank() } ?: other?.takeIf { it.isNotBlank() }
                    return copy(
                        latitude = if (keepCoords) latitude else full.latitude,
                        longitude = if (keepCoords) longitude else full.longitude,
                        metadata = metadata.copy(
                            title = metadata.title.orHydrated(full.metadata.title),
                            description = metadata.description.orHydrated(full.metadata.description),
                            trackName = metadata.trackName.orHydrated(full.metadata.trackName),
                            artistName = metadata.artistName.orHydrated(full.metadata.artistName),
                            artist = metadata.artist.orHydrated(full.metadata.artist),
                            previewUrl = metadata.previewUrl.orHydrated(full.metadata.previewUrl),
                            albumArtUrl = metadata.albumArtUrl.orHydrated(full.metadata.albumArtUrl),
                            musicUrl = metadata.musicUrl.orHydrated(full.metadata.musicUrl),
                            originalUrl = metadata.originalUrl.orHydrated(full.metadata.originalUrl),
                            eventCategories = metadata.eventCategories.ifEmpty {
                                full.metadata.eventCategories
                            },
                            raw = if (schedule != null) {
                                mergeEventScheduleIntoRaw(metadata.raw, schedule)
                            } else {
                                full.metadata.raw ?: metadata.raw
                            },
                        ),
                        createdByUserId = createdByUserId ?: full.createdByUserId,
                        createdAtEpochMs = createdAtEpochMs ?: full.createdAtEpochMs,
                        expiresAtEpochMs = expiresAtEpochMs ?: full.expiresAtEpochMs,
                        showCreatorName = showCreatorName || full.showCreatorName,
                        creatorDisplayName = creatorDisplayName.orHydrated(full.creatorDisplayName),
                        sourceBeaconType = sourceBeaconType ?: full.sourceBeaconType,
                    )
                }
                _mapBeacons.update { list ->
                    var found = false
                    val mapped = list.map { b ->
                        if (b.id == id) {
                            found = true
                            b.withHydratedDetail()
                        } else {
                            b
                        }
                    }
                    if (found) {
                        mapped
                    } else {
                        mergeMapBeaconLists(list, listOf(current.withHydratedDetail()))
                    }
                }
                val patched = _mapBeacons.value.firstOrNull { it.id == id }
                    ?: current.withHydratedDetail()
                when (patched.kind) {
                    MapBeaconKind.EVENT -> eventDetailHydratedIds += id
                    MapBeaconKind.SOUNDTRACK -> soundtrackDetailHydratedIds += id
                    else -> Unit
                }
                AppDataManager.mergeCachedMapBeacons(listOf(patched))
                val sel = _selection.value as? MapSelection.BeaconSelected
                if (sel != null && sel.beacon.id == id) {
                    _selection.value = sel.copy(beacon = patched)
                }
            },
            onFailure = { /* keep sheet open with whatever we have */ },
        )
    }

    /** @deprecated Use [ensureEventBeaconDetail]. */
    fun ensureEventBeaconSchedule(beaconId: String) = ensureEventBeaconDetail(beaconId)

    /**
     * Pan the camera to [beaconId] and open its detail sheet (Home Featured Event / deep link).
     * Ensures the matching layer for this beacon kind is visible so the pin isn't filtered out.
     */
    fun focusBeaconOnMap(beaconId: String, seedDistanceMeters: Double? = null) {
        val id = beaconId.trim()
        if (id.isEmpty()) return
        val beacon = _mapBeacons.value.firstOrNull { it.id == id }
            ?: EventReminderCoordinator.beaconById(id)
            ?: return
        if (_mapBeacons.value.none { it.id == id }) {
            _mapBeacons.update { current ->
                if (current.any { it.id == id }) current else current + beacon
            }
        }
        val neededLayer = mapBeaconKindToLayerFilter(beacon.kind)
        _selectedLayerFilters.update { filters ->
            if (MapLayerFilter.ALL in filters || neededLayer in filters) {
                filters
            } else {
                filters + neededLayer
            }
        }
        val zoom = 15.0
        val target = CameraTarget(
            latitude = beacon.latitude,
            longitude = beacon.longitude,
            zoom = zoom,
        )
        // Mark device-seed done so a late GPS fix cannot overwrite this focus.
        seededDeviceCameraThisSession = true
        _cameraTarget.value = target
        lastKnownCameraTarget = target
        pendingProgrammaticZoomTarget = zoom
        pendingProgrammaticZoomSetAtMs = Clock.System.now().toEpochMilliseconds()
        _zoomLevel.value = zoom
        updateStickyPinModeForZoom(zoom)
        onBeaconPinTapped(id, seedDistanceMeters)
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

    /**
     * Loads enriched people directory (interests, FoF mutuals, RSVP distance).
     * Requires viewer RSVP or check-in (403 otherwise — treated as empty/locked).
     */
    fun loadBeaconAttendeeDirectory(beaconId: String, forceRefresh: Boolean = false) {
        val id = beaconId.trim()
        if (id.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            if (!ensureClickWebAuthReady()) return@launch
            if (!forceRefresh && _beaconDirectoryById.value.containsKey(id)) return@launch
            if (id in _beaconDirectoryLoadingIds.value) return@launch

            _beaconDirectoryLoadingIds.update { it + id }
            try {
                mapBeaconRepository.fetchBeaconAttendeeDirectory(id).fold(
                    onSuccess = { payload ->
                        val mapped = payload.attendees.map { dto ->
                            compose.project.click.click.events.DirectoryAttendee(
                                userId = dto.userId,
                                name = dto.name,
                                avatarUrl = dto.avatarUrl,
                                signedUpAt = dto.signedUpAt,
                                distanceMeters = dto.distanceMeters,
                                sharedInterests = dto.sharedInterests,
                                sharedInterestCount = dto.sharedInterestCount,
                                relationship = compose.project.click.click.events.AttendeeRelationship.fromApi(dto.relationship),
                                mutualVia = dto.mutualVia.map {
                                    compose.project.click.click.events.MutualViaPeer(it.userId, it.name)
                                },
                                mutualConnectionCount = dto.mutualConnectionCount,
                            )
                        }
                        _beaconDirectoryById.update { current ->
                            current + (id to BeaconDirectoryCacheEntry(
                                attendees = mapped,
                                currentUserSignedUp = payload.currentUserSignedUp,
                                currentUserCheckedIn = payload.currentUserCheckedIn,
                                mutualsSectionUnlocked = payload.mutualsSectionUnlocked,
                            ))
                        }
                    },
                    onFailure = {
                        // Keep prior cache; directory may be locked until RSVP/check-in.
                    },
                )
            } finally {
                _beaconDirectoryLoadingIds.update { it - id }
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

    fun hasLocationPermission(): Boolean = locationService.hasLocationPermission()

    /** Exposed for BeaconDropSheet “Use my location”. */
    suspend fun resolveDropLocationForUi(): LocationResult? = resolveBeaconDropLocation()

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
                    loadBeaconAttendeeDirectory(id, forceRefresh = true)
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

    fun loadBeaconEngagement(beaconId: String, forceRefresh: Boolean = false) {
        val id = beaconId.trim()
        if (id.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            if (!ensureClickWebAuthReady()) return@launch
            if (!forceRefresh && _beaconEngagementById.value.containsKey(id)) return@launch
            if (id in _beaconEngagementPendingIds.value) return@launch
            mapBeaconRepository.fetchBeaconEngagement(id).fold(
                onSuccess = { payload ->
                    if (id in _beaconEngagementPendingIds.value) return@fold
                    updateBeaconEngagementCache { current ->
                        val existing = current[id]
                        current + (id to mergeEngagementFromServer(
                            existing = existing,
                            beaconId = id,
                            bookmarked = payload.bookmarked,
                            checkedIn = payload.checkedIn,
                            checkedInAt = payload.checkedInAt,
                            checkInCount = payload.checkInCount,
                            preferServer = forceRefresh,
                        ))
                    }
                },
                onFailure = { /* keep disk cache */ },
            )
        }
    }

    /** Pull server engagement for visible event pins so check-ins sync across devices after cold start. */
    fun hydrateEventEngagementFromServer() {
        viewModelScope.launch(Dispatchers.Default) {
            if (!ensureClickWebAuthReady()) return@launch
            val eventIds = _mapBeacons.value
                .asSequence()
                .filter { it.kind == MapBeaconKind.EVENT }
                .map { it.id }
                .distinct()
                .take(40)
                .toList()
            for (id in eventIds) {
                if (id in _beaconEngagementPendingIds.value) continue
                mapBeaconRepository.fetchBeaconEngagement(id).fold(
                    onSuccess = { payload ->
                        if (id in _beaconEngagementPendingIds.value) return@fold
                        updateBeaconEngagementCache { current ->
                            current + (id to mergeEngagementFromServer(
                                existing = current[id],
                                beaconId = id,
                                bookmarked = payload.bookmarked,
                                checkedIn = payload.checkedIn,
                                checkedInAt = payload.checkedInAt,
                                checkInCount = payload.checkInCount,
                                preferServer = true,
                            ))
                        }
                    },
                    onFailure = { /* ignore per-beacon */ },
                )
            }
        }
    }

    fun recordEventImpression(beaconId: String) {
        val id = beaconId.trim()
        if (id.isEmpty()) return
        viewModelScope.launch {
            if (!ensureClickWebAuthReady()) return@launch
            mapBeaconRepository.recordBeaconImpression(
                id,
                engagementTelemetry(surface = "detail"),
            )
        }
    }

    fun recordEventShare(beaconId: String, shareUrl: String? = null) {
        val id = beaconId.trim()
        if (id.isEmpty()) return
        viewModelScope.launch {
            if (!ensureClickWebAuthReady()) return@launch
            mapBeaconRepository.recordBeaconShare(
                id,
                engagementTelemetry(surface = "detail"),
                shareUrl = shareUrl,
            )
        }
    }

    fun toggleBeaconBookmark(beaconId: String) {
        val id = beaconId.trim()
        if (id.isEmpty() || id in _beaconEngagementPendingIds.value) return
        val previous = _beaconEngagementById.value[id]
        val nextBookmarked = !(previous?.bookmarked ?: false)
        _beaconEngagementPendingIds.update { it + id }
        updateBeaconEngagementCache { current ->
            val base = current[id] ?: BeaconEngagementCacheEntry()
            current + (id to base.copy(bookmarked = nextBookmarked))
        }
        // Optimistic Home "Saved events" update — do not wait for network or app restart.
        syncCachedEventBookmarksAfterToggle(id, nextBookmarked)
        PlatformHapticsPolicy.successNotification()
        viewModelScope.launch {
            if (!ensureClickWebAuthReady()) {
                restoreEngagementSnapshot(id, previous)
                syncCachedEventBookmarksAfterToggle(id, previous?.bookmarked == true)
                _beaconEngagementPendingIds.update { it - id }
                return@launch
            }
            mapBeaconRepository.setBeaconBookmark(
                id,
                nextBookmarked,
                engagementTelemetry(bookmarked = nextBookmarked),
            ).fold(
                onSuccess = {
                    _beaconEngagementPendingIds.update { it - id }
                },
                onFailure = {
                    restoreEngagementSnapshot(id, previous)
                    syncCachedEventBookmarksAfterToggle(id, previous?.bookmarked == true)
                    _beaconEngagementPendingIds.update { it - id }
                    _engagementSnackbar.value = "Couldn't update bookmark"
                },
            )
        }
    }

    private fun syncCachedEventBookmarksAfterToggle(beaconId: String, bookmarked: Boolean) {
        viewModelScope.launch(Dispatchers.Default) {
            if (bookmarked) {
                val beacon = _mapBeacons.value.firstOrNull { it.id == beaconId }
                val schedule = beacon?.eventSchedule()
                val item = compose.project.click.click.data.api.EventBookmarkItemDto(
                    beaconId = beaconId,
                    bookmarkedAt = Clock.System.now().toString(),
                    title = beacon?.displayDynamicTitle(),
                    eventStartAt = schedule?.let {
                        kotlinx.datetime.Instant.fromEpochMilliseconds(it.startEpochMs).toString()
                    },
                    eventEndAt = schedule?.let {
                        kotlinx.datetime.Instant.fromEpochMilliseconds(it.endEpochMs).toString()
                    },
                    latitude = beacon?.latitude,
                    longitude = beacon?.longitude,
                    expiresAt = beacon?.expiresAtEpochMs?.let {
                        kotlinx.datetime.Instant.fromEpochMilliseconds(it).toString()
                    },
                )
                val merged = (listOf(item) + AppDataManager.cachedEventBookmarks.value.filterNot { it.beaconId == beaconId })
                    .distinctBy { it.beaconId }
                AppDataManager.updateCachedEventBookmarks(merged)
            } else {
                AppDataManager.updateCachedEventBookmarks(
                    AppDataManager.cachedEventBookmarks.value.filterNot { it.beaconId == beaconId },
                )
            }
            // Reconcile with server list so titles/schedule stay accurate.
            mapBeaconRepository.fetchMyEventBookmarks().onSuccess { remote ->
                AppDataManager.updateCachedEventBookmarks(remote.bookmarks)
            }
        }
    }

    fun toggleBeaconCheckIn(beaconId: String) {
        val id = beaconId.trim()
        if (id.isEmpty() || id in _beaconEngagementPendingIds.value) return
        val previous = _beaconEngagementById.value[id]
        val currentlyCheckedIn = previous?.checkedIn == true
        if (currentlyCheckedIn) {
            earlyCheckInBeaconIds -= id
            _beaconEngagementPendingIds.update { it + id }
            updateBeaconEngagementCache { current ->
                val base = current[id] ?: BeaconEngagementCacheEntry()
                current + (id to base.copy(checkedIn = false, checkedInAt = null, localEarlyCheckIn = false))
            }
            PlatformHapticsPolicy.successNotification()
            viewModelScope.launch {
                if (!ensureClickWebAuthReady()) {
                    if (previous?.localEarlyCheckIn == true) earlyCheckInBeaconIds += id
                    restoreEngagementSnapshot(id, previous)
                    _beaconEngagementPendingIds.update { it - id }
                    return@launch
                }
                mapBeaconRepository.checkOutBeacon(id).fold(
                    onSuccess = { _beaconEngagementPendingIds.update { it - id } },
                    onFailure = {
                        if (previous?.localEarlyCheckIn == true) earlyCheckInBeaconIds += id
                        restoreEngagementSnapshot(id, previous)
                        _beaconEngagementPendingIds.update { it - id }
                        _engagementSnackbar.value = "Couldn't undo check-in"
                    },
                )
            }
            return
        }

        // Optimistic UI only — do not persist until the server confirms (or in-geofence early 409).
        // Persisting mid-flight caused "checked in" to survive app kill after a 403 far-away reject.
        _beaconEngagementPendingIds.update { it + id }
        updateBeaconEngagementCache(persistDisk = false) { current ->
            val base = current[id] ?: BeaconEngagementCacheEntry()
            current + (id to base.copy(checkedIn = true, localEarlyCheckIn = false))
        }
        PlatformHapticsPolicy.successNotification()
        viewModelScope.launch {
            if (!locationService.hasLocationPermission()) {
                restoreEngagementSnapshot(id, previous)
                _beaconEngagementPendingIds.update { it - id }
                _engagementSnackbar.value = "Location access is required to check in"
                return@launch
            }
            val loc = resolveBeaconDropLocation()
            if (loc == null ||
                !loc.latitude.isFinite() ||
                !loc.longitude.isFinite() ||
                (loc.latitude == 0.0 && loc.longitude == 0.0)
            ) {
                restoreEngagementSnapshot(id, previous)
                _beaconEngagementPendingIds.update { it - id }
                _engagementSnackbar.value = "Location required to check in"
                return@launch
            }
            val beacon = _mapBeacons.value.firstOrNull { it.id == id }
                ?: (_selection.value as? MapSelection.BeaconSelected)?.beacon?.takeIf { it.id == id }
            if (beacon != null) {
                val radiusM = beacon.resolveEventCheckInRadiusMeters()
                val distanceM = haversineDistance(
                    loc.latitude,
                    loc.longitude,
                    beacon.latitude,
                    beacon.longitude,
                )
                if (distanceM > radiusM) {
                    restoreEngagementSnapshot(id, previous)
                    _beaconEngagementPendingIds.update { it - id }
                    _engagementSnackbar.value = "You're too far to check in"
                    return@launch
                }
            }
            if (!ensureClickWebAuthReady()) {
                restoreEngagementSnapshot(id, previous)
                _beaconEngagementPendingIds.update { it - id }
                _engagementSnackbar.value = "Couldn't check in — try again"
                return@launch
            }
            mapBeaconRepository.checkInBeacon(
                id,
                engagementTelemetry(latitude = loc.latitude, longitude = loc.longitude),
            ).fold(
                onSuccess = { payload ->
                    if (payload.checkedIn) {
                        earlyCheckInBeaconIds -= id
                    }
                    updateBeaconEngagementCache { current ->
                        current + (id to BeaconEngagementCacheEntry(
                            bookmarked = current[id]?.bookmarked ?: false,
                            checkedIn = payload.checkedIn,
                            checkedInAt = payload.checkedInAt,
                            checkInCount = payload.checkInCount,
                            localEarlyCheckIn = false,
                        ))
                    }
                    _beaconEngagementPendingIds.update { it - id }
                    if (payload.checkedIn) {
                        loadBeaconAttendeeDirectory(id, forceRefresh = true)
                    }
                    _engagementSnackbar.value = if (payload.checkedIn) {
                        "Checked in"
                    } else {
                        "Checked out"
                    }
                },
                onFailure = { err ->
                    val http = err as? BeaconEngagementHttpException
                    // Early check-in (409) is only valid when already inside the geofence —
                    // server now enforces this; still refuse to persist remote false positives.
                    if (http?.status == 409 && beacon != null) {
                        val radiusM = beacon.resolveEventCheckInRadiusMeters()
                        val distanceM = haversineDistance(
                            loc.latitude,
                            loc.longitude,
                            beacon.latitude,
                            beacon.longitude,
                        )
                        if (distanceM <= radiusM) {
                            earlyCheckInBeaconIds += id
                            updateBeaconEngagementCache { current ->
                                val base = current[id] ?: BeaconEngagementCacheEntry()
                                current + (id to base.copy(
                                    checkedIn = true,
                                    localEarlyCheckIn = true,
                                ))
                            }
                            _beaconEngagementPendingIds.update { it - id }
                            _engagementSnackbar.value = "Checked in early — see you at the event"
                            return@fold
                        }
                    }
                    restoreEngagementSnapshot(id, previous)
                    _beaconEngagementPendingIds.update { it - id }
                    _engagementSnackbar.value = beaconCheckInFailureMessage(
                        httpStatus = http?.status,
                        fallback = http?.message,
                    )
                },
            )
        }
    }

    private fun restoreEngagementSnapshot(beaconId: String, previous: BeaconEngagementCacheEntry?) {
        updateBeaconEngagementCache { current ->
            if (previous == null) current - beaconId
            else current + (beaconId to previous)
        }
    }

    fun deleteOwnedBeacon(beaconId: String, onFinished: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            mapBeaconRepository.deleteBeacon(beaconId).fold(
                onSuccess = {
                    _mapBeacons.update { list -> list.filterNot { it.id == beaconId } }
                    updateBeaconRsvpCache { it - beaconId }
                    updateBeaconEngagementCache { it - beaconId }
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
                    _mapBeacons.update { list -> mergeMapBeaconLists(list, listOf(updated)) }
                    val merged = _mapBeacons.value.firstOrNull { it.id == beaconId } ?: updated
                    val sel = _selection.value
                    if (sel is MapSelection.BeaconSelected && sel.beacon.id == beaconId) {
                        _selection.value = sel.copy(beacon = merged)
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
        eventCategories: List<String> = emptyList(),
        venueScale: EventVenueScale = EventVenueScale.DEFAULT,
        eventLocation: GeocodedPlace? = null,
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
            val useProvidedEventLocation =
                kind == MapBeaconKind.EVENT &&
                    eventLocation != null &&
                    eventLocation.latitude.isFinite() &&
                    eventLocation.longitude.isFinite()
            if (!useProvidedEventLocation && !locationService.hasLocationPermission()) {
                _beaconInsertError.value =
                    "Location is required to drop a community beacon. Enable location in Settings and try again."
                onRejectedEarly()
                onRemoteFinished(false)
                return@launch
            }
            val locationDeferred = if (useProvidedEventLocation) {
                null
            } else {
                async(Dispatchers.Default) { resolveBeaconDropLocation() }
            }
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
                    if (!useProvidedEventLocation) {
                        _beaconInsertError.value =
                            "Set an event location (search an address or use my location)."
                        onRejectedEarly()
                        onRemoteFinished(false)
                        return@launch
                    }
                    buildJsonObject {
                        put("title", trimmedTitle)
                        trimmedDescription?.let { put("description", it) }
                        eventScheduleMetadata(schedule).forEach { (k, v) -> put(k, v) }
                        val categories = eventCategories
                            .map { it.trim() }
                            .filter { it.isNotEmpty() && it in EVENT_CATEGORY_OPTIONS }
                            .distinct()
                        if (categories.isNotEmpty()) {
                            putJsonArray(EVENT_CATEGORIES_METADATA_KEY) {
                                categories.forEach { add(it) }
                            }
                        }
                        put(EVENT_VENUE_SCALE_METADATA_KEY, venueScale.apiValue)
                        put(EVENT_CHECK_IN_RADIUS_METADATA_KEY, venueScale.radiusMeters)
                        val locationName = eventLocation!!.shortLabel.trim().ifEmpty {
                            eventLocation.displayName.trim()
                        }
                        if (locationName.isNotEmpty()) {
                            put(EVENT_LOCATION_NAME_METADATA_KEY, locationName)
                        }
                        val formatted = eventLocation.displayName.trim()
                        if (formatted.isNotEmpty()) {
                            put(EVENT_FORMATTED_ADDRESS_METADATA_KEY, formatted)
                        }
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
            val locLat: Double
            val locLon: Double
            if (useProvidedEventLocation) {
                locLat = eventLocation!!.latitude
                locLon = eventLocation.longitude
            } else {
                val loc = locationDeferred!!.await()
                    ?: run {
                        _beaconInsertError.value =
                            "Could not read GPS. Enable location and try again."
                        onRejectedEarly()
                        onRemoteFinished(false)
                        return@launch
                    }
                locLat = loc.latitude
                locLon = loc.longitude
            }
            val squadSession = CollaborationSessionManager.activeMapDropSession()
            val eventExpiresIso = eventSchedule?.endEpochMs?.let {
                kotlinx.datetime.Instant.fromEpochMilliseconds(it).toString()
            }
            val insert = MapBeaconInsert(
                kind = kind.apiValue,
                lat = locLat,
                lon = locLon,
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
                latitude = locLat,
                longitude = locLon,
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
                    val confirmed = if (
                        serverBeacon.latitude.isFinite() &&
                        serverBeacon.longitude.isFinite() &&
                        !(serverBeacon.latitude == 0.0 && serverBeacon.longitude == 0.0)
                    ) {
                        serverBeacon
                    } else {
                        // Insert response can lack parseable PostGIS location — keep drop coords.
                        serverBeacon.copy(latitude = locLat, longitude = locLon)
                    }
                    _mapBeacons.update { current ->
                        mergeMapBeaconLists(
                            current.filter { it.id != optimisticId },
                            listOf(confirmed),
                        )
                    }
                    EventReminderCoordinator.rememberBeacon(confirmed)
                    refreshBeaconsAfterDrop(
                        latitude = locLat,
                        longitude = locLon,
                        confirmedBeacon = confirmed,
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
        updateStickyPinModeForZoom(coerced)

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
        updateStickyPinModeForZoom(target)
    }

    /**
     * Zoom out
     */
    fun zoomOut() {
        val target = maxOf(_zoomLevel.value - 1.0, 2.0)
        pendingProgrammaticZoomTarget = target
        pendingProgrammaticZoomSetAtMs = Clock.System.now().toEpochMilliseconds()
        anchorLatLonForProgrammaticCamera()?.let { (lat, lon) ->
            _cameraTarget.value = CameraTarget(latitude = lat, longitude = lon, zoom = target)
        }
        _zoomLevel.value = target
        updateStickyPinModeForZoom(target)
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
        _stickyPinMode.value = true
        
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

    fun onCommunityHubTapped(hub: CommunityHubPin, seedDistanceMeters: Double? = null) {
        val quickDistance = seedDistanceMeters?.takeIf { it.isFinite() && it < Double.MAX_VALUE }
            ?: hub.reportedDistanceMeters?.takeIf { it.isFinite() }
            ?: distanceToHubFromCachedLocation(hub)
        val quickCanJoin = quickDistance?.let { it <= hubJoinRadiusMeters(hub) }

        _selection.value = MapSelection.HubSelected(
            hub = hub,
            distanceMeters = quickDistance,
            canJoinGeofence = quickCanJoin,
        )

        viewModelScope.launch(Dispatchers.Default) {
            val loc = resolveFastMapLocation() ?: return@launch
            val distance = haversineDistance(loc.latitude, loc.longitude, hub.latitude, hub.longitude)
            val canJoin = distance <= hubJoinRadiusMeters(hub)
            val current = _selection.value as? MapSelection.HubSelected ?: return@launch
            if (current.hub.hubId != hub.hubId) return@launch
            _selection.value = current.copy(
                distanceMeters = distance,
                canJoinGeofence = canJoin,
            )
        }
    }

    private fun hubJoinRadiusMeters(hub: CommunityHubPin): Double =
        hub.radiusMeters.coerceAtLeast(1).toDouble()

    private fun distanceToHubFromCachedLocation(hub: CommunityHubPin): Double? {
        val cached = AppDataManager.lastKnownDeviceLocation.value ?: return null
        return haversineDistance(cached.first, cached.second, hub.latitude, hub.longitude)
    }

    private suspend fun resolveFastMapLocation(): LocationResult? =
        resolveHubGatekeeperLocation(
            locationService = locationService,
            lastKnownLatLon = AppDataManager.lastKnownDeviceLocation.value,
            highAccuracyTimeoutMs = HUB_GATEKEEPER_HIGH_ACCURACY_TIMEOUT_MS,
        )

    fun onMapPinTapped(pin: MapPin) {
        val overlaps = overlappingMapPins(
            tapped = pin,
            visiblePins = currentVisibleMapPins(),
            zoomLevel = _zoomLevel.value,
        )
        if (overlaps.size > 1) {
            _selection.value = MapSelection.OverlappingPinsSelected(overlaps)
            return
        }
        openResolvedMapPin(pin)
    }

    /** User picked one pin from an overlapping stack chooser. */
    fun onOverlappingPinChosen(pin: MapPin) {
        openResolvedMapPin(pin)
    }

    private fun currentVisibleMapPins(): List<MapPin> {
        val hubs = _communityHubs.value.map { MapPin.fromCommunityHub(it) }
        val currentUserId = AppDataManager.currentUser.value?.id
        val connectedUsers = AppDataManager.connectedUsers.value
        return when (val state = _renderData.value) {
            is MapRenderData.IndividualPins -> {
                val connections = state.points.map { point ->
                    val peerId = point.connection.user_ids.firstOrNull { it != currentUserId }
                    val peer = peerId?.let { connectedUsers[it] }
                    MapPin.fromConnectionPoint(
                        point,
                        imageUrl = peer?.image,
                        avatarSeed = peerId ?: point.connection.id,
                    )
                }
                val beacons = state.beacons.map { MapPin.fromBeacon(it) }
                connections + beacons + hubs
            }
            is MapRenderData.Clusters -> {
                state.standaloneBeacons.map { MapPin.fromBeacon(it) } + hubs
            }
        }
    }

    private fun openResolvedMapPin(pin: MapPin) {
        if (pin.kind == MapPinKind.COMMUNITY_HUB || pin.id.startsWith("hub:")) {
            val raw = pin.id.removePrefix("hub:")
            val hub = _communityHubs.value.firstOrNull { it.hubId == raw } ?: return
            onCommunityHubTapped(hub)
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
            updateStickyPinModeForZoom(_zoomLevel.value)
        }
    }

    /**
     * Called whenever the map screen is entered so we can restore the last viewport.
     */
    fun onMapScreenEntered() {
        if (_cameraTarget.value == null) {
            val raw = lastKnownCameraTarget
                ?: deviceLocationCameraTarget()
                ?: _defaultCameraTarget.value
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
                updateStickyPinModeForZoom(_zoomLevel.value)
            }
        }
        ensureDiscoveryFeedLoaded()
        hydrateEventEngagementFromServer()
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
            val pulseStartedAtMs = if (showPulse) {
                kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            } else {
                0L
            }
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
                            reportedDistanceMeters = dto.distanceMeters,
                        )
                    }
                    if (incoming.isNotEmpty()) {
                        _communityHubs.update { current ->
                            mergeCommunityHubLists(current, filterDismissedCommunityHubs(incoming))
                        }
                        AppDataManager.mergeCachedCommunityHubsFromDto(hubRows)
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
                        AppDataManager.mergeCachedMapBeacons(beaconRows)
                        hydrateEventEngagementFromServer()
                    }
                }
            }
            } finally {
                if (seq == discoveryFetchSeq) {
                    if (showPulse) {
                        // Hold the indicator long enough for Material pull-to-refresh to settle
                        // instead of snapping shut on a fast network response.
                        val elapsed = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() -
                            pulseStartedAtMs
                        val remaining = 520L - elapsed
                        if (remaining > 0L) delay(remaining)
                        _discoveryFeedLoading.value = false
                    }
                    if (markInitialComplete) {
                        val hasFeedData = _mapBeacons.value.isNotEmpty() ||
                            _communityHubs.value.isNotEmpty() ||
                            AppDataManager.prefetchedMapBeacons.value.isNotEmpty() ||
                            AppDataManager.prefetchedCommunityHubs.value.isNotEmpty()
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

        // Prefer a live/coarse GPS fix early so Android does not wait solely on map bounds.
        if (raw.isEmpty() && locationService.hasLocationPermission()) {
            val gps = locationService.getCurrentLocation()
                ?: locationService.getHighAccuracyLocation(1_500L)
            if (gps != null) {
                AppDataManager.noteDeviceLocation(gps.latitude, gps.longitude)
                raw += gps.latitude to gps.longitude
            }
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
                AppDataManager.noteDeviceLocation(gps.latitude, gps.longitude)
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
                            reportedDistanceMeters = dto.distanceMeters,
                        )
                    }
                    _communityHubs.update { current ->
                        mergeCommunityHubLists(current, filterDismissedCommunityHubs(incoming))
                    }
                    AppDataManager.mergeCachedCommunityHubsFromDto(rows)
                }
                beaconsDeferred?.await()?.onSuccess { list ->
                    if (list.isNotEmpty()) {
                        _mapBeacons.update { current -> mergeMapBeaconLists(current, list) }
                        AppDataManager.mergeCachedMapBeacons(list)
                    }
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
     * Connection junction updates handled by [RealtimeCoordinator] → [AppDataManager].
     */
    private fun subscribeToConnectionChanges() {
        // Intentionally empty — map reads AppDataManager.connections.
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

private const val DISCOVERY_PREFETCH_DEBOUNCE_MS = 400L
