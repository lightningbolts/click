@file:Suppress(
    "ktlint:standard:backing-property-naming",
    "ktlint:standard:property-naming",
)

package compose.project.click.click.viewmodel // pragma: allowlist secret

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.ui.utils.BoundingBox // pragma: allowlist secret
import compose.project.click.click.ui.utils.CommunityHubPin // pragma: allowlist secret
import compose.project.click.click.ui.utils.hasUsableMapCoordinates // pragma: allowlist secret
import compose.project.click.click.ui.utils.haversineDistance // pragma: allowlist secret
import compose.project.click.click.ui.utils.mergeCommunityHubLists // pragma: allowlist secret
import compose.project.click.click.ui.utils.mergeMapBeaconLists // pragma: allowlist secret
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.serialization.json.add // pragma: allowlist secret

/** Starts (or retries) discovery hub/beacon loading when map is opened. */
internal fun MapViewModel.warmDiscoveryFeedImpl() {
    if (AppDataManager.currentUser.value == null) return
    AppDataManager.requestMapDiscoveryPrefetch()
    prefetchDiscoveryProximityData(showPulse = false, markInitialComplete = true)
}

internal fun MapViewModel.scheduleDiscoveryPrefetchRetry(delayMs: Long = 2_000L) {
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
    discoveryPrefetchRetryJob =
        viewModelScope.launch {
            delay(backoffMs)
            if (!_discoveryProximityFetchCompleted.value) {
                warmDiscoveryFeed()
            }
        }
}

internal fun MapViewModel.canEverResolveProximityCenters(): Boolean {
    if (AppDataManager.lastKnownDeviceLocation.value != null) return true
    if (AppDataManager.connections.value.any { it.connectionMapGeo() != null }) return true
    if (cachedDiscoveryAnchor() != null) return true
    if (_defaultCameraTarget.value != null || MapViewModel.lastKnownCameraTarget != null) return true
    if (_visibleBounds.value != null) return true
    // Permission alone is not enough on simulators with Location=None — avoid endless GPS retries.
    return false
}

/**
 * Non-GPS proximity center from disk-cached beacons / hubs / saved events so Nearby still
 * hydrates when the simulator (or permission) has no live fix.
 */
internal fun MapViewModel.cachedDiscoveryAnchor(): Pair<Double, Double>? {
    val coords =
        buildList {
            AppDataManager.prefetchedMapBeacons.value.forEach { b ->
                if (b.hasUsableMapCoordinates()) add(b.latitude to b.longitude)
            }
            _mapBeacons.value.forEach { b ->
                if (b.hasUsableMapCoordinates()) add(b.latitude to b.longitude)
            }
            AppDataManager.prefetchedCommunityHubs.value.forEach { h ->
                if (h.latitude.isFinite() &&
                    h.longitude.isFinite() &&
                    !(h.latitude == 0.0 && h.longitude == 0.0)
                ) {
                    add(h.latitude to h.longitude)
                }
            }
            AppDataManager.cachedEventBookmarks.value.forEach { bm ->
                val lat = bm.latitude ?: return@forEach
                val lon = bm.longitude ?: return@forEach
                if (lat.isFinite() && lon.isFinite() && !(lat == 0.0 && lon == 0.0)) {
                    add(lat to lon)
                }
            }
        }
    if (coords.isEmpty()) return null
    return coords.map { it.first }.average() to coords.map { it.second }.average()
}

internal fun MapViewModel.finishDiscoveryPrefetchAttempt() {
    markDiscoveryProximityFetchCompleted()
    _discoveryFeedLoading.value = false
    discoveryPrefetchRetryJob?.cancel()
    discoveryPrefetchRetryJob = null
}

internal fun MapViewModel.completeDiscoveryPrefetchAfterSuccess() {
    discoveryPrefetchAttempts = 0
    finishDiscoveryPrefetchAttempt()
}

internal fun MapViewModel.markDiscoveryProximityFetchCompleted() {
    _discoveryProximityFetchCompleted.value = true
}

/**
 * User-initiated discovery feed reload (pull-to-refresh or header button).
 */
internal fun MapViewModel.refreshDiscoveryFeedImpl() {
    prefetchDiscoveryProximityData(showPulse = true, markInitialComplete = true)
}

/**
 * Silent refresh after the user opens the expanded map (viewport / more detail).
 */
internal fun MapViewModel.refreshDiscoveryFromMapInteractionImpl() {
    prefetchDiscoveryProximityData(showPulse = false, markInitialComplete = false)
}

/**
 * Loads discovery hubs/beacons on first map visit if startup prefetch has not finished yet.
 */
internal fun MapViewModel.ensureDiscoveryFeedLoaded() {
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

internal fun MapViewModel.prefetchDiscoveryProximityDataImpl(
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
    discoveryProximityJob =
        viewModelScope.launch {
            var fetchRan = false
            val pulseStartedAtMs =
                if (showPulse) {
                    kotlinx.datetime.Clock.System
                        .now()
                        .toEpochMilliseconds()
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
                        val hubRows =
                            centers
                                .map { (lat, lon) ->
                                    async {
                                        val bounds = boundsAroundPoint(lat, lon, discoveryProximityRadiusMeters)
                                        mapBeaconRepository
                                            .fetchNearbyCommunityHubs(
                                                minLat = bounds.minLat,
                                                maxLat = bounds.maxLat,
                                                minLon = bounds.minLon,
                                                maxLon = bounds.maxLon,
                                            ).getOrNull()
                                            .orEmpty()
                                    }
                                }.awaitAll()
                                .flatten()
                        val incoming =
                            hubRows.map { dto ->
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
                        val beaconRows =
                            centers
                                .map { (lat, lon) ->
                                    async {
                                        val bounds = boundsAroundPoint(lat, lon, discoveryProximityRadiusMeters)
                                        mapBeaconRepository
                                            .fetchLocalBeacons(
                                                minLat = bounds.minLat,
                                                maxLat = bounds.maxLat,
                                                minLon = bounds.minLon,
                                                maxLon = bounds.maxLon,
                                                beaconTypeFilters = beaconTypesQueryForLayers(layers),
                                            ).getOrNull()
                                            .orEmpty()
                                    }
                                }.awaitAll()
                                .flatten()
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
                        val elapsed =
                            kotlinx.datetime.Clock.System
                                .now()
                                .toEpochMilliseconds() -
                                pulseStartedAtMs
                        val remaining = 520L - elapsed
                        if (remaining > 0L) delay(remaining)
                        _discoveryFeedLoading.value = false
                    }
                    if (markInitialComplete) {
                        val hasFeedData =
                            _mapBeacons.value.isNotEmpty() ||
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
internal suspend fun MapViewModel.resolveDiscoveryProximityCenters(): List<Pair<Double, Double>> {
    val raw = mutableListOf<Pair<Double, Double>>()

    AppDataManager.lastKnownDeviceLocation.value?.let { (lat, lon) ->
        raw += lat to lon
    }

    // Prefer a live/coarse GPS fix early so Android does not wait solely on map bounds.
    // Cap wait so simulators with Location=None do not block discovery for seconds.
    if (raw.isEmpty() && locationService.hasLocationPermission()) {
        val gps =
            withTimeoutOrNull(1_200L) {
                locationService.getCurrentLocation()
                    ?: locationService.getHighAccuracyLocation(1_000L)
            }
        if (gps != null) {
            AppDataManager.noteDeviceLocation(gps.latitude, gps.longitude)
            raw += gps.latitude to gps.longitude
        }
    }

    val connectionGeos = AppDataManager.connections.value.mapNotNull { it.connectionMapGeo() }
    if (connectionGeos.isNotEmpty()) {
        raw += connectionGeos.map { it.lat }.average() to connectionGeos.map { it.lon }.average()
    }

    cachedDiscoveryAnchor()?.let { raw += it }

    listOfNotNull(
        _cameraTarget.value?.let { it.latitude to it.longitude },
        MapViewModel.lastKnownCameraTarget?.let { it.latitude to it.longitude },
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

    return dedupeProximityCenters(raw)
}

/** Skip redundant fetches when GPS and map camera are essentially the same point. */
internal fun MapViewModel.dedupeProximityCenters(centers: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
    if (centers.isEmpty()) return emptyList()
    val out = mutableListOf<Pair<Double, Double>>()
    for ((lat, lon) in centers) {
        val duplicate =
            out.any { (existingLat, existingLon) ->
                haversineDistance(existingLat, existingLon, lat, lon) < 2_000.0
            }
        if (!duplicate) out += lat to lon
    }
    return out
}

internal suspend fun MapViewModel.refreshBeaconsAfterDrop(
    latitude: Double,
    longitude: Double,
    confirmedBeacon: MapBeacon,
) {
    val bounds =
        _visibleBounds.value
            ?: boundsAroundPoint(latitude, longitude, discoveryProximityRadiusMeters)
    mapBeaconRepository
        .fetchLocalBeacons(
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

internal fun MapViewModel.fetchProximityLayersForBounds(
    bounds: BoundingBox,
    debounceMs: Long,
    jobSlot: MapViewModel.DiscoveryFetchSlot,
) {
    if (AppDataManager.currentUser.value == null) return

    val seq =
        when (jobSlot) {
            MapViewModel.DiscoveryFetchSlot.MapViewport -> {
                beaconPollJob?.cancel()
                ++beaconFetchSeq
            }
            MapViewModel.DiscoveryFetchSlot.Discovery -> ++discoveryFetchSeq
        }

    val job =
        viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            when (jobSlot) {
                MapViewModel.DiscoveryFetchSlot.MapViewport -> if (seq != beaconFetchSeq) return@launch
                MapViewModel.DiscoveryFetchSlot.Discovery -> if (seq != discoveryFetchSeq) return@launch
            }
            val layers = _selectedLayerFilters.value
            val wantHubs = layersWantHubFetch(layers)
            val wantBeacons = layersWantBeaconFetch(layers)
            if (!wantHubs && !wantBeacons) return@launch

            coroutineScope {
                val hubsDeferred =
                    if (wantHubs) {
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
                val beaconsDeferred =
                    if (wantBeacons) {
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
        MapViewModel.DiscoveryFetchSlot.MapViewport -> beaconPollJob = job
        MapViewModel.DiscoveryFetchSlot.Discovery -> {
            discoveryProximityJob?.cancel()
            discoveryProximityJob = job
        }
    }
}
