package compose.project.click.click.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import compose.project.click.click.ui.utils.TimeState
import kotlinx.datetime.Clock
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKCircle
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKMarkerAnnotationView
import platform.MapKit.MKPointAnnotation
import platform.MapKit.MKUserLocation
import platform.MapKit.MKUserTrackingModeNone
import platform.MapKit.MKStandardMapConfiguration
import platform.MapKit.MKMapElevationStyleFlat
import platform.UIKit.UIColor
import platform.UIKit.UIUserInterfaceStyle
import platform.darwin.NSObject
import kotlin.collections.filterIsInstance
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformMap(
    modifier: Modifier,
    pins: List<MapPin>,
    clusters: List<MapClusterPin>,
    zoom: Double,
    centerLat: Double?,
    centerLon: Double?,
    ghostMode: Boolean,
    onPinTapped: (MapPin) -> Unit,
    onClusterTapped: (MapClusterPin) -> Unit,
    onZoomChanged: (Double) -> Unit,
    onVisibleBoundsChanged: (minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) -> Unit,
    onCameraAnimationComplete: () -> Unit
) {
    var lastPinsHash by remember { mutableStateOf(pins.hashCode() + clusters.hashCode()) }
    var hasCentered by remember { mutableStateOf(false) }
    var lastAppliedTargetLat by remember { mutableStateOf<Double?>(null) }
    var lastAppliedTargetLon by remember { mutableStateOf<Double?>(null) }
    var lastAppliedTargetZoom by remember { mutableStateOf<Double?>(null) }

    // C12: MKMapViewDelegate bridge so iOS pin taps reach the shared ProfileBottomSheet
    // flow through the same `onPinTapped` / `onClusterTapped` callbacks as Android.
    // We keep the delegate stable across recompositions and refresh its callback /
    // lookup references in `update` — MapKit fires `mapView:didSelectAnnotationView:`
    // on tap, and we resolve the annotation back to its MapPin / MapClusterPin by
    // object identity against the lists we just added.
    val pinTapDelegate = remember { MapPinTapDelegate() }
    pinTapDelegate.onPin = onPinTapped
    pinTapDelegate.onCluster = onClusterTapped
    pinTapDelegate.onProgrammaticCameraSettled = onCameraAnimationComplete
    pinTapDelegate.onVisibleBoundsChanged = onVisibleBoundsChanged
    pinTapDelegate.onZoomChanged = onZoomChanged

    UIKitView(
        modifier = modifier,
        factory = {
            MKMapView().apply {
                delegate = pinTapDelegate
                showsCompass = true
                showsScale = false
                zoomEnabled = true
                scrollEnabled = true
                showsUserLocation = !ghostMode
                userTrackingMode = MKUserTrackingModeNone
                
                // Enable dark mode for the map
                overrideUserInterfaceStyle = UIUserInterfaceStyle.UIUserInterfaceStyleDark
                
                // Use flat elevation for cleaner dark appearance
                preferredConfiguration = MKStandardMapConfiguration().apply {
                    elevationStyle = MKMapElevationStyleFlat
                }
            }
        },
        update = { map ->
            // Update user location visibility based on ghost mode
            map.showsUserLocation = !ghostMode
            if (map.delegate !== pinTapDelegate) {
                map.delegate = pinTapDelegate
            }
            
            val currentHash = pins.hashCode() + clusters.hashCode()
            
            // Handle Pins and Clusters
            if (currentHash != lastPinsHash || !hasCentered) {
                // Replacing all annotations can reset the visible region on some MapKit versions;
                // preserve the user's viewport when we are not in the middle of a VM-driven camera target.
                val savedRegion = if (hasCentered && centerLat == null && centerLon == null) {
                    map.region
                } else {
                    null
                }
                // Remove existing annotations (except user location)
                val existingAnnotations = map.annotations.filterIsInstance<MKPointAnnotation>()
                if (existingAnnotations.isNotEmpty()) {
                    map.removeAnnotations(existingAnnotations)
                }

                val pinEntries = mutableListOf<Pair<MKPointAnnotation, MapPin>>()
                val clusterEntries = mutableListOf<Pair<MKPointAnnotation, MapClusterPin>>()

                // Add individual pins with color based on time state
                pins.forEach { pin ->
                    val ann = MKPointAnnotation()
                    val displayTitle = when (pin.timeState) {
                        TimeState.LIVE -> "🔵 ${pin.title}"
                        TimeState.RECENT -> "💠 ${pin.title}"
                        TimeState.ARCHIVE -> "⚪ ${pin.title}"
                    }
                    ann.setTitle(displayTitle)
                    ann.setSubtitle(pin.id)
                    ann.setCoordinate(CLLocationCoordinate2DMake(pin.latitude, pin.longitude))
                    map.addAnnotation(ann)
                    pinEntries += ann to pin
                }

                // Add cluster pins
                clusters.forEach { cluster ->
                    val ann = MKPointAnnotation()
                    val icon = if (cluster.hasLiveConnections) "🔵" else "⭕"
                    ann.setTitle("$icon ${cluster.count} memories")
                    ann.setSubtitle("cluster:${cluster.id}")
                    ann.setCoordinate(CLLocationCoordinate2DMake(cluster.latitude, cluster.longitude))
                    map.addAnnotation(ann)
                    clusterEntries += ann to cluster
                }

                pinTapDelegate.pinEntries = pinEntries
                pinTapDelegate.clusterEntries = clusterEntries

                if (savedRegion != null && centerLat == null && centerLon == null) {
                    map.setRegion(savedRegion, false)
                }

                // Initial centering
                if (!hasCentered) {
                    val initialTarget = when {
                        centerLat != null && centerLon != null -> Pair(centerLat, centerLon)
                        else -> computeDataCenter(pins, clusters)
                    }

                    if (initialTarget != null) {
                        val (targetLat, targetLon) = initialTarget
                        val meters = metersForZoom(zoom)
                        val center = CLLocationCoordinate2DMake(targetLat, targetLon)
                        val region = MKCoordinateRegionMakeWithDistance(center, meters, meters)
                        map.setRegion(map.regionThatFits(region), false)
                        lastAppliedTargetLat = targetLat
                        lastAppliedTargetLon = targetLon
                        lastAppliedTargetZoom = zoom
                        hasCentered = true
                    }
                }
                lastPinsHash = currentHash
            }

            // Handle camera target animation
            if (centerLat != null && centerLon != null) {
                val centerChanged =
                    !approximatelyEqual(lastAppliedTargetLat, centerLat, epsilon = 0.000001) ||
                    !approximatelyEqual(lastAppliedTargetLon, centerLon, epsilon = 0.000001) ||
                    !approximatelyEqual(lastAppliedTargetZoom, zoom, epsilon = 0.01)

                if (centerChanged) {
                    pinTapDelegate.pendingProgrammaticCamera =
                        ProgrammaticCameraTarget(centerLat, centerLon, zoom)
                    pinTapDelegate.programmaticCameraStartedAtMs =
                        Clock.System.now().toEpochMilliseconds()
                    val meters = metersForZoom(zoom)
                    val center = CLLocationCoordinate2DMake(centerLat, centerLon)
                    val region = MKCoordinateRegionMakeWithDistance(center, meters, meters)
                    map.setRegion(map.regionThatFits(region), true)
                    lastAppliedTargetLat = centerLat
                    lastAppliedTargetLon = centerLon
                    lastAppliedTargetZoom = zoom
                }
            }
            // When centerLat/centerLon are null the *map* owns pinch / double-tap zoom. Pushing
            // setRegion from ViewModel zoom here created a feedback loop with regionDidChange →
            // onZoomChanged → _zoomLevel → update → setRegion, and bogus span reads produced
            // continent-scale jumps. Programmatic zoom (buttons, cluster) always supplies a target center.
        }
    )
}

private fun computeDataCenter(pins: List<MapPin>, clusters: List<MapClusterPin>): Pair<Double, Double>? {
    if (pins.isNotEmpty()) {
        val minLat = pins.minOf { it.latitude }
        val maxLat = pins.maxOf { it.latitude }
        val minLon = pins.minOf { it.longitude }
        val maxLon = pins.maxOf { it.longitude }
        return Pair((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0)
    }
    if (clusters.isNotEmpty()) {
        val minLat = clusters.minOf { it.latitude }
        val maxLat = clusters.maxOf { it.latitude }
        val minLon = clusters.minOf { it.longitude }
        val maxLon = clusters.maxOf { it.longitude }
        return Pair((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0)
    }
    return null
}

private fun approximateZoomLevel(latitudeDelta: Double, longitudeDelta: Double): Double {
    val zoomCandidates = mutableListOf<Double>()

    val safeLon = longitudeDelta.takeIf { it.isFinite() && it > 0.0 && it <= 180.0 }
    val safeLat = latitudeDelta.takeIf { it.isFinite() && it > 0.0 && it <= 180.0 }

    if (safeLon != null) {
        zoomCandidates += ln(360.0 / safeLon) / ln(2.0)
    }
    if (safeLat != null) {
        zoomCandidates += ln(180.0 / safeLat) / ln(2.0)
    }

    val raw = when {
        zoomCandidates.isEmpty() -> 10.0
        else -> zoomCandidates.average()
    }.coerceIn(2.0, 20.0)

    return ((raw * 100.0).toInt()) / 100.0
}

private fun approximatelyEqual(previous: Double?, current: Double, epsilon: Double = 0.00001): Boolean {
    return previous != null && kotlin.math.abs(previous - current) <= epsilon
}

private data class ProgrammaticCameraTarget(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
)

@OptIn(ExperimentalForeignApi::class)
private fun mapRegionMatchesProgrammaticTarget(
    map: MKMapView,
    target: ProgrammaticCameraTarget,
    startedAtMs: Long,
): Boolean {
    val elapsedMs = if (startedAtMs > 0L) {
        Clock.System.now().toEpochMilliseconds() - startedAtMs
    } else {
        0L
    }
    val relaxZoomMatch = elapsedMs > 900L
    return map.region.useContents {
        val z = approximateZoomLevel(span.latitudeDelta, span.longitudeDelta)
        val centerOk =
            abs(center.latitude - target.latitude) < 0.00025 &&
                abs(center.longitude - target.longitude) < 0.00025
        val zoomOk = abs(z - target.zoom) < 2.5
        centerOk && (zoomOk || relaxZoomMatch)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun mapRegionLooksUsable(map: MKMapView): Boolean =
    map.region.useContents {
        val latD = span.latitudeDelta
        val lonD = span.longitudeDelta
        center.latitude.isFinite() && center.longitude.isFinite() &&
            latD.isFinite() && lonD.isFinite() &&
            latD > 1e-8 && lonD > 1e-8 &&
            latD <= 160.0 && lonD <= 340.0 &&
            abs(center.latitude) <= 90.0 && abs(center.longitude) <= 180.0
    }

/**
 * Bridges `MKMapViewDelegate` pin-tap callbacks back into the Kotlin `onPinTapped` /
 * `onClusterTapped` lambdas (C12). Identity-based lookup because MKPointAnnotation does
 * not carry an app-level id; the list is small (viewport-bound) so O(n) is fine and
 * robust against coordinate-rounding collisions. Always deselects after dispatch so a
 * repeated tap on the same pin re-fires the selection.
 *
 * Implementation notes:
 *  * Overrides `viewForAnnotation` so every annotation gets a reusable, explicitly
 *    [MKMarkerAnnotationView] that is guaranteed `canShowCallout = false` but still
 *    selectable. This is the pathway that actually produces reliable tap semantics
 *    under Compose Multiplatform's `UIKitView` touch interop — the default annotation
 *    pipeline can drop selection events when the map is hosted inside a Compose
 *    interop container.
 *  * Keeps stable references so we can dispatch taps even if MapKit's `didSelect`
 *    callback is starved (see the gesture-recognizer fallback attached in
 *    `viewForAnnotation`).
 *  * Implements both the deprecated `mapView:didSelectAnnotationView:` and the
 *    iOS 16+ `mapView:didSelectAnnotation:` selectors so pin taps bubble up regardless
 *    of the deployment target MapKit settled on.
 */
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
private class MapPinTapDelegate : NSObject(), MKMapViewDelegateProtocol {
    var pinEntries: List<Pair<MKPointAnnotation, MapPin>> = emptyList()
    var clusterEntries: List<Pair<MKPointAnnotation, MapClusterPin>> = emptyList()
    var onPin: (MapPin) -> Unit = {}
    var onCluster: (MapClusterPin) -> Unit = {}
    var pendingProgrammaticCamera: ProgrammaticCameraTarget? = null
    var programmaticCameraStartedAtMs: Long = 0L
    var onProgrammaticCameraSettled: () -> Unit = {}
    var onVisibleBoundsChanged: (minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) -> Unit =
        { _, _, _, _ -> }
    var onZoomChanged: (Double) -> Unit = {}

    private var lastViewportMinLat: Double? = null
    private var lastViewportMaxLat: Double? = null
    private var lastViewportMinLon: Double? = null
    private var lastViewportMaxLon: Double? = null
    private var lastViewportZoom: Double? = null

    private fun dispatchViewportIfChanged(mapView: MKMapView) {
        if (!mapRegionLooksUsable(mapView)) return
        val (minLat, maxLat, minLon, maxLon, zoomLevel) = mapView.region.useContents {
            val minLatValue = center.latitude - (span.latitudeDelta / 2.0)
            val maxLatValue = center.latitude + (span.latitudeDelta / 2.0)
            val minLonValue = center.longitude - (span.longitudeDelta / 2.0)
            val maxLonValue = center.longitude + (span.longitudeDelta / 2.0)
            val zoomValue = approximateZoomLevel(
                latitudeDelta = span.latitudeDelta,
                longitudeDelta = span.longitudeDelta,
            )
            listOf(minLatValue, maxLatValue, minLonValue, maxLonValue, zoomValue)
        }

        val boundsChanged =
            !approximatelyEqual(lastViewportMinLat, minLat) ||
            !approximatelyEqual(lastViewportMaxLat, maxLat) ||
            !approximatelyEqual(lastViewportMinLon, minLon) ||
            !approximatelyEqual(lastViewportMaxLon, maxLon)
        if (boundsChanged) {
            onVisibleBoundsChanged(minLat, maxLat, minLon, maxLon)
            lastViewportMinLat = minLat
            lastViewportMaxLat = maxLat
            lastViewportMinLon = minLon
            lastViewportMaxLon = maxLon
        }

        if (!approximatelyEqual(lastViewportZoom, zoomLevel, epsilon = 0.05)) {
            onZoomChanged(zoomLevel)
            lastViewportZoom = zoomLevel
        }
    }

    private fun maybeFinishProgrammaticCamera(mapView: MKMapView) {
        val pending = pendingProgrammaticCamera ?: return
        if (!mapRegionLooksUsable(mapView)) return
        val now = Clock.System.now().toEpochMilliseconds()
        if (programmaticCameraStartedAtMs > 0L && now - programmaticCameraStartedAtMs > 3000L) {
            pendingProgrammaticCamera = null
            programmaticCameraStartedAtMs = 0L
            onProgrammaticCameraSettled()
            return
        }
        if (mapRegionMatchesProgrammaticTarget(mapView, pending, programmaticCameraStartedAtMs)) {
            pendingProgrammaticCamera = null
            programmaticCameraStartedAtMs = 0L
            onProgrammaticCameraSettled()
        }
    }

    private fun dispatchByIdentifier(rawIdentifier: String?, mapView: MKMapView?): Boolean {
        val identifier = rawIdentifier?.trim().orEmpty()
        if (identifier.isEmpty()) return false

        val clusterId = identifier.removePrefix("cluster:")
        if (identifier.startsWith("cluster:")) {
            val cluster = clusterEntries.firstOrNull { it.second.id == clusterId }?.second
            if (cluster != null) {
                onCluster(cluster)
                return true
            }
        }

        val pin = pinEntries.firstOrNull { it.second.id == identifier }?.second
        if (pin != null) {
            onPin(pin)
            return true
        }

        val cluster = clusterEntries.firstOrNull { it.second.id == identifier }?.second
        if (cluster != null) {
            onCluster(cluster)
            return true
        }

        val selected = mapView?.selectedAnnotations
            ?.filterIsInstance<MKPointAnnotation>()
            ?.firstOrNull { it.subtitle == rawIdentifier || it.title == rawIdentifier }
        if (selected != null) {
            mapView.deselectAnnotation(selected, animated = true)
        }
        return false
    }

    private fun dispatch(annotation: Any?, mapView: MKMapView?) {
        if (annotation is MKUserLocation) return
        val pointAnnotation = annotation as? MKPointAnnotation ?: return
        val pin = pinEntries.firstOrNull { it.first === pointAnnotation }?.second
        if (pin != null) {
            onPin(pin)
            mapView?.deselectAnnotation(pointAnnotation, animated = true)
            return
        }
        val cluster = clusterEntries.firstOrNull { it.first === pointAnnotation }?.second
        if (cluster != null) {
            onCluster(cluster)
            mapView?.deselectAnnotation(pointAnnotation, animated = true)
        }
    }

    /**
     * MapKit fires `mapView:didSelectAnnotationView:` on tap (still supported on iOS 13+).
     * This is the canonical pathway that runs `onPinTapped` per the C12 directive.
     */
    override fun mapView(mapView: MKMapView, didSelectAnnotationView: MKAnnotationView) {
        val annotation = didSelectAnnotationView.annotation as? MKPointAnnotation
        val identifier = annotation?.subtitle ?: annotation?.title
        if (dispatchByIdentifier(identifier, mapView)) {
            annotation?.let { mapView.deselectAnnotation(it, animated = true) }
            return
        }
        dispatch(didSelectAnnotationView.annotation, mapView)
    }

    /**
     * iOS 11+ `mapView:didSelectAnnotation:` selector (Swift signature
     * `mapView(_:didSelect:)`). Implemented in addition to the deprecated
     * `didSelectAnnotationView:` so the pin-tap → ProfileBottomSheet flow keeps working
     * even when MapKit decides to dispatch only the modern callback under Compose
     * Multiplatform's `UIKitView` interop. `@ObjCSignatureOverride` is required because
     * both selectors collapse to the same Kotlin name `mapView(MKMapView, ...)`.
     */
    @kotlinx.cinterop.ObjCSignatureOverride
    override fun mapView(mapView: MKMapView, didSelectAnnotation: MKAnnotationProtocol) {
        dispatch(didSelectAnnotation, mapView)
    }

    /**
     * Explicitly return a selectable [MKMarkerAnnotationView] for every annotation. This
     * covers the Compose interop case where the implicit MapKit pipeline silently
     * declines to create selectable views, which in turn starved the `didSelect` callback.
     * `@ObjCSignatureOverride` is needed because the `didSelectAnnotation:` selector below
     * shares the same Kotlin name `mapView(MKMapView, MKAnnotationProtocol)`.
     */
    @kotlinx.cinterop.ObjCSignatureOverride
    override fun mapView(mapView: MKMapView, viewForAnnotation: MKAnnotationProtocol): MKAnnotationView? {
        if (viewForAnnotation is MKUserLocation) return null
        val identifier = "ClickPinMarker"
        val reused = mapView.dequeueReusableAnnotationViewWithIdentifier(identifier)
        val view = (reused as? MKMarkerAnnotationView)
            ?: MKMarkerAnnotationView(annotation = viewForAnnotation, reuseIdentifier = identifier)
        view.annotation = viewForAnnotation
        view.canShowCallout = false
        view.setEnabled(true)
        view.setSelected(false, animated = false)
        return view
    }

    override fun mapViewDidFinishLoadingMap(mapView: MKMapView) {
        dispatchViewportIfChanged(mapView)
    }

    @kotlinx.cinterop.ObjCSignatureOverride
    override fun mapView(mapView: MKMapView, regionDidChangeAnimated: Boolean) {
        dispatchViewportIfChanged(mapView)
        maybeFinishProgrammaticCamera(mapView)
    }
}

private fun metersForZoom(zoomLevel: Double): Double {
    val maxZoom = 20.0
    val minMeters = 120.0
    val maxMeters = 4_000_000.0
    val normalized = (maxZoom - zoomLevel).coerceIn(0.0, maxZoom)
    val meters = minMeters * 2.0.pow(normalized)
    return meters.coerceIn(minMeters, maxMeters)
}
