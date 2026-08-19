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
 * Resolves a tapped cluster marker to [MapCluster] using the latest [renderData] snapshot
 * inside the ViewModel (avoids races with Compose where [renderData] already flipped to pins).
 */
internal fun MapViewModel.onClusterTappedFromMapImpl(clusterId: String) {
    fun findCluster(): MapCluster? = (_renderData.value as? MapRenderData.Clusters)?.clusters?.find { it.id == clusterId }

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
internal fun MapViewModel.onClusterTappedImpl(cluster: MapCluster) {
    _selection.value = MapSelection.ClusterSelected(cluster)

    // Calculate zoom level to fit the cluster bounds
    val bounds = cluster.boundingBox
    val targetZoom = maxOf(clusterThreshold + 1, calculateZoomForBounds(bounds))
    _pinRenderZoomFloor.value = maxOf(clusterThreshold + 0.25, targetZoom)
    _stickyPinMode.value = true

    // Animate camera to cluster center with appropriate zoom
    _cameraTarget.value =
        CameraTarget(
            latitude = bounds.centerLat,
            longitude = bounds.centerLon,
            zoom = targetZoom,
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
internal fun MapViewModel.onConnectionTappedImpl(point: ConnectionMapPoint) {
    viewModelScope.launch {
        // Find the other user in this connection
        val currentUserId = AppDataManager.currentUser.value?.id
        val otherUserId = point.connection.user_ids.find { it != currentUserId }
        val otherUser = otherUserId?.let { AppDataManager.getConnectedUser(it) }

        _selection.value = MapSelection.ConnectionSelected(point, otherUser)
    }
}

internal fun MapViewModel.onCommunityHubTappedImpl(
    hub: CommunityHubPin,
    seedDistanceMeters: Double? = null,
) {
    val quickDistance =
        seedDistanceMeters?.takeIf { it.isFinite() && it < Double.MAX_VALUE }
            ?: hub.reportedDistanceMeters?.takeIf { it.isFinite() }
            ?: distanceToHubFromCachedLocation(hub)
    val quickCanJoin = quickDistance?.let { it <= hubJoinRadiusMeters(hub) }

    _selection.value =
        MapSelection.HubSelected(
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
        _selection.value =
            current.copy(
                distanceMeters = distance,
                canJoinGeofence = canJoin,
            )
    }
}

internal fun MapViewModel.hubJoinRadiusMeters(hub: CommunityHubPin): Double = hub.radiusMeters.coerceAtLeast(1).toDouble()


internal fun MapViewModel.distanceToHubFromCachedLocation(hub: CommunityHubPin): Double? {
    val cached = AppDataManager.lastKnownDeviceLocation.value ?: return null
    return haversineDistance(cached.first, cached.second, hub.latitude, hub.longitude)
}

internal suspend fun MapViewModel.resolveFastMapLocation(): LocationResult? =
    resolveHubGatekeeperLocation(
        locationService = locationService,
        lastKnownLatLon = AppDataManager.lastKnownDeviceLocation.value,
        highAccuracyTimeoutMs = HUB_GATEKEEPER_HIGH_ACCURACY_TIMEOUT_MS,
    )


internal fun MapViewModel.onMapPinTappedImpl(pin: MapPin) {
    val overlaps =
        overlappingMapPins(
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
internal fun MapViewModel.onOverlappingPinChosenImpl(pin: MapPin) {
    openResolvedMapPin(pin)
}

internal fun MapViewModel.currentVisibleMapPins(): List<MapPin> {
    val hubs = _communityHubs.value.map { MapPin.fromCommunityHub(it) }
    val currentUserId = AppDataManager.currentUser.value?.id
    val connectedUsers = AppDataManager.connectedUsers.value
    return when (val state = _renderData.value) {
        is MapRenderData.IndividualPins -> {
            val connections =
                state.points.map { point ->
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

internal fun MapViewModel.openResolvedMapPin(pin: MapPin) {
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
        val point =
            when (state) {
                is MapRenderData.IndividualPins ->
                    state.points.firstOrNull { it.connection.id == pin.id }
                is MapRenderData.Clusters ->
                    state.clusters.flatMap { it.points }.firstOrNull { it.connection.id == pin.id }
            }
        if (point != null) onConnectionTapped(point)
    }
}

internal fun MapViewModel.refreshSelectedConnectionUser(connectedUsers: Map<String, User>) {
    val selected = _selection.value as? MapSelection.ConnectionSelected ?: return
    val currentUserId = AppDataManager.currentUser.value?.id
    val otherUserId =
        selected.point.connection.user_ids
            .find { it != currentUserId } ?: return
    val refreshedUser = connectedUsers[otherUserId] ?: return
    if (refreshedUser != selected.otherUser) {
        _selection.value = selected.copy(otherUser = refreshedUser)
    }
}

internal fun MapViewModel.toggleLayerFilterImpl(filter: MapLayerFilter) {
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
internal fun MapViewModel.applyHomeLayerPresetImpl(filter: MapLayerFilter) {
    _pinRenderZoomFloor.value = null
    _selectedLayerFilters.value =
        when (filter) {
            MapLayerFilter.ALL -> defaultMapLayerFilters()
            else -> setOf(filter)
        }
    _visibleBounds.value?.let { scheduleBeaconFetchForBounds(it, debounceMs = 0L) }
    prefetchDiscoveryProximityData(showPulse = false, markInitialComplete = false)
}
