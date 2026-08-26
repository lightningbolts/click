@file:Suppress(
    "ktlint:standard:backing-property-naming",
    "ktlint:standard:property-naming",
)

package compose.project.click.click.viewmodel // pragma: allowlist secret

import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.ui.utils.BoundingBox // pragma: allowlist secret
import compose.project.click.click.ui.utils.calculateZoomForBounds // pragma: allowlist secret
import kotlinx.datetime.Clock
import kotlin.math.abs
import kotlin.math.pow

internal fun MapViewModel.zoomForClusteringRender(zoom: Double): Double {
    val floor = _pinRenderZoomFloor.value
    if (floor != null) return maxOf(zoom, floor)
    if (_stickyPinMode.value && zoom >= pinModeExitBelow) {
        return maxOf(zoom, clusterThreshold)
    }
    return zoom
}

internal fun MapViewModel.updateStickyPinModeForZoom(zoom: Double) {
    when {
        zoom >= clusterThreshold -> _stickyPinMode.value = true
        zoom < pinModeExitBelow -> {
            _stickyPinMode.value = false
            _pinRenderZoomFloor.value = null
        }
    }
}

/**
 * Computes a one-time default camera. Prefers the user's GPS fix; falls back to connection bounds.
 */
internal fun MapViewModel.ensureDefaultCameraTarget(connections: List<Connection>) {
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

    if (valid.isEmpty()) {
        cachedDiscoveryAnchor()?.let { (lat, lon) ->
            val target = CameraTarget(latitude = lat, longitude = lon, zoom = 12.0)
            _defaultCameraTarget.value = target
            if (_cameraTarget.value == null) {
                _cameraTarget.value = target
            }
            prefetchDiscoveryProximityData(showPulse = false, markInitialComplete = false)
        }
        return
    }

    val minLat = valid.minOf { it.second.lat }
    val maxLat = valid.maxOf { it.second.lat }
    val minLon = valid.minOf { it.second.lon }
    val maxLon = valid.maxOf { it.second.lon }

    val bounds = BoundingBox(minLat = minLat, maxLat = maxLat, minLon = minLon, maxLon = maxLon)
    val targetZoom = calculateZoomForBounds(bounds).coerceIn(4.0, 16.0)

    val computedTarget =
        CameraTarget(
            latitude = bounds.centerLat,
            longitude = bounds.centerLon,
            zoom = targetZoom,
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
internal fun MapViewModel.updateMapDeviceLocationImpl(
    latitude: Double,
    longitude: Double,
) {
    if (!latitude.isFinite() || !longitude.isFinite()) return
    if (latitude == 0.0 && longitude == 0.0) return
    AppDataManager.noteDeviceLocation(latitude, longitude)
    if (AppDataManager.ghostModeEnabled.value) return
    if (!locationService.hasLocationPermission()) return

    val target =
        CameraTarget(
            latitude = latitude,
            longitude = longitude,
            zoom = MapViewModel.DEFAULT_USER_MAP_ZOOM,
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
                _zoomLevel.value = MapViewModel.DEFAULT_USER_MAP_ZOOM
            }
        }
    }
}

internal fun MapViewModel.deviceLocationCameraTarget(): CameraTarget? {
    if (!locationService.hasLocationPermission()) return null
    val loc = AppDataManager.lastKnownDeviceLocation.value ?: return null
    return CameraTarget(
        latitude = loc.first,
        longitude = loc.second,
        zoom = MapViewModel.DEFAULT_USER_MAP_ZOOM,
    )
}

/**
 * Update the current zoom level
 */
internal fun MapViewModel.setZoomLevelImpl(zoom: Double) {
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
            zoom = coerced,
        )
    }
}

/**
 * Update visible bounds from outside (e.g., the platform map callback)
 */
internal fun MapViewModel.updateVisibleBoundsImpl(
    minLat: Double,
    maxLat: Double,
    minLon: Double,
    maxLon: Double,
) {
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
        zoom = _zoomLevel.value,
    )
    scheduleBeaconFetchForBounds(bounds)
}

internal fun MapViewModel.scheduleBeaconFetchForBounds(
    bounds: BoundingBox,
    debounceMs: Long = 400L,
) {
    fetchProximityLayersForBounds(bounds, debounceMs, MapViewModel.DiscoveryFetchSlot.MapViewport)
}

internal fun MapViewModel.persistCameraTarget(
    latitude: Double,
    longitude: Double,
    zoom: Double,
) {
    if (!latitude.isFinite() || !longitude.isFinite() || !zoom.isFinite()) return
    val z = zoom.coerceIn(2.0, 20.0)
    // Never persist continent/world scale; it becomes the next session's "restore camera".
    if (z < 4.0) return

    val candidate =
        CameraTarget(
            latitude = latitude,
            longitude = longitude,
            zoom = z,
        )

    val previous = MapViewModel.lastKnownCameraTarget
    val changed =
        previous == null ||
            abs(previous.latitude - candidate.latitude) > 0.000001 ||
            abs(previous.longitude - candidate.longitude) > 0.000001 ||
            abs(previous.zoom - candidate.zoom) > 0.01

    if (changed) {
        MapViewModel.lastKnownCameraTarget = candidate
    }
}

/**
 * Estimate visible bounds from zoom level and camera target.
 * This is used as a fallback when the platform map doesn't report bounds.
 */
internal fun MapViewModel.estimateVisibleBounds() {
    fun validConnections(): List<Connection> {
        val state = _mapState.value
        if (state !is MapState.Success) return emptyList()
        return state.connections.filter {
            val g = it.connectionMapGeo()
            g != null && g.lat.isFinite() && g.lon.isFinite() && !(g.lat == 0.0 && g.lon == 0.0)
        }
    }

    val center = _cameraTarget.value
    val centerLat =
        center?.latitude ?: run {
            val connections = validConnections()
            if (connections.isNotEmpty()) {
                connections.mapNotNull { it.connectionMapGeo()?.lat }.average()
            } else {
                return
            }
        }
    val centerLon =
        center?.longitude ?: run {
            val connections = validConnections()
            if (connections.isNotEmpty()) {
                connections.mapNotNull { it.connectionMapGeo()?.lon }.average()
            } else {
                return
            }
        }

    // Estimate viewport span based on zoom level
    // At zoom 10, ~30 miles visible; at zoom 16, ~0.5 miles
    val latSpan = 180.0 / 2.0.pow(_zoomLevel.value - 1)
    val lonSpan = 360.0 / 2.0.pow(_zoomLevel.value - 1)

    _visibleBounds.value =
        BoundingBox(
            minLat = centerLat - latSpan / 2,
            maxLat = centerLat + latSpan / 2,
            minLon = centerLon - lonSpan / 2,
            maxLon = centerLon + lonSpan / 2,
        )
}

internal fun MapViewModel.anchorLatLonForProgrammaticCamera(): Pair<Double, Double>? {
    MapViewModel.lastKnownCameraTarget?.let { return it.latitude to it.longitude }
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
internal fun MapViewModel.zoomInImpl() {
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
internal fun MapViewModel.zoomOutImpl() {
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
 * Clear camera target after animation completes
 */
internal fun MapViewModel.onCameraAnimationCompleteImpl() {
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

internal fun MapViewModel.boundsAroundPoint(
    lat: Double,
    lon: Double,
    radiusMeters: Double,
): BoundingBox {
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
