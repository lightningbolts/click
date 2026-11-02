package compose.project.click.click.ui.utils // pragma: allowlist secret

import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.ui.components.MapPin // pragma: allowlist secret
import compose.project.click.click.ui.components.MapPinKind // pragma: allowlist secret
import compose.project.click.click.ui.screens.naturalCompare // pragma: allowlist secret
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tan

/**
 * Pins whose faces would overlap on screen, drawn as one stack (Luma-style) and listed in a sheet
 * when tapped. A lone pin is a stack of one. Port of iOS `MapCluster` / `MapFeatureModel.clusters`
 * (click-ios `Click/Features/Map/MapFeatureModel.swift`).
 */
data class MapPinStack(
    val id: String,
    /** Most important first (see [mapPinStackRank]); never empty. */
    val members: List<MapPin>,
) {
    val lead: MapPin get() = members.first()
    val count: Int get() = members.size

    /** Where the stack sits: its lead's spot, so the most important pin never moves. */
    val latitude: Double get() = lead.latitude
    val longitude: Double get() = lead.longitude
}

object MapPinStacking {
    /**
     * Pins whose centres are closer than this on screen (dp) collide: a 40 dp face with its edge,
     * plus room for a stack's badge so stacks clear too. iOS `stackDistance` (points).
     */
    const val STACK_DISTANCE_DP: Double = 60.0

    /** Members this close together can't be told apart at any zoom: the list offers no zoom. */
    const val SAME_SPOT_METERS: Double = 20.0

    /** Zoom in quarter steps, rounded down so stacks err toward merging (iOS `zoomStep`). */
    fun zoomStep(zoom: Double): Double = floor(zoom * 4.0) / 4.0
}

/**
 * Which pin leads a stack and the order its list reads: alerts (so one is never hidden under
 * another pin), live events, then events (soonest first), Places, hubs, other beacons, then
 * connections. iOS `MapItem.stackRank` (plans = 5 have no Android pin yet).
 */
fun mapPinStackRank(
    pin: MapPin,
    nowEpochMs: Long,
): Int {
    val kind = pin.beaconKind
    return when {
        kind == MapBeaconKind.HAZARD || kind == MapBeaconKind.SOS -> -1
        kind == MapBeaconKind.EVENT -> if (pin.isLiveEventAt(nowEpochMs)) 0 else 1
        pin.kind == MapPinKind.PLACE -> 2
        pin.kind == MapPinKind.COMMUNITY_HUB -> 3
        kind != null -> 4
        pin.kind == MapPinKind.CONNECTION -> 7
        else -> 4
    }
}

private fun MapPin.isLiveEventAt(nowEpochMs: Long): Boolean {
    val start = eventStartEpochMs ?: return false
    val end = eventEndEpochMs ?: return false
    return nowEpochMs in start until end
}

/** iOS `MapItem.stackOrder`: rank, then soonest event, then A–Z, then id. */
fun mapPinStackComparator(nowEpochMs: Long): Comparator<MapPin> =
    compareBy<MapPin> { mapPinStackRank(it, nowEpochMs) }
        .thenBy { it.eventStartEpochMs ?: Long.MAX_VALUE }
        .then { a, b -> naturalCompare(a.title, b.title) }
        .thenBy { it.id }

/** On-screen dp of a coordinate at [zoom] (Web Mercator, 256 dp world at zoom 0). */
internal fun mercatorPointDp(
    latitude: Double,
    longitude: Double,
    zoom: Double,
): Pair<Double, Double> {
    val scale = 256.0 * 2.0.pow(zoom)
    val lat = latitude.coerceIn(-85.05112878, 85.05112878) * PI / 180.0
    val x = (longitude + 180.0) / 360.0 * scale
    val y = (1.0 - ln(tan(lat) + 1.0 / cos(lat)) / PI) / 2.0 * scale
    return x to y
}

/**
 * Walking pins most important first, each unclaimed pin takes every unclaimed pin whose face
 * would overlap its own at [zoom]. A grid of [MapPinStacking.STACK_DISTANCE_DP] cells keeps this
 * close to linear for thousands of pins. Stacks come out lead-first in importance order.
 */
fun stackMapPins(
    pins: List<MapPin>,
    zoom: Double,
    nowEpochMs: Long,
): List<MapPinStack> {
    if (pins.isEmpty()) return emptyList()
    val ordered = pins.distinctBy { it.id }.sortedWith(mapPinStackComparator(nowEpochMs))
    val points = ordered.map { mercatorPointDp(it.latitude, it.longitude, zoom) }
    val d = MapPinStacking.STACK_DISTANCE_DP

    fun cell(p: Pair<Double, Double>): Pair<Long, Long> = floor(p.first / d).toLong() to floor(p.second / d).toLong()
    val grid = HashMap<Pair<Long, Long>, MutableList<Int>>()
    ordered.indices.forEach { i -> grid.getOrPut(cell(points[i])) { mutableListOf() }.add(i) }

    val claimed = BooleanArray(ordered.size)
    val result = ArrayList<MapPinStack>()
    for (seed in ordered.indices) {
        if (claimed[seed]) continue
        claimed[seed] = true
        val members = mutableListOf(seed)
        val home = cell(points[seed])
        for (dx in -1L..1L) {
            for (dy in -1L..1L) {
                for (other in grid[(home.first + dx) to (home.second + dy)].orEmpty()) {
                    if (claimed[other]) continue
                    val dist = hypot(points[other].first - points[seed].first, points[other].second - points[seed].second)
                    if (dist >= d) continue
                    claimed[other] = true
                    members += other
                }
            }
        }
        // Indices follow the stack order, so sorting them keeps the lead first.
        members.sort()
        val group = members.map { ordered[it] }
        result += MapPinStack(id = if (group.size == 1) group[0].id else "stack.${group[0].id}", members = group)
    }
    return result
}

/** The stack's lead drawn with its member count (the map draws a badge when > 1). */
fun MapPinStack.toRenderedPin(): MapPin = if (count == 1) lead else lead.copy(stackCount = count)

/** Whether zooming in could pull a stack apart (its members aren't all at one spot). */
fun MapPinStack.canSeparate(): Boolean =
    members.any { haversineDistance(latitude, longitude, it.latitude, it.longitude) > MapPinStacking.SAME_SPOT_METERS }

/**
 * Camera that shows every member with room around them, close enough that they separate
 * (iOS `MapFeatureModel.region(fitting:)`: 1.8× the members' span, at least 0.003°).
 * Returns centre latitude, longitude and zoom.
 */
fun MapPinStack.zoomInTarget(): Triple<Double, Double, Double> {
    val lats = members.map { it.latitude }
    val lons = members.map { it.longitude }
    val minLat = lats.min()
    val maxLat = lats.max()
    val minLon = lons.min()
    val maxLon = lons.max()
    val span = max(max((maxLat - minLat) * 1.8, (maxLon - minLon) * 1.8), 0.003)
    val zoom = log2(FIT_WIDTH_DEGREES_AT_ZOOM_0 / span).coerceIn(3.0, 20.0)
    return Triple((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0, zoom)
}

/**
 * Degrees of longitude a 400 dp wide map (phones are 360–430) shows at zoom 0: Google Maps and
 * MapKit both draw a 256 dp/pt Web-Mercator world at zoom 0.
 */
private const val FIT_WIDTH_DEGREES_AT_ZOOM_0: Double = 400.0 / 256.0 * 360.0

/**
 * "5 Events", "3 Clicks", or "4 Here" when they're of different kinds (iOS `MapStackSheet.title`).
 */
fun mapPinStackTitle(pins: List<MapPin>): String {
    val nouns =
        pins
            .map { pin ->
                when {
                    pin.kind == MapPinKind.CONNECTION -> "Clicks"
                    pin.kind == MapPinKind.COMMUNITY_HUB -> "Hubs"
                    pin.kind == MapPinKind.PLACE -> "Places"
                    pin.beaconKind == MapBeaconKind.EVENT -> "Events"
                    else -> "Beacons"
                }
            }.toSet()
    return "${pins.size} ${nouns.singleOrNull() ?: "Here"}"
}

/** Members of the stack whose lead is [leadId] at [zoom], or null when it isn't a stack there. */
fun findMapPinStack(
    leadId: String,
    pins: List<MapPin>,
    zoom: Double,
    nowEpochMs: Long,
): MapPinStack? = stackMapPins(pins, zoom, nowEpochMs).firstOrNull { it.lead.id == leadId && it.count > 1 }
