@file:Suppress(
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import compose.project.click.click.ui.theme.LocalIsDarkMode // pragma: allowlist secret
import compose.project.click.click.ui.utils.TimeState // pragma: allowlist secret
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.datetime.Clock
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapElevationStyleFlat
import platform.MapKit.MKMapView
import platform.MapKit.MKPointAnnotation
import platform.MapKit.MKStandardMapConfiguration
import platform.MapKit.MKStandardMapEmphasisStyleMuted
import platform.MapKit.MKUserTrackingModeNone
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIUserInterfaceStyle
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import kotlin.collections.filterIsInstance
import kotlin.math.abs
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
    mapGesturesEnabled: Boolean,
    showCompass: Boolean,
    onPinTapped: (MapPin) -> Unit,
    onClusterTapped: (MapClusterPin) -> Unit,
    onZoomChanged: (Double) -> Unit,
    onVisibleBoundsChanged: (minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) -> Unit,
    onCameraAnimationComplete: () -> Unit,
    onMapGesture: () -> Unit,
) {
    var lastAnnotationSnapshot by remember { mutableStateOf("") }
    var hasCentered by remember { mutableStateOf(false) }
    var lastAppliedTargetLat by remember { mutableStateOf<Double?>(null) }
    var lastAppliedTargetLon by remember { mutableStateOf<Double?>(null) }
    var lastAppliedTargetZoom by remember { mutableStateOf<Double?>(null) }
    val isDarkMode = LocalIsDarkMode.current

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
    pinTapDelegate.onMapGesture = onMapGesture

    UIKitView(
        modifier = modifier,
        properties =
            UIKitInteropProperties(
                isInteractive = mapGesturesEnabled,
                isNativeAccessibilityEnabled = true,
            ),
        factory = {
            MKMapView().apply {
                delegate = pinTapDelegate
                showsCompass = showCompass
                showsScale = false
                zoomEnabled = mapGesturesEnabled
                scrollEnabled = mapGesturesEnabled
                userInteractionEnabled = mapGesturesEnabled
                showsUserLocation = !ghostMode
                userTrackingMode = MKUserTrackingModeNone
                preferredConfiguration =
                    MKStandardMapConfiguration().apply {
                        elevationStyle = MKMapElevationStyleFlat
                    }
            }
        },
        update = { map ->
            map.showsCompass = showCompass
            map.zoomEnabled = mapGesturesEnabled
            map.scrollEnabled = mapGesturesEnabled
            map.userInteractionEnabled = mapGesturesEnabled
            // Update user location visibility based on ghost mode
            map.showsUserLocation = !ghostMode
            // Basemap: ghost → muted; dark app → dark UI style; light → default color map.
            map.overrideUserInterfaceStyle =
                when {
                    ghostMode -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
                    isDarkMode -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
                    else -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
                }
            map.preferredConfiguration =
                MKStandardMapConfiguration().apply {
                    elevationStyle = MKMapElevationStyleFlat
                    if (ghostMode) {
                        emphasisStyle = MKStandardMapEmphasisStyleMuted
                    }
                }
            if (map.delegate !== pinTapDelegate) {
                map.delegate = pinTapDelegate
            }
            pinTapDelegate.mapViewRef = map

            // Prefetch avatar photos for circular glyph markers (background; no main-thread I/O).
            pins.forEach { pin ->
                val url = pin.imageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
                if (pinTapDelegate.avatarImages.containsKey(url)) return@forEach
                if (!pinTapDelegate.avatarLoadInFlight.add(url)) return@forEach
                val nsUrl =
                    NSURL.URLWithString(url) ?: run {
                        pinTapDelegate.avatarLoadInFlight.remove(url)
                        return@forEach
                    }
                dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
                    val data = NSData.dataWithContentsOfURL(nsUrl)
                    val image = data?.let { UIImage.imageWithData(it) }
                    dispatch_async(dispatch_get_main_queue()) {
                        pinTapDelegate.avatarLoadInFlight.remove(url)
                        if (image != null) {
                            pinTapDelegate.avatarImages[url] = image
                            // Drop cached glyphs that used this URL so they rebuild with the photo.
                            val staleKeys =
                                pinTapDelegate.avatarPinImageCache.keys.filter { key ->
                                    key.contains(url)
                                }
                            staleKeys.forEach { pinTapDelegate.avatarPinImageCache.remove(it) }
                            val mapRef = pinTapDelegate.mapViewRef ?: return@dispatch_async
                            // Update only pins that use this URL — avoid remove-all/re-add flicker.
                            pinTapDelegate.pinEntries.forEach { (ann, pin) ->
                                if (pin.imageUrl?.trim() != url) return@forEach
                                val view =
                                    mapRef.viewForAnnotation(ann) as? MKAnnotationView
                                        ?: return@forEach
                                val initials = pin.avatarInitials.take(2).ifEmpty { "?" }
                                val cacheKey =
                                    "${pin.id}|${pin.imageUrl.orEmpty()}|true|$initials|${pin.avatarFillArgb}|${pin.pinShape}|${pin.visualFillArgb2}"
                                val glyph =
                                    pinTapDelegate.avatarPinImageCache.getOrPut(cacheKey) {
                                        shapedMapPinUIImage(
                                            sizePts = 44.0,
                                            shape = pin.pinShape,
                                            fill = pin.markerTintUIColor(),
                                            fillSecondary = pin.visualFillArgb2?.let { argbToUIColor(it) },
                                            initials = initials,
                                            photo = image,
                                        )
                                    }
                                view.image = glyph
                            }
                        }
                    }
                }
            }

            val currentSnapshot = mapAnnotationSnapshot(pins, clusters)

            // Handle Pins and Clusters — incremental sync avoids remove-all/re-add flicker when
            // beacons arrive after connections are already on the map.
            if (currentSnapshot != lastAnnotationSnapshot || !hasCentered) {
                // Replacing all annotations can reset the visible region on some MapKit versions;
                // preserve the user's viewport when we are not in the middle of a VM-driven camera target.
                val savedRegion =
                    if (hasCentered && centerLat == null && centerLon == null) {
                        map.region
                    } else {
                        null
                    }
                syncMapAnnotations(map, pins, clusters, pinTapDelegate)

                if (savedRegion != null && centerLat == null && centerLon == null) {
                    map.setRegion(savedRegion, false)
                }

                // Initial centering
                if (!hasCentered) {
                    val initialTarget =
                        when {
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
                lastAnnotationSnapshot = currentSnapshot
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
        },
    )
}

private fun mapPinDisplayTitle(pin: MapPin): String =
    when (pin.kind) {
        MapPinKind.CONNECTION ->
            when (pin.timeState) {
                TimeState.LIVE -> "🔵 ${pin.title}"
                TimeState.RECENT -> "💠 ${pin.title}"
                TimeState.ARCHIVE -> "⚪ ${pin.title}"
            }
        MapPinKind.BEACON_SOUNDTRACK -> "🎵 ${pin.title}"
        MapPinKind.BEACON_ALERT -> "⚠️ ${pin.title}"
        MapPinKind.BEACON_SOCIAL -> "✨ ${pin.title}"
        MapPinKind.BEACON_OTHER -> "📍 ${pin.title}"
        MapPinKind.COMMUNITY_HUB -> "🏟️ ${pin.title}"
    }

/** Stable fingerprint so we only touch MapKit annotations when pin/cluster data actually changes. */
private fun mapAnnotationSnapshot(
    pins: List<MapPin>,
    clusters: List<MapClusterPin>,
): String {
    val pinPart =
        pins.sortedBy { it.id }.joinToString("|") { pin ->
            "${pin.id}:${pin.latitude},${pin.longitude}:${pin.title}:${pin.kind}:${pin.timeState}:${pin.avatarInitials}:${pin.imageUrl.orEmpty()}:${pin.avatarFillArgb}:${pin.pinShape}:${pin.visualFillArgb2}"
        }
    val clusterPart =
        clusters.sortedBy { it.id }.joinToString("|") { cluster ->
            "${cluster.id}:${cluster.latitude},${cluster.longitude}:${cluster.count}"
        }
    return "$pinPart#$clusterPart"
}

@OptIn(ExperimentalForeignApi::class)
private fun syncMapAnnotations(
    map: MKMapView,
    pins: List<MapPin>,
    clusters: List<MapClusterPin>,
    pinTapDelegate: MapPinTapDelegate,
) {
    val desiredPinIds = pins.associateBy { it.id }
    val desiredClusterIds = clusters.associateBy { it.id }

    val existingAnnotations = map.annotations.filterIsInstance<MKPointAnnotation>()
    val stale =
        existingAnnotations.filter { ann ->
            val sub = (ann.subtitle as? String)?.trim().orEmpty()
            when {
                sub.startsWith("cluster:") -> {
                    val id = sub.removePrefix("cluster:").trim()
                    id !in desiredClusterIds
                }
                sub.isNotEmpty() -> sub !in desiredPinIds
                else -> true
            }
        }
    if (stale.isNotEmpty()) {
        map.removeAnnotations(stale)
    }

    val presentBySubtitle = mutableMapOf<String, MKPointAnnotation>()
    map.annotations.filterIsInstance<MKPointAnnotation>().forEach { ann ->
        val sub = (ann.subtitle as? String)?.trim().orEmpty()
        if (sub.isNotEmpty()) presentBySubtitle[sub] = ann
    }

    pins.forEach { pin ->
        val existing = presentBySubtitle[pin.id]
        if (existing != null) {
            val (lat, lon) = existing.coordinate.useContents { latitude to longitude }
            if (abs(lat - pin.latitude) > 1e-7 || abs(lon - pin.longitude) > 1e-7) {
                existing.setCoordinate(CLLocationCoordinate2DMake(pin.latitude, pin.longitude))
            }
            val desiredTitle = mapPinDisplayTitle(pin)
            if (existing.title != desiredTitle) {
                existing.setTitle(desiredTitle)
            }
            // Refresh glyph in place when metadata changed (no remove/re-add).
            val view = map.viewForAnnotation(existing) as? MKAnnotationView
            if (view != null) {
                val photo =
                    pin.imageUrl
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { pinTapDelegate.avatarImages[it] }
                val initials = pin.avatarInitials.take(2).ifEmpty { "?" }
                val cacheKey =
                    "${pin.id}|${pin.imageUrl.orEmpty()}|${photo != null}|$initials|${pin.avatarFillArgb}|${pin.pinShape}|${pin.visualFillArgb2}"
                view.image =
                    pinTapDelegate.avatarPinImageCache.getOrPut(cacheKey) {
                        shapedMapPinUIImage(
                            sizePts = 44.0,
                            shape = pin.pinShape,
                            fill = pin.markerTintUIColor(),
                            fillSecondary = pin.visualFillArgb2?.let { argbToUIColor(it) },
                            initials = initials,
                            photo = photo,
                        )
                    }
                view.zPriority = pin.zIndex
            }
            return@forEach
        }
        val ann = MKPointAnnotation()
        ann.setTitle(mapPinDisplayTitle(pin))
        ann.setSubtitle(pin.id)
        ann.setCoordinate(CLLocationCoordinate2DMake(pin.latitude, pin.longitude))
        map.addAnnotation(ann)
    }

    clusters.forEach { cluster ->
        val key = "cluster:${cluster.id}"
        val existing = presentBySubtitle[key]
        if (existing != null) {
            val (lat, lon) = existing.coordinate.useContents { latitude to longitude }
            if (abs(lat - cluster.latitude) > 1e-7 || abs(lon - cluster.longitude) > 1e-7) {
                existing.setCoordinate(CLLocationCoordinate2DMake(cluster.latitude, cluster.longitude))
            }
            val desiredTitle = "${cluster.count}"
            if (existing.title != desiredTitle) {
                existing.setTitle(desiredTitle)
            }
            val view = map.viewForAnnotation(existing) as? MKAnnotationView
            if (view != null) {
                val label = if (cluster.count > 99) "99+" else cluster.count.toString()
                val fill =
                    when {
                        cluster.isConnectionOnly -> UIColor.magentaColor
                        cluster.hasLiveConnections -> UIColor.blueColor
                        else -> UIColor.orangeColor
                    }
                val cacheKey = "cluster|$label|${cluster.isConnectionOnly}|${cluster.hasLiveConnections}"
                view.image =
                    pinTapDelegate.avatarPinImageCache.getOrPut(cacheKey) {
                        circularMapPinUIImage(
                            sizePts = 44.0,
                            fill = fill,
                            initials = label,
                            photo = null,
                        )
                    }
                view.zPriority = cluster.zIndex
            }
            return@forEach
        }
        val ann = MKPointAnnotation()
        ann.setTitle("${cluster.count}")
        ann.setSubtitle(key)
        ann.setCoordinate(CLLocationCoordinate2DMake(cluster.latitude, cluster.longitude))
        map.addAnnotation(ann)
    }

    val pinEntries = mutableListOf<Pair<MKPointAnnotation, MapPin>>()
    val clusterEntries = mutableListOf<Pair<MKPointAnnotation, MapClusterPin>>()
    map.annotations.filterIsInstance<MKPointAnnotation>().forEach { ann ->
        val sub = (ann.subtitle as? String)?.trim().orEmpty()
        when {
            sub.startsWith("cluster:") -> {
                val id = sub.removePrefix("cluster:").trim()
                desiredClusterIds[id]?.let { clusterEntries += ann to it }
            }
            sub.isNotEmpty() -> desiredPinIds[sub]?.let { pinEntries += ann to it }
        }
    }
    pinTapDelegate.pinEntries = pinEntries
    pinTapDelegate.clusterEntries = clusterEntries
}

private fun computeDataCenter(
    pins: List<MapPin>,
    clusters: List<MapClusterPin>,
): Pair<Double, Double>? {
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

internal fun approximatelyEqual(
    previous: Double?,
    current: Double,
    epsilon: Double = 0.00001,
): Boolean = previous != null && kotlin.math.abs(previous - current) <= epsilon

private fun metersForZoom(zoomLevel: Double): Double {
    val maxZoom = 20.0
    val minMeters = 120.0
    val maxMeters = 4_000_000.0
    val normalized = (maxZoom - zoomLevel).coerceIn(0.0, maxZoom)
    val meters = minMeters * 2.0.pow(normalized)
    return meters.coerceIn(minMeters, maxMeters)
}
