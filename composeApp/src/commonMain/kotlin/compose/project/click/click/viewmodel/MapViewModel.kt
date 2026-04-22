package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.LocationPreferences // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconInsert // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.repository.ChatRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.MapBeaconRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseChatRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.ui.components.MapPin // pragma: allowlist secret
import compose.project.click.click.ui.utils.* // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import compose.project.click.click.util.teardownBlocking // pragma: allowlist secret
import compose.project.click.click.utils.LocationService // pragma: allowlist secret
import kotlinx.serialization.json.JsonObject // pragma: allowlist secret
import kotlinx.serialization.json.buildJsonObject // pragma: allowlist secret
import kotlinx.serialization.json.put // pragma: allowlist secret
import io.github.jan.supabase.realtime.PostgresAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow

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
}

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

    private val mapBeaconRepository = MapBeaconRepository()

    private val _beaconInsertError = MutableStateFlow<String?>(null)
    val beaconInsertError: StateFlow<String?> = _beaconInsertError.asStateFlow()

    private var beaconPollJob: Job? = null
    private var beaconFetchSeq: Long = 0L

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

    init {
        observeAppData()
        subscribeToConnectionChanges()
    }

    private fun observeAppData() {
        viewModelScope.launch {
            combine(
                AppDataManager.connections,
                AppDataManager.connectedUsers,
                AppDataManager.archivedConnectionIds,
                AppDataManager.hiddenConnectionIds,
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
                val isDataLoaded = values[4] as Boolean
                val isLoading = values[5] as Boolean
                val locationPrefs = values[6] as LocationPreferences
                val zoom = values[7] as Double
                Octuple(
                    connections,
                    connectedUsers,
                    archivedIds,
                    hiddenIds,
                    isDataLoaded,
                    isLoading,
                    locationPrefs,
                    zoom,
                )
            }.collectLatest { (connections, connectedUsers, archivedIds, hiddenIds, isDataLoaded, isLoading, locationPrefs, zoom) ->
                when {
                    // `archivedIds` is read so archive/unarchive recomputes the map when the connections list is unchanged.
                    isDataLoaded && (archivedIds.isNotEmpty() || archivedIds.isEmpty()) -> {
                        // Memory map: show full history (incl. per-user archived) but never removed/hidden rows.
                        val mapConnections = connections.filter { it.id !in hiddenIds }
                        _mapState.value = MapState.Success(mapConnections)
                        val mapVisibleConnections =
                            if (locationPrefs.showOnMapEnabled) mapConnections else emptyList()
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
                    _zoomLevel,
                ) { state, prefs, hidden, zoom ->
                    Quadruple(state, prefs, hidden, zoom)
                },
                _mapBeacons,
                _selectedLayerFilters,
            ) { base, _, _ ->
                base
            }.collectLatest { (state, prefs, hidden, zoom) ->
                if (state !is MapState.Success) return@collectLatest
                val mapVisible = state.connections.filter { it.id !in hidden }
                val visible = if (prefs.showOnMapEnabled) mapVisible else emptyList()
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
        val layers = _selectedLayerFilters.value
        val beaconsRaw = _mapBeacons.value
        renderDataJob = viewModelScope.launch {
            val rendered = withContext(Dispatchers.Default) {
                val showConnections = layers.contains(MapLayerFilter.ALL) ||
                    layers.contains(MapLayerFilter.MY_CONNECTIONS)
                val filteredConnections = if (showConnections) connections else emptyList()
                val filteredBeacons = filterBeaconsForLayers(beaconsRaw, layers)
                determineMapRenderData(filteredConnections, filteredBeacons, zoom, clusterThreshold)
            }
            _renderData.value = rendered
        }
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
                MapBeaconKind.SOCIAL_VIBE -> layers.contains(MapLayerFilter.SOCIAL_VIBES)
                MapBeaconKind.OTHER -> layers.contains(MapLayerFilter.SOCIAL_VIBES)
            }
            if (include) out.add(b)
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
        _visibleBounds.value?.let { scheduleBeaconFetchForBounds(it) }
    }

    fun clearBeaconInsertError() {
        _beaconInsertError.value = null
    }

    fun onBeaconPinTapped(beaconId: String) {
        viewModelScope.launch {
            val beacon = _mapBeacons.value.firstOrNull { it.id == beaconId }
                ?: return@launch
            val distance = locationService.getHighAccuracyLocation(3500L)?.let { loc ->
                haversineDistance(loc.latitude, loc.longitude, beacon.latitude, beacon.longitude)
            }
            _selection.value = MapSelection.BeaconSelected(beacon, distance)
        }
    }

    fun submitBeaconDrop(kind: MapBeaconKind, text: String, onFinished: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _beaconInsertError.value = null
            val loc = locationService.getHighAccuracyLocation(6000L)
                ?: run {
                    _beaconInsertError.value = "Could not read GPS. Enable location and try again."
                    onFinished(false)
                    return@launch
                }
            val trimmed = text.trim()
            val metadata: JsonObject? = when (kind) {
                MapBeaconKind.SOUNDTRACK -> {
                    if (!isValidStreamingUrl(trimmed)) {
                        _beaconInsertError.value = "Enter a valid Spotify or Apple Music link."
                        onFinished(false)
                        return@launch
                    }
                    buildJsonObject {
                        put("music_url", trimmed)
                    }
                }
                MapBeaconKind.SOS, MapBeaconKind.HAZARD, MapBeaconKind.UTILITY, MapBeaconKind.STUDY -> {
                    if (trimmed.isEmpty()) {
                        _beaconInsertError.value = "Please add a description."
                        onFinished(false)
                        return@launch
                    }
                    if (trimmed.length > 140) {
                        _beaconInsertError.value = "Description must be 140 characters or less."
                        onFinished(false)
                        return@launch
                    }
                    buildJsonObject {
                        put("description", trimmed)
                    }
                }
                else -> {
                    if (trimmed.isEmpty()) {
                        _beaconInsertError.value = "Please add a description."
                        onFinished(false)
                        return@launch
                    }
                    if (trimmed.length > 140) {
                        _beaconInsertError.value = "Description must be 140 characters or less."
                        onFinished(false)
                        return@launch
                    }
                    buildJsonObject {
                        put("description", trimmed)
                    }
                }
            }
            val insert = MapBeaconInsert(
                kind = kind.apiValue,
                lat = loc.latitude,
                lon = loc.longitude,
                metadata = metadata,
            )
            mapBeaconRepository.insertBeacon(insert).fold(
                onSuccess = {
                    _visibleBounds.value?.let { b ->
                        mapBeaconRepository.fetchLocalBeacons(
                            minLat = b.minLat,
                            maxLat = b.maxLat,
                            minLon = b.minLon,
                            maxLon = b.maxLon,
                        ).onSuccess { list -> _mapBeacons.value = list }
                    }
                    onFinished(true)
                },
                onFailure = { e ->
                    _beaconInsertError.value = e.message ?: "Could not drop beacon"
                    onFinished(false)
                },
            )
        }
    }

    private fun isValidStreamingUrl(s: String): Boolean {
        val lower = s.lowercase()
        val schemeOk = lower.startsWith("http://") || lower.startsWith("https://")
        if (!schemeOk) return false
        // Extract host from URL to prevent domain spoofing via substring matching.
        // e.g. "https://evil.com/path?q=spotify.com" must NOT pass.
        val hostPart = lower.removePrefix("https://").removePrefix("http://")
            .substringBefore("/").substringBefore("?").substringBefore("#")
            .substringBefore(":")
        return hostPart == "spotify.com" || hostPart.endsWith(".spotify.com") ||
            hostPart == "music.apple.com" || hostPart.endsWith(".music.apple.com") ||
            hostPart == "itunes.apple.com" || hostPart.endsWith(".itunes.apple.com")
    }

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

    private fun scheduleBeaconFetchForBounds(bounds: BoundingBox) {
        val layers = _selectedLayerFilters.value
        val wantBeacons = layers.contains(MapLayerFilter.ALL) ||
            layers.contains(MapLayerFilter.SOUNDTRACKS) ||
            layers.contains(MapLayerFilter.ALERTS_UTILITIES) ||
            layers.contains(MapLayerFilter.SOCIAL_VIBES)
        if (!wantBeacons) return
        if (AppDataManager.currentUser.value == null) return

        beaconPollJob?.cancel()
        val seq = ++beaconFetchSeq
        beaconPollJob = viewModelScope.launch {
            delay(400)
            if (seq != beaconFetchSeq) return@launch
            val result = mapBeaconRepository.fetchLocalBeacons(
                minLat = bounds.minLat,
                maxLat = bounds.maxLat,
                minLon = bounds.minLon,
                maxLon = bounds.maxLon,
            )
            result.onSuccess { list -> _mapBeacons.value = list }
        }
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
        if (_cameraTarget.value != null) return

        val raw = lastKnownCameraTarget ?: _defaultCameraTarget.value ?: return
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
