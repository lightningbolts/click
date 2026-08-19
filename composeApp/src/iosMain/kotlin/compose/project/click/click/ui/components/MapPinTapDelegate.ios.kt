@file:Suppress(
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.datetime.Clock
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKFeatureDisplayPriorityRequired
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKMarkerAnnotationView
import platform.MapKit.MKPointAnnotation
import platform.MapKit.MKUserLocation
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.darwin.NSObject
import kotlin.collections.filterIsInstance
import kotlin.math.abs
import kotlin.math.ln

private fun approximateZoomLevel(
    latitudeDelta: Double,
    longitudeDelta: Double,
): Double {
    val zoomCandidates = mutableListOf<Double>()

    val safeLon = longitudeDelta.takeIf { it.isFinite() && it > 0.0 && it <= 180.0 }
    val safeLat = latitudeDelta.takeIf { it.isFinite() && it > 0.0 && it <= 180.0 }

    if (safeLon != null) {
        zoomCandidates += ln(360.0 / safeLon) / ln(2.0)
    }
    if (safeLat != null) {
        zoomCandidates += ln(180.0 / safeLat) / ln(2.0)
    }

    val raw =
        when {
            zoomCandidates.isEmpty() -> 10.0
            else -> zoomCandidates.average()
        }.coerceIn(2.0, 20.0)

    return ((raw * 100.0).toInt()) / 100.0
}

internal data class ProgrammaticCameraTarget(
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
    val elapsedMs =
        if (startedAtMs > 0L) {
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
        center.latitude.isFinite() &&
            center.longitude.isFinite() &&
            latD.isFinite() &&
            lonD.isFinite() &&
            latD > 1e-8 &&
            lonD > 1e-8 &&
            latD <= 160.0 &&
            lonD <= 340.0 &&
            abs(center.latitude) <= 90.0 &&
            abs(center.longitude) <= 180.0
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
internal class MapPinTapDelegate :
    NSObject(),
    MKMapViewDelegateProtocol {
    var pinEntries: List<Pair<MKPointAnnotation, MapPin>> = emptyList()
    var clusterEntries: List<Pair<MKPointAnnotation, MapClusterPin>> = emptyList()

    /** Loaded connection/event avatar photos keyed by image URL. */
    val avatarImages: MutableMap<String, UIImage> = mutableMapOf()

    /** URLs currently downloading — avoids duplicate prefetch storms. */
    val avatarLoadInFlight: MutableSet<String> = mutableSetOf()

    /** Rendered circular pin bitmaps keyed by pin id + photo/initials signature. */
    val avatarPinImageCache: MutableMap<String, UIImage> = mutableMapOf()
    var onPin: (MapPin) -> Unit = {}
    var onCluster: (MapClusterPin) -> Unit = {}
    var pendingProgrammaticCamera: ProgrammaticCameraTarget? = null
    var programmaticCameraStartedAtMs: Long = 0L
    var onProgrammaticCameraSettled: () -> Unit = {}
    var onVisibleBoundsChanged: (minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) -> Unit =
        { _, _, _, _ -> }
    var onZoomChanged: (Double) -> Unit = {}
    var onMapGesture: () -> Unit = {}

    /** Weak map handle so avatar loads can refresh annotation views. */
    var mapViewRef: MKMapView? = null

    private var lastViewportMinLat: Double? = null
    private var lastViewportMaxLat: Double? = null
    private var lastViewportMinLon: Double? = null
    private var lastViewportMaxLon: Double? = null
    private var lastViewportZoom: Double? = null

    private fun dispatchViewportIfChanged(mapView: MKMapView) {
        if (!mapRegionLooksUsable(mapView)) return
        val (minLat, maxLat, minLon, maxLon, zoomLevel) =
            mapView.region.useContents {
                val minLatValue = center.latitude - (span.latitudeDelta / 2.0)
                val maxLatValue = center.latitude + (span.latitudeDelta / 2.0)
                val minLonValue = center.longitude - (span.longitudeDelta / 2.0)
                val maxLonValue = center.longitude + (span.longitudeDelta / 2.0)
                val zoomValue =
                    approximateZoomLevel(
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

    private fun findMapCluster(ann: MKPointAnnotation): MapClusterPin? {
        val byRef = clusterEntries.firstOrNull { (a, _) -> a === ann }?.second
        if (byRef != null) return byRef
        val sub = (ann.subtitle as? String)?.trim().orEmpty()
        if (sub.isNotEmpty() && sub.startsWith("cluster:")) {
            val id = sub.removePrefix("cluster:").trim()
            clusterEntries.firstOrNull { it.second.id == id }?.second?.let { return it }
        }
        if (sub.isNotEmpty() && !sub.startsWith("cluster:")) {
            return null
        }
        return ann.coordinate.useContents {
            val aLat = latitude
            val aLon = longitude
            clusterEntries
                .firstOrNull { (_, c) ->
                    abs(c.latitude - aLat) < 1.2e-4 && abs(c.longitude - aLon) < 1.2e-4
                }?.second
        }
    }

    private fun findMapPin(ann: MKPointAnnotation): MapPin? {
        val byRef = pinEntries.firstOrNull { (a, _) -> a === ann }?.second
        if (byRef != null) return byRef
        val sub = (ann.subtitle as? String)?.trim().orEmpty()
        if (sub.isNotEmpty()) {
            if (sub.startsWith("cluster:")) return null
            pinEntries.firstOrNull { it.second.id == sub }?.second?.let { return it }
        }
        return ann.coordinate.useContents {
            val aLat = latitude
            val aLon = longitude
            pinEntries
                .firstOrNull { (_, p) ->
                    abs(p.latitude - aLat) < 1.2e-4 && abs(p.longitude - aLon) < 1.2e-4
                }?.second
        }
    }

    private fun dispatchByIdentifier(
        rawIdentifier: String?,
        mapView: MKMapView?,
    ): Boolean {
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

        val selected =
            mapView
                ?.selectedAnnotations
                ?.filterIsInstance<MKPointAnnotation>()
                ?.firstOrNull { it.subtitle == rawIdentifier || it.title == rawIdentifier }
        if (selected != null) {
            mapView.deselectAnnotation(selected, animated = true)
        }
        return false
    }

    private fun dispatch(
        annotation: Any?,
        mapView: MKMapView?,
    ) {
        if (annotation is MKUserLocation) return
        val pointAnnotation = annotation as? MKPointAnnotation ?: return
        val cluster = findMapCluster(pointAnnotation)
        if (cluster != null) {
            onCluster(cluster)
            mapView?.deselectAnnotation(pointAnnotation, animated = true)
            return
        }
        val pin = findMapPin(pointAnnotation)
        if (pin != null) {
            onPin(pin)
            mapView?.deselectAnnotation(pointAnnotation, animated = true)
        }
    }

    /**
     * MapKit fires `mapView:didSelectAnnotationView:` on tap (still supported on iOS 13+).
     * This is the canonical pathway that runs `onPinTapped` per the C12 directive.
     */
    override fun mapView(
        mapView: MKMapView,
        didSelectAnnotationView: MKAnnotationView,
    ) {
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
    override fun mapView(
        mapView: MKMapView,
        didSelectAnnotation: MKAnnotationProtocol,
    ) {
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
    override fun mapView(
        mapView: MKMapView,
        viewForAnnotation: MKAnnotationProtocol,
    ): MKAnnotationView? {
        if (viewForAnnotation is MKUserLocation) return null
        val pointAnn = viewForAnnotation as? MKPointAnnotation ?: return null
        // MapKit often hands a *different* MKPointAnnotation instance than the one we added, so
        // pointer identity fails. Resolve by subtitle (pin id) and then by coordinate.
        val cluster = findMapCluster(pointAnn)
        val pin = if (cluster == null) findMapPin(pointAnn) else null
        val identifier =
            when {
                cluster != null -> "C|${cluster.id}"
                pin != null -> "P|${pin.id}"
                else -> "X|orphan"
            }
        when {
            cluster != null -> {
                val reused = mapView.dequeueReusableAnnotationViewWithIdentifier(identifier)
                val view = reused ?: MKAnnotationView(annotation = viewForAnnotation, reuseIdentifier = identifier)
                view.annotation = viewForAnnotation
                view.canShowCallout = false
                view.setEnabled(true)
                view.setSelected(false, animated = false)
                val label = if (cluster.count > 99) "99+" else cluster.count.toString()
                val fill =
                    when {
                        cluster.isConnectionOnly -> UIColor.magentaColor
                        cluster.hasLiveConnections -> UIColor.blueColor
                        else -> UIColor.orangeColor
                    }
                val cacheKey = "cluster|$label|${cluster.isConnectionOnly}|${cluster.hasLiveConnections}"
                val image =
                    avatarPinImageCache.getOrPut(cacheKey) {
                        circularMapPinUIImage(
                            sizePts = 44.0,
                            fill = fill,
                            initials = label,
                            photo = null,
                        )
                    }
                view.image = image
                view.centerOffset = platform.CoreGraphics.CGPointMake(0.0, 0.0)
                view.zPriority = cluster.zIndex
                return view
            }
            pin != null -> {
                val reused = mapView.dequeueReusableAnnotationViewWithIdentifier(identifier)
                val view = reused ?: MKAnnotationView(annotation = viewForAnnotation, reuseIdentifier = identifier)
                view.annotation = viewForAnnotation
                view.canShowCallout = false
                view.setEnabled(true)
                view.setSelected(false, animated = false)
                val photo =
                    pin.imageUrl
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { avatarImages[it] }
                val initials = pin.avatarInitials.take(2).ifEmpty { "?" }
                val cacheKey =
                    "${pin.id}|${pin.imageUrl.orEmpty()}|${photo != null}|$initials|${pin.avatarFillArgb}|${pin.pinShape}|${pin.visualFillArgb2}"
                val image =
                    avatarPinImageCache.getOrPut(cacheKey) {
                        // Fixed 44pt for all pins so scrunched markers match cluster hubs.
                        shapedMapPinUIImage(
                            sizePts = 44.0,
                            shape = pin.pinShape,
                            fill = pin.markerTintUIColor(),
                            fillSecondary = pin.visualFillArgb2?.let { argbToUIColor(it) },
                            initials = initials,
                            photo = photo,
                        )
                    }
                view.image = image
                view.centerOffset = platform.CoreGraphics.CGPointMake(0.0, 0.0)
                view.zPriority = pin.zIndex
                if (pin.kind == MapPinKind.COMMUNITY_HUB) {
                    view.displayPriority = MKFeatureDisplayPriorityRequired
                }
                return view
            }
            else -> {
                val reused = mapView.dequeueReusableAnnotationViewWithIdentifier(identifier)
                val view =
                    (reused as? MKMarkerAnnotationView)
                        ?: MKMarkerAnnotationView(annotation = viewForAnnotation, reuseIdentifier = identifier)
                view.annotation = viewForAnnotation
                view.canShowCallout = false
                view.glyphText = ""
                view.markerTintColor = UIColor.yellowColor
                view.zPriority = 0f
                return view
            }
        }
    }

    override fun mapViewDidFinishLoadingMap(mapView: MKMapView) {
        dispatchViewportIfChanged(mapView)
    }

    @kotlinx.cinterop.ObjCSignatureOverride
    override fun mapView(
        mapView: MKMapView,
        regionDidChangeAnimated: Boolean,
    ) {
        dispatchViewportIfChanged(mapView)
        maybeFinishProgrammaticCamera(mapView)
        if (pendingProgrammaticCamera == null) {
            onMapGesture()
        }
    }
}
