package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.User
import compose.project.click.click.data.repository.ChatRepository
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.data.storage.createTokenStorage
import compose.project.click.click.ui.utils.*
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.datetime.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

    // Current zoom level (drives cluster vs pin rendering)
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

    // Tribe filter for categorizing connections
    private val _selectedFilter = MutableStateFlow("All")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()
    val availableFilters = listOf("All", "Study", "Party", "Coffee", "Outdoors")

    // Cluster threshold - zoom level above which individual pins are shown
    private val clusterThreshold = 12.0
    
    // Realtime channel for connections changes
    private var connectionsChannel: RealtimeChannel? = null

    // Chat repository for nudge messages
    private val chatRepository = ChatRepository(tokenStorage = createTokenStorage())

    // Nudge result for snackbar feedback
    private val _nudgeResult = MutableStateFlow<String?>(null)
    val nudgeResult: StateFlow<String?> = _nudgeResult.asStateFlow()

    // Guards against map callback feedback immediately canceling programmatic zoom animations.
    private var pendingProgrammaticZoomTarget: Double? = null
    private var pendingProgrammaticZoomSetAtMs: Long = 0L

    init {
        observeAppData()
        subscribeToConnectionChanges()
    }

    private fun observeAppData() {
        viewModelScope.launch {
            combine(
                AppDataManager.connections,
                AppDataManager.connectedUsers,
                AppDataManager.isDataLoaded,
                AppDataManager.isLoading,
                _zoomLevel,
                _selectedFilter
            ) { values ->
                // Using array-based combine for 5+ flows
                @Suppress("UNCHECKED_CAST")
                val connections = values[0] as List<Connection>
                val connectedUsers = values[1] as Map<String, User>
                val isDataLoaded = values[2] as Boolean
                val isLoading = values[3] as Boolean
                val zoom = values[4] as Double
                val filter = values[5] as String
                Sextuple(connections, connectedUsers, isDataLoaded, isLoading, zoom, filter)
            }.collectLatest { (connections, connectedUsers, isDataLoaded, isLoading, zoom, filter) ->
                when {
                    isDataLoaded -> {
                        _mapState.value = MapState.Success(connections)
                        ensureDefaultCameraTarget(connections)
                        updateRenderData(connections, zoom, filter)
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
    }

    /**
     * Update render data based on connections, zoom level, and active filter
     */
    private fun updateRenderData(connections: List<Connection>, zoom: Double, filter: String = "All") {
        val filtered = if (filter == "All") {
            connections
        } else {
            connections.filter { conn ->
                val location = conn.semantic_location?.lowercase() ?: ""
                val tag = conn.displayLocationLabel?.lowercase() ?: ""
                val searchTerm = filter.lowercase()
                location.contains(searchTerm) || tag.contains(searchTerm)
            }
        }
        _renderData.value = determineMapRenderData(filtered, zoom, clusterThreshold)
    }

    /**
     * Computes a one-time default camera based on all valid connection coordinates.
     */
    private fun ensureDefaultCameraTarget(connections: List<Connection>) {
        if (_defaultCameraTarget.value != null) return

        val valid = connections.filter {
            val lat = it.geo_location.lat
            val lon = it.geo_location.lon
            lat.isFinite() && lon.isFinite() && !(lat == 0.0 && lon == 0.0)
        }

        if (valid.isEmpty()) return

        val minLat = valid.minOf { it.geo_location.lat }
        val maxLat = valid.maxOf { it.geo_location.lat }
        val minLon = valid.minOf { it.geo_location.lon }
        val maxLon = valid.maxOf { it.geo_location.lon }

        val bounds = BoundingBox(minLat = minLat, maxLat = maxLat, minLon = minLon, maxLon = maxLon)
        val targetZoom = calculateZoomForBounds(bounds).coerceIn(2.0, 16.0)

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

    /**
     * Set the active tribe filter
     */
    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    /**
     * Update the current zoom level
     */
    fun setZoomLevel(zoom: Double) {
        val pendingTarget = pendingProgrammaticZoomTarget
        if (pendingTarget != null) {
            val now = Clock.System.now().toEpochMilliseconds()
            val ageMs = now - pendingProgrammaticZoomSetAtMs
            val reachedPendingTarget = abs(zoom - pendingTarget) <= 0.25

            if (reachedPendingTarget || ageMs > 1500L) {
                pendingProgrammaticZoomTarget = null
            } else {
                return
            }
        }

        if (abs(_zoomLevel.value - zoom) <= 0.01) return
        _zoomLevel.value = zoom

        _visibleBounds.value?.let { bounds ->
            persistCameraTarget(
                latitude = bounds.centerLat,
                longitude = bounds.centerLon,
                zoom = zoom
            )
        }
    }

    /**
     * Update visible bounds from outside (e.g., the platform map callback)
     */
    fun updateVisibleBounds(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {
        val bounds = BoundingBox(minLat, maxLat, minLon, maxLon)
        _visibleBounds.value = bounds
        persistCameraTarget(
            latitude = bounds.centerLat,
            longitude = bounds.centerLon,
            zoom = _zoomLevel.value
        )
    }

    private fun persistCameraTarget(latitude: Double, longitude: Double, zoom: Double) {
        if (!latitude.isFinite() || !longitude.isFinite() || !zoom.isFinite()) return

        val candidate = CameraTarget(
            latitude = latitude,
            longitude = longitude,
            zoom = zoom.coerceIn(2.0, 20.0)
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
                val lat = it.geo_location.lat
                val lon = it.geo_location.lon
                lat.isFinite() && lon.isFinite() && !(lat == 0.0 && lon == 0.0)
            }
        }

        val center = _cameraTarget.value
        val centerLat = center?.latitude ?: run {
            val connections = validConnections()
            if (connections.isNotEmpty()) {
                connections.map { it.geo_location.lat }.average()
            } else return
        }
        val centerLon = center?.longitude ?: run {
            val connections = validConnections()
            if (connections.isNotEmpty()) {
                connections.map { it.geo_location.lon }.average()
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

    /**
     * Zoom in
     */
    fun zoomIn() {
        val target = minOf(_zoomLevel.value + 1.0, 20.0)
        pendingProgrammaticZoomTarget = target
        pendingProgrammaticZoomSetAtMs = Clock.System.now().toEpochMilliseconds()
        _zoomLevel.value = target
    }

    /**
     * Zoom out
     */
    fun zoomOut() {
        val target = maxOf(_zoomLevel.value - 1.0, 2.0)
        pendingProgrammaticZoomTarget = target
        pendingProgrammaticZoomSetAtMs = Clock.System.now().toEpochMilliseconds()
        _zoomLevel.value = target
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
        _cameraTarget.value = null
    }

    /**
     * Called whenever the map screen is entered so we can restore the last viewport.
     */
    fun onMapScreenEntered() {
        if (_cameraTarget.value != null) return

        val target = lastKnownCameraTarget ?: _defaultCameraTarget.value
        if (target != null) {
            _cameraTarget.value = target
            if (abs(_zoomLevel.value - target.zoom) > 0.01) {
                _zoomLevel.value = target.zoom
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
                
                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "connections"
                }.onEach {
                    AppDataManager.refresh(force = true)
                }.launchIn(this)
                
                channel.subscribe()
            } catch (e: Exception) {
                println("MapViewModel: Error subscribing to connections: ${e.message}")
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        connectionsChannel?.let { channel ->
            viewModelScope.launch {
                try { channel.unsubscribe() } catch (_: Exception) {}
            }
        }
        connectionsChannel = null
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
