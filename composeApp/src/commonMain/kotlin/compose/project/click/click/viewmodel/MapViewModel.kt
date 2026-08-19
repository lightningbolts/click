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
        internal var lastKnownCameraTarget: CameraTarget? = null
        internal const val DEFAULT_USER_MAP_ZOOM = 14.0
    }

    internal var seededDeviceCameraThisSession = false

    // Base data state
    internal val _mapState = MutableStateFlow<MapState>(MapState.Loading)
    val mapState: StateFlow<MapState> = _mapState.asStateFlow()

    // Current zoom level (logical; updated from native map readbacks and programmatic moves)
    internal val _zoomLevel = MutableStateFlow(10.0)
    val zoomLevel: StateFlow<Double> = _zoomLevel.asStateFlow()

    // What to render on the map (clusters or individual pins)
    internal val _renderData = MutableStateFlow<MapRenderData>(MapRenderData.Clusters(emptyList()))
    val renderData: StateFlow<MapRenderData> = _renderData.asStateFlow()

    // Currently selected item (for bottom sheet)
    internal val _selection = MutableStateFlow<MapSelection>(MapSelection.None)
    val selection: StateFlow<MapSelection> = _selection.asStateFlow()

    // Ghost Mode - when enabled, user location is not shared
    val ghostModeEnabled: StateFlow<Boolean> = AppDataManager.ghostModeEnabled

    // Camera target for animations
    internal val _cameraTarget = MutableStateFlow<CameraTarget?>(null)
    val cameraTarget: StateFlow<CameraTarget?> = _cameraTarget.asStateFlow()

    // Stable default camera target derived from all connection locations.
    internal val _defaultCameraTarget = MutableStateFlow<CameraTarget?>(lastKnownCameraTarget)
    val defaultCameraTarget: StateFlow<CameraTarget?> = _defaultCameraTarget.asStateFlow()

    // Visible bounds for viewport-based filtering in ConnectionsList
    internal val _visibleBounds = MutableStateFlow<BoundingBox?>(null)
    val visibleBounds: StateFlow<BoundingBox?> = _visibleBounds.asStateFlow()

    internal val _selectedLayerFilters = MutableStateFlow(defaultMapLayerFilters())
    val selectedLayerFilters: StateFlow<Set<MapLayerFilter>> = _selectedLayerFilters.asStateFlow()

    val availableLayerFilters: List<MapLayerFilter> = MapLayerFilter.entries

    internal val _mapBeacons = MutableStateFlow<List<MapBeacon>>(emptyList())
    val mapBeacons: StateFlow<List<MapBeacon>> = _mapBeacons.asStateFlow()

    internal val _communityHubs = MutableStateFlow<List<CommunityHubPin>>(emptyList())
    val communityHubs: StateFlow<List<CommunityHubPin>> = _communityHubs.asStateFlow()

    /** Layer-filtered beacons for the discovery feed (matches map chip filters). */
    val discoveryFeedBeacons: StateFlow<List<MapBeacon>> =
        combine(_mapBeacons, _selectedLayerFilters) { beacons, layers ->
            filterBeaconsForLayers(beacons, layers)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val discoveryFeedHubs: StateFlow<List<CommunityHubPin>> =
        combine(_communityHubs, _selectedLayerFilters) { hubs, layers ->
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

    internal val mapBeaconRepository = MapBeaconRepository()
    internal val tokenStorage: TokenStorage = createTokenStorage()
    internal val authRepository: AuthRepository by lazy { AuthRepository(tokenStorage) }
    internal val apiClient: ApiClient by lazy { ApiClient() }

    internal val _beaconInsertError = MutableStateFlow<String?>(null)
    val beaconInsertError: StateFlow<String?> = _beaconInsertError.asStateFlow()

    /** One-shot remote failure after an optimistic beacon was shown (sheet already dismissed). */
    internal val _beaconDropFailureToast = MutableStateFlow<String?>(null)
    val beaconDropFailureToast: StateFlow<String?> = _beaconDropFailureToast.asStateFlow()

    /** True while a beacon drop POST is in flight (prevents duplicate soundtrack inserts). */
    internal val _beaconSubmitInFlight = MutableStateFlow(false)
    val beaconSubmitInFlight: StateFlow<Boolean> = _beaconSubmitInFlight.asStateFlow()

    /** Cached RSVP state keyed by beacon id — survives tab navigation and sheet dismiss. */
    internal val _beaconRsvpById = MutableStateFlow<Map<String, BeaconRsvpCacheEntry>>(emptyMap())
    val beaconRsvpById: StateFlow<Map<String, BeaconRsvpCacheEntry>> = _beaconRsvpById.asStateFlow()

    internal val _beaconDirectoryById = MutableStateFlow<Map<String, BeaconDirectoryCacheEntry>>(emptyMap())
    val beaconDirectoryById: StateFlow<Map<String, BeaconDirectoryCacheEntry>> = _beaconDirectoryById.asStateFlow()

    internal val _beaconDirectoryLoadingIds = MutableStateFlow<Set<String>>(emptySet())
    val beaconDirectoryLoadingIds: StateFlow<Set<String>> = _beaconDirectoryLoadingIds.asStateFlow()

    /** Beacon ids with an in-flight GET `/api/beacons/{id}/rsvp`. */
    internal val _beaconRsvpLoadingIds = MutableStateFlow<Set<String>>(emptySet())
    val beaconRsvpLoadingIds: StateFlow<Set<String>> = _beaconRsvpLoadingIds.asStateFlow()

    /** Beacon ids with an optimistic POST/DELETE awaiting server confirmation. */
    internal val _beaconRsvpPendingIds = MutableStateFlow<Set<String>>(emptySet())
    val beaconRsvpPendingIds: StateFlow<Set<String>> = _beaconRsvpPendingIds.asStateFlow()

    internal val _beaconEngagementById =
        MutableStateFlow<Map<String, BeaconEngagementCacheEntry>>(emptyMap())
    val beaconEngagementById: StateFlow<Map<String, BeaconEngagementCacheEntry>> =
        _beaconEngagementById.asStateFlow()

    internal val _beaconEngagementPendingIds = MutableStateFlow<Set<String>>(emptySet())

    /** Combined pending set (legacy); prefer [beaconBookmarkPendingIds] / [beaconCheckInPendingIds]. */
    val beaconEngagementPendingIds: StateFlow<Set<String>> =
        _beaconEngagementPendingIds.asStateFlow()
    internal val _beaconBookmarkPendingIds = MutableStateFlow<Set<String>>(emptySet())
    val beaconBookmarkPendingIds: StateFlow<Set<String>> = _beaconBookmarkPendingIds.asStateFlow()
    internal val _beaconCheckInPendingIds = MutableStateFlow<Set<String>>(emptySet())
    val beaconCheckInPendingIds: StateFlow<Set<String>> = _beaconCheckInPendingIds.asStateFlow()

    /**
     * Beacon ids the user early-checked-in (HTTP 409). Survives force-refresh races that can
     * briefly see checkedIn=true with localEarlyCheckIn=false before the 409 write lands.
     */
    internal val earlyCheckInBeaconIds = mutableSetOf<String>()
    internal val engagementPersistMutex = Mutex()
    internal var engagementPersistGeneration = 0

    internal val _engagementSnackbar = MutableStateFlow<String?>(null)
    val engagementSnackbar: StateFlow<String?> = _engagementSnackbar.asStateFlow()

    fun clearEngagementSnackbar() {
        _engagementSnackbar.value = null
    }

    /**
     * False until startup prefetch or the first map-tab proximity fetch finishes.
     * Silent map refreshes do not reset this.
     */
    internal val _discoveryProximityFetchCompleted = MutableStateFlow(false)
    val discoveryProximityFetchCompleted: StateFlow<Boolean> =
        _discoveryProximityFetchCompleted.asStateFlow()

    /** Drives the discovery feed logo pulse (initial load + user pull-to-refresh). */
    internal val _discoveryFeedLoading = MutableStateFlow(false)
    val discoveryFeedLoading: StateFlow<Boolean> = _discoveryFeedLoading.asStateFlow()

    val discoveryFeedPending: StateFlow<Boolean> =
        combine(
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
            val hasLocalFeedData =
                beacons.isNotEmpty() ||
                    hubs.isNotEmpty() ||
                    prefetchedBeacons.isNotEmpty() ||
                    prefetchedHubs.isNotEmpty()
            loading || (!completed && !hasLocalFeedData)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
        )

    internal var beaconPollJob: Job? = null
    internal var discoveryProximityJob: Job? = null
    internal var discoveryPrefetchRetryJob: Job? = null
    internal var discoveryPrefetchAttempts = 0
    internal var beaconFetchSeq: Long = 0L
    internal var discoveryFetchSeq: Long = 0L

    /** Discovery feed uses a GPS-centered radius so beacons load before the map is zoomed in. */
    internal val discoveryProximityRadiusMeters = 50_000.0

    internal val maxDiscoveryPrefetchAttempts = 5

    internal val locationService = LocationService()

    // Cluster threshold - zoom level above which individual pins are shown
    internal val clusterThreshold = 12.0

    /**
     * Must zoom out past this before leaving individual-pin mode. Prevents cluster↔pin flicker
     * when native zoom readbacks chatter around [clusterThreshold] during pinch.
     */
    internal val pinModeExitBelow = clusterThreshold - 0.75

    /**
     * After a cluster zoom-in, native map zoom readbacks often dip below [clusterThreshold] briefly
     * or stay inconsistent with [metersForZoom]. Until the user clearly zooms out past the
     * threshold, treat the map as "pin mode" for clustering decisions so [determineMapRenderData]
     * does not snap back to hub markers while the camera is still on the cluster.
     */
    internal val _pinRenderZoomFloor = MutableStateFlow<Double?>(null)

    /** Sticky pin mode from user pinch (enter ≥ threshold, exit only below [pinModeExitBelow]). */
    internal val _stickyPinMode = MutableStateFlow(false)

    /**
     * Zoom passed to [PlatformMap] for camera span / meters. Sits above [_zoomLevel] while
     * [_pinRenderZoomFloor] keeps pin mode so the map is not left at world scale with many
     * markers stacked on one pixel.
     */
    val mapBindingZoom: StateFlow<Double> =
        combine(_zoomLevel, _pinRenderZoomFloor) { z, floor ->
            floor?.let { maxOf(z, it) } ?: z
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = _zoomLevel.value,
        )

    // Realtime channel for connections changes
    internal var connectionsChannel: RealtimeChannel? = null

    // Chat repository for nudge messages
    internal val chatRepository: ChatRepository = SupabaseChatRepository(tokenStorage = createTokenStorage())

    // Nudge result for snackbar feedback
    internal val _nudgeResult = MutableStateFlow<String?>(null)
    val nudgeResult: StateFlow<String?> = _nudgeResult.asStateFlow()

    // Guards against map callback feedback immediately canceling programmatic zoom animations.
    internal var pendingProgrammaticZoomTarget: Double? = null
    internal var pendingProgrammaticZoomSetAtMs: Long = 0L

    internal var renderDataJob: Job? = null

    // Job for incremental population of markers when initial cap is applied
    internal var incrementalPopulationJob: Job? = null

    // Initial number of pins to render immediately. The rest will be added incrementally.
    internal val INITIAL_PIN_CAP = 200

    internal val beaconSubmitMutex = Mutex()

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

    fun warmDiscoveryFeed() = warmDiscoveryFeedImpl()

    fun updateMapDeviceLocation(
        latitude: Double,
        longitude: Double,
    ) = updateMapDeviceLocationImpl(latitude = latitude, longitude = longitude)

    fun toggleLayerFilter(filter: MapLayerFilter) = toggleLayerFilterImpl(filter = filter)

    fun applyHomeLayerPreset(filter: MapLayerFilter) = applyHomeLayerPresetImpl(filter = filter)

    fun clearBeaconInsertError() {
        _beaconInsertError.value = null
    }

    fun clearBeaconDropFailureToast() {
        _beaconDropFailureToast.value = null
    }

    fun onBeaconPinTapped(
        beaconId: String,
        seedDistanceMeters: Double? = null,
    ) = onBeaconPinTappedImpl(beaconId = beaconId, seedDistanceMeters = seedDistanceMeters)

    /** Beacon ids that already received a successful detail GET this session. */
    internal val eventDetailHydratedIds = mutableSetOf<String>()
    internal val soundtrackDetailHydratedIds = mutableSetOf<String>()

    fun ensureEventBeaconDetail(
        beaconId: String,
        seed: MapBeacon? = null,
    ) = ensureEventBeaconDetailImpl(beaconId = beaconId, seed = seed)

    fun ensureSoundtrackBeaconDetail(
        beaconId: String,
        seed: MapBeacon? = null,
    ) = ensureSoundtrackBeaconDetailImpl(beaconId = beaconId, seed = seed)

    fun ensureEventBeaconSchedule(beaconId: String) = ensureEventBeaconScheduleImpl(beaconId = beaconId)

    fun loadBeaconRsvp(
        beaconId: String,
        forceRefresh: Boolean = false,
    ) = loadBeaconRsvpImpl(beaconId = beaconId, forceRefresh = forceRefresh)

    fun loadBeaconAttendeeDirectory(
        beaconId: String,
        forceRefresh: Boolean = false,
    ) = loadBeaconAttendeeDirectoryImpl(beaconId = beaconId, forceRefresh = forceRefresh)

    /** Restores/refreshes Supabase session before click-web bearer calls (cold start). */
    internal suspend fun ensureClickWebAuthReady(): Boolean = ClickWebAuthCoordinator.ensureReady(authRepository)

    fun hasLocationPermission(): Boolean = locationService.hasLocationPermission()

    /** Exposed for BeaconDropSheet “Use my location”. */
    suspend fun resolveDropLocationForUi(): LocationResult? = resolveBeaconDropLocation()

    fun focusBeaconOnMap(
        beaconId: String,
        seedDistanceMeters: Double? = null,
    ) = focusBeaconOnMapImpl(beaconId = beaconId, seedDistanceMeters = seedDistanceMeters)

    fun rsvpToBeacon(
        beaconId: String,
        onFinished: (Boolean) -> Unit = {},
    ) = rsvpToBeaconImpl(beaconId = beaconId, onFinished = onFinished)

    fun cancelRsvpToBeacon(
        beaconId: String,
        onFinished: (Boolean) -> Unit = {},
    ) = cancelRsvpToBeaconImpl(beaconId = beaconId, onFinished = onFinished)

    fun loadBeaconEngagement(
        beaconId: String,
        forceRefresh: Boolean = false,
    ) = loadBeaconEngagementImpl(beaconId = beaconId, forceRefresh = forceRefresh)

    fun hydrateEventEngagementFromServer() = hydrateEventEngagementFromServerImpl()

    fun recordEventImpression(beaconId: String) = recordEventImpressionImpl(beaconId = beaconId)

    fun recordEventShare(
        beaconId: String,
        shareUrl: String? = null,
    ) = recordEventShareImpl(beaconId = beaconId, shareUrl = shareUrl)

    fun toggleBeaconBookmark(beaconId: String) = toggleBeaconBookmarkImpl(beaconId = beaconId)

    fun toggleBeaconCheckIn(beaconId: String) = toggleBeaconCheckInImpl(beaconId = beaconId)

    fun deleteOwnedBeacon(
        beaconId: String,
        onFinished: (Boolean) -> Unit = {},
    ) = deleteOwnedBeaconImpl(beaconId = beaconId, onFinished = onFinished)

    fun updateOwnedBeaconDescription(
        beaconId: String,
        description: String,
        onFinished: (Boolean) -> Unit = {},
    ) = updateOwnedBeaconDescriptionImpl(beaconId = beaconId, description = description, onFinished = onFinished)

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
        imageBytes: ByteArray? = null,
        imageMime: String? = null,
        onAcceptedLocally: () -> Unit = {},
        onRejectedEarly: () -> Unit = {},
        onRemoteFinished: (Boolean) -> Unit = {},
    ) = submitBeaconDropImpl(kind = kind, title = title, description = description, soundtrackUrl = soundtrackUrl, ttlMs = ttlMs, showCreatorName = showCreatorName, visibilityAudience = visibilityAudience, eventSchedule = eventSchedule, eventCategories = eventCategories, venueScale = venueScale, eventLocation = eventLocation, imageBytes = imageBytes, imageMime = imageMime, onAcceptedLocally = onAcceptedLocally, onRejectedEarly = onRejectedEarly, onRemoteFinished = onRemoteFinished)

    // URL validation is now in compose.project.click.click.util.isValidStreamingUrl

    fun setZoomLevel(zoom: Double) = setZoomLevelImpl(zoom = zoom)

    fun updateVisibleBounds(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ) = updateVisibleBoundsImpl(minLat = minLat, maxLat = maxLat, minLon = minLon, maxLon = maxLon)

    fun zoomIn() = zoomInImpl()

    fun zoomOut() = zoomOutImpl()

    fun onClusterTappedFromMap(clusterId: String) = onClusterTappedFromMapImpl(clusterId = clusterId)

    fun onClusterTapped(cluster: MapCluster) = onClusterTappedImpl(cluster = cluster)

    fun onConnectionTapped(point: ConnectionMapPoint) = onConnectionTappedImpl(point = point)

    fun onCommunityHubTapped(
        hub: CommunityHubPin,
        seedDistanceMeters: Double? = null,
    ) = onCommunityHubTappedImpl(hub = hub, seedDistanceMeters = seedDistanceMeters)

    fun onMapPinTapped(pin: MapPin) = onMapPinTappedImpl(pin = pin)

    fun onOverlappingPinChosen(pin: MapPin) = onOverlappingPinChosenImpl(pin = pin)

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

    fun onCameraAnimationComplete() = onCameraAnimationCompleteImpl()

    /**
     * Called whenever the map screen is entered so we can restore the last viewport.
     */
    fun onMapScreenEntered() {
        if (_cameraTarget.value == null) {
            val raw =
                lastKnownCameraTarget
                    ?: deviceLocationCameraTarget()
                    ?: _defaultCameraTarget.value
            if (raw != null) {
                val safeZoom = raw.zoom.coerceIn(4.0, 20.0)
                val target =
                    if (abs(raw.zoom - safeZoom) > 0.01) {
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

    fun refreshDiscoveryFeed() = refreshDiscoveryFeedImpl()

    fun refreshDiscoveryFromMapInteraction() = refreshDiscoveryFromMapInteractionImpl()

    fun prefetchDiscoveryProximityData(
        showPulse: Boolean = false,
        markInitialComplete: Boolean = true,
    ) = prefetchDiscoveryProximityDataImpl(showPulse = showPulse, markInitialComplete = markInitialComplete)

    internal enum class DiscoveryFetchSlot { MapViewport, Discovery }

    fun loadConnections() = loadConnectionsImpl()

    fun refresh() = refreshImpl()

    fun getMapStats(): MapStats = getMapStatsImpl()

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

    fun sendNudge(
        connectionId: String,
        otherUserName: String,
    ) = sendNudgeImpl(connectionId = connectionId, otherUserName = otherUserName)

    fun clearNudgeResult() {
        _nudgeResult.value = null
    }
}
