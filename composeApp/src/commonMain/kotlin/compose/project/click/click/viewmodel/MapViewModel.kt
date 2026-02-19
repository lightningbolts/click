package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.User
import compose.project.click.click.ui.utils.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
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

    // Visible bounds for viewport-based filtering in ConnectionsList
    private val _visibleBounds = MutableStateFlow<BoundingBox?>(null)
    val visibleBounds: StateFlow<BoundingBox?> = _visibleBounds.asStateFlow()

    // Tribe filter for categorizing connections
    private val _selectedFilter = MutableStateFlow("All")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()
    val availableFilters = listOf("All", "Study", "Party", "Coffee", "Outdoors")

    // Cluster threshold - zoom level above which individual pins are shown
    private val clusterThreshold = 12.0

    init {
        observeAppData()
    }

    private fun observeAppData() {
        viewModelScope.launch {
            combine(
                AppDataManager.connections,
                AppDataManager.isDataLoaded,
                AppDataManager.isLoading,
                _zoomLevel,
                _selectedFilter
            ) { values ->
                // Using array-based combine for 5+ flows
                @Suppress("UNCHECKED_CAST")
                val connections = values[0] as List<Connection>
                val isDataLoaded = values[1] as Boolean
                val isLoading = values[2] as Boolean
                val zoom = values[3] as Double
                val filter = values[4] as String
                Quintuple(connections, isDataLoaded, isLoading, zoom, filter)
            }.collectLatest { (connections, isDataLoaded, isLoading, zoom, filter) ->
                when {
                    isDataLoaded -> {
                        _mapState.value = MapState.Success(connections)
                        updateRenderData(connections, zoom, filter)
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
                val tag = conn.context_tag?.lowercase() ?: ""
                val searchTerm = filter.lowercase()
                location.contains(searchTerm) || tag.contains(searchTerm)
            }
        }
        _renderData.value = determineMapRenderData(filtered, zoom, clusterThreshold)
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
        _zoomLevel.value = zoom
        estimateVisibleBounds()
    }

    /**
     * Update visible bounds from outside (e.g., the platform map callback)
     */
    fun updateVisibleBounds(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {
        _visibleBounds.value = BoundingBox(minLat, maxLat, minLon, maxLon)
    }

    /**
     * Estimate visible bounds from zoom level and camera target.
     * This is used as a fallback when the platform map doesn't report bounds.
     */
    private fun estimateVisibleBounds() {
        val center = _cameraTarget.value
        val centerLat = center?.latitude ?: run {
            // Estimate from connections
            val state = _mapState.value
            if (state is MapState.Success && state.connections.isNotEmpty()) {
                state.connections.map { it.geo_location.lat }.average()
            } else return
        }
        val centerLon = center?.longitude ?: run {
            val state = _mapState.value
            if (state is MapState.Success && state.connections.isNotEmpty()) {
                state.connections.map { it.geo_location.lon }.average()
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
        _zoomLevel.value = minOf(_zoomLevel.value + 1.0, 20.0)
        estimateVisibleBounds()
    }

    /**
     * Zoom out
     */
    fun zoomOut() {
        _zoomLevel.value = maxOf(_zoomLevel.value - 1.0, 2.0)
        estimateVisibleBounds()
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
