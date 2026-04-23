package compose.project.click.click.ui.utils // pragma: allowlist secret

import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.GeoLocation // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import kotlinx.datetime.Clock
import kotlin.math.*

/**
 * Map Utilities for the Social Memory Map
 * 
 * Provides clustering algorithm and time-based visual decay calculations
 */

// Time thresholds in milliseconds
private const val LIVE_THRESHOLD_MS = 4L * 60 * 60 * 1000      // 4 hours
private const val RECENT_THRESHOLD_MS = 24L * 60 * 60 * 1000   // 24 hours

/**
 * Time-based decay state for connection visualization
 */
enum class TimeState {
    LIVE,      // 0-4 hours: Full opacity + pulsing animation
    RECENT,    // 4-24 hours: Full opacity, no pulse
    ARCHIVE    // >24 hours: 60% opacity (ghosted)
}

/**
 * Represents a connection point on the map with visual metadata
 */
data class ConnectionMapPoint(
    val connection: Connection,
    val latitude: Double,
    val longitude: Double,
    val timeState: TimeState,
    val opacity: Float,
    val shouldPulse: Boolean,
    val displayName: String,
    val formattedDate: String
)

/**
 * Semantic icon types for cluster rendering.
 * Maps semantic_location strings to specific icons.
 */
enum class SemanticIcon {
    COFFEE,    // café, coffee shop, starbucks
    BOOK,      // library, bookstore
    MUSIC,     // concert, music venue, bar
    FOOD,      // restaurant, dining
    SCHOOL,    // university, school, campus
    PARK,      // park, garden, outdoor
    GYM,       // gym, fitness
    SHOPPING,  // mall, store, shop
    DEFAULT    // fallback for unknown locations
}

/**
 * Parse a semantic_location string to a SemanticIcon.
 */
fun parseSemanticIcon(semanticLocation: String?): SemanticIcon {
    if (semanticLocation == null) return SemanticIcon.DEFAULT
    val lower = semanticLocation.lowercase()
    return when {
        lower.contains("coffee") || lower.contains("café") || lower.contains("cafe") || lower.contains("starbucks") -> SemanticIcon.COFFEE
        lower.contains("library") || lower.contains("book") -> SemanticIcon.BOOK
        lower.contains("concert") || lower.contains("music") || lower.contains("bar") || lower.contains("club") -> SemanticIcon.MUSIC
        lower.contains("restaurant") || lower.contains("dining") || lower.contains("food") || lower.contains("eat") -> SemanticIcon.FOOD
        lower.contains("university") || lower.contains("school") || lower.contains("campus") || lower.contains("college") -> SemanticIcon.SCHOOL
        lower.contains("park") || lower.contains("garden") || lower.contains("trail") || lower.contains("outdoor") -> SemanticIcon.PARK
        lower.contains("gym") || lower.contains("fitness") || lower.contains("sport") -> SemanticIcon.GYM
        lower.contains("mall") || lower.contains("store") || lower.contains("shop") || lower.contains("market") -> SemanticIcon.SHOPPING
        else -> SemanticIcon.DEFAULT
    }
}

/**
 * Represents a cluster of nearby connections (Hub)
 */
data class MapCluster(
    val id: String,
    val centerLat: Double,
    val centerLon: Double,
    val points: List<ConnectionMapPoint>,
    val beaconPoints: List<MapBeacon> = emptyList(),
    val count: Int = points.size + beaconPoints.size,
    val semanticIcon: SemanticIcon = SemanticIcon.DEFAULT
) {
    val boundingBox: BoundingBox
        get() {
            val allLats = points.map { it.latitude } + beaconPoints.map { it.latitude }
            val allLons = points.map { it.longitude } + beaconPoints.map { it.longitude }
            val minLat = allLats.minOrNull() ?: centerLat
            val maxLat = allLats.maxOrNull() ?: centerLat
            val minLon = allLons.minOrNull() ?: centerLon
            val maxLon = allLons.maxOrNull() ?: centerLon
            return BoundingBox(minLat, maxLat, minLon, maxLon)
        }
}

/** Single map item for unified clustering (connections + beacons). */
private sealed class MapClusterMember {
    data class Conn(val point: ConnectionMapPoint) : MapClusterMember()
    data class Bcn(val beacon: MapBeacon) : MapClusterMember()
}

/**
 * Bounding box for zoom calculations
 */
data class BoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
) {
    val centerLat: Double get() = (minLat + maxLat) / 2
    val centerLon: Double get() = (minLon + maxLon) / 2
}

/**
 * Sealed class representing what to render on the map based on zoom level
 */
sealed class MapRenderData {
    /**
     * @param standaloneBeacons High-priority beacons (soundtrack, hazard, utility) that stay
     * as individual pins while zoomed out so they are not absorbed into connection clusters.
     */
    data class Clusters(
        val clusters: List<MapCluster>,
        val standaloneBeacons: List<MapBeacon> = emptyList(),
    ) : MapRenderData()

    data class IndividualPins(
        val points: List<ConnectionMapPoint>,
        val beacons: List<MapBeacon> = emptyList(),
    ) : MapRenderData()
}

/**
 * Calculates the time state for a connection based on when it was created
 */
fun calculateTimeState(createdTimestamp: Long): TimeState {
    val now = Clock.System.now().toEpochMilliseconds()
    val age = now - createdTimestamp
    
    return when {
        age <= LIVE_THRESHOLD_MS -> TimeState.LIVE
        age <= RECENT_THRESHOLD_MS -> TimeState.RECENT
        else -> TimeState.ARCHIVE
    }
}

/**
 * Gets the opacity value for a given time state
 */
fun getOpacityForTimeState(timeState: TimeState): Float {
    return when (timeState) {
        TimeState.LIVE -> 1.0f
        TimeState.RECENT -> 1.0f
        TimeState.ARCHIVE -> 0.6f
    }
}

/**
 * Converts a Connection to a ConnectionMapPoint with visual metadata
 */
fun Connection.toMapPoint(): ConnectionMapPoint {
    val geo = this.connectionMapGeo()
        ?: throw IllegalArgumentException("Invalid geo for connection $id")
    val lat = geo.lat
    val lon = geo.lon

    val timeState = calculateTimeState(created)
    
    val displayName = semanticLocation
        ?: displayLocationLabel
        ?: "Connection"
    
    val formattedDate = formatTimestamp(created)
    
    return ConnectionMapPoint(
        connection = this,
        latitude = lat,
        longitude = lon,
        timeState = timeState,
        opacity = getOpacityForTimeState(timeState),
        shouldPulse = timeState == TimeState.LIVE,
        displayName = displayName,
        formattedDate = formattedDate
    )
}

/**
 * Format timestamp to human-readable date
 */
private fun formatTimestamp(timestamp: Long): String {
    val now = Clock.System.now().toEpochMilliseconds()
    val age = now - timestamp
    
    return when {
        age < 60 * 1000 -> "Just now"
        age < 60 * 60 * 1000 -> "${age / (60 * 1000)} min ago"
        age < 24 * 60 * 60 * 1000 -> "${age / (60 * 60 * 1000)} hours ago"
        age < 7 * 24 * 60 * 60 * 1000 -> "${age / (24 * 60 * 60 * 1000)} days ago"
        else -> {
            // Simple date formatting
            val days = age / (24 * 60 * 60 * 1000)
            val weeks = days / 7
            if (weeks < 4) "$weeks weeks ago" else "${days / 30} months ago"
        }
    }
}

/**
 * Simple distance-based clustering algorithm
 * 
 * @param points List of map points to cluster
 * @param clusterRadiusMeters Radius in meters for clustering
 * @return List of clusters
 */
fun clusterPoints(
    points: List<ConnectionMapPoint>,
    clusterRadiusMeters: Double = 500.0
): List<MapCluster> =
    clusterUnifiedMembers(
        members = points.map { MapClusterMember.Conn(it) },
        clusterRadiusMeters = clusterRadiusMeters,
    )

private fun clusterUnifiedMembers(
    members: List<MapClusterMember>,
    clusterRadiusMeters: Double,
): List<MapCluster> {
    if (members.isEmpty()) return emptyList()

    val clusters = mutableListOf<MapCluster>()
    val assigned = mutableSetOf<String>()

    fun memberId(m: MapClusterMember): String = when (m) {
        is MapClusterMember.Conn -> "c:${m.point.connection.id}"
        is MapClusterMember.Bcn -> "b:${m.beacon.id}"
    }

    fun latLon(m: MapClusterMember): Pair<Double, Double> = when (m) {
        is MapClusterMember.Conn -> m.point.latitude to m.point.longitude
        is MapClusterMember.Bcn -> m.beacon.latitude to m.beacon.longitude
    }

    for (seed in members) {
        val sid = memberId(seed)
        if (sid in assigned) continue

        val seedLat = latLon(seed).first
        val seedLon = latLon(seed).second
        val nearby = members.filter { other ->
            memberId(other) !in assigned &&
                haversineDistance(seedLat, seedLon, latLon(other).first, latLon(other).second) <= clusterRadiusMeters
        }
        nearby.forEach { assigned.add(memberId(it)) }

        val connPts = nearby.filterIsInstance<MapClusterMember.Conn>().map { it.point }
        val bcns = nearby.filterIsInstance<MapClusterMember.Bcn>().map { it.beacon }

        val centerLat = nearby.map { latLon(it).first }.average()
        val centerLon = nearby.map { latLon(it).second }.average()

        val iconCounts = connPts
            .map { parseSemanticIcon(it.connection.semanticLocation) }
            .groupBy { it }
            .mapValues { it.value.size }
        val dominantIcon = iconCounts.maxByOrNull { it.value }?.key ?: SemanticIcon.DEFAULT

        val memberKey = nearby.map { memberId(it) }.sorted().joinToString("|")
        val stableId = "cluster_${abs(memberKey.hashCode())}"

        clusters.add(
            MapCluster(
                id = stableId,
                centerLat = centerLat,
                centerLon = centerLon,
                points = connPts,
                beaconPoints = bcns,
                semanticIcon = dominantIcon,
            ),
        )
    }

    return clusters
}

/**
 * Determines what to render based on current zoom level
 *
 * @param connections All connections
 * @param zoomLevel Current map zoom level
 * @param clusterThreshold Zoom level above which to show individual pins
 * @return MapRenderData indicating clusters or individual pins
 */
fun determineMapRenderData(
    connections: List<Connection>,
    beacons: List<MapBeacon>,
    zoomLevel: Double,
    clusterThreshold: Double = 12.0
): MapRenderData {
    val standaloneKinds = setOf(
        MapBeaconKind.SOUNDTRACK,
        MapBeaconKind.HAZARD,
        MapBeaconKind.UTILITY,
    )
    val points = connections.mapNotNull { conn ->
        try {
            conn.toMapPoint()
        } catch (e: Exception) {
            null
        }
    }

    return if (zoomLevel >= clusterThreshold) {
        MapRenderData.IndividualPins(points = points, beacons = beacons)
    } else {
        val clusterRadius = when {
            zoomLevel < 6 -> 10000.0
            zoomLevel < 8 -> 5000.0
            zoomLevel < 10 -> 1000.0
            else -> 500.0
        }
        val standalone = beacons.filter { it.kind in standaloneKinds }
        val clusterable = beacons.filter { it.kind !in standaloneKinds }
        val members = buildList {
            points.forEach { add(MapClusterMember.Conn(it)) }
            clusterable.forEach { add(MapClusterMember.Bcn(it)) }
        }
        MapRenderData.Clusters(
            clusters = clusterUnifiedMembers(members, clusterRadius),
            standaloneBeacons = standalone,
        )
    }
}

/**
 * All beacon pins draw above connection pins and cluster hubs ([MapPin.fromConnectionPoint]
 * and [MapClusterPin] use lower z-index values).
 */
fun beaconZIndex(beacon: MapBeacon): Float =
    when (beacon.kind) {
        MapBeaconKind.HAZARD, MapBeaconKind.SOS -> 12_000f
        MapBeaconKind.SOUNDTRACK -> 11_500f
        MapBeaconKind.UTILITY -> 11_400f
        MapBeaconKind.STUDY -> 11_200f
        MapBeaconKind.SOCIAL_VIBE -> 11_100f
        MapBeaconKind.OTHER -> 11_050f
    }

/**
 * Haversine formula to calculate distance between two coordinates in meters
 */
fun haversineDistance(
    lat1: Double, lon1: Double,
    lat2: Double, lon2: Double
): Double {
    val earthRadius = 6371000.0 // meters
    
    // Convert degrees to radians
    val dLat = (lat2 - lat1) * PI / 180.0
    val dLon = (lon2 - lon1) * PI / 180.0
    val lat1Rad = lat1 * PI / 180.0
    val lat2Rad = lat2 * PI / 180.0
    
    val a = sin(dLat / 2).pow(2) +
            cos(lat1Rad) * cos(lat2Rad) *
            sin(dLon / 2).pow(2)
    
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    
    return earthRadius * c
}

/**
 * Calculate the optimal zoom level to fit a bounding box
 */
fun calculateZoomForBounds(bounds: BoundingBox, mapWidthPx: Int = 400): Double {
    val latDiff = bounds.maxLat - bounds.minLat
    val lonDiff = bounds.maxLon - bounds.minLon
    
    val maxDiff = maxOf(latDiff, lonDiff)
    
    return when {
        maxDiff > 10 -> 4.0
        maxDiff > 5 -> 6.0
        maxDiff > 1 -> 8.0
        maxDiff > 0.1 -> 10.0
        maxDiff > 0.01 -> 12.0
        maxDiff > 0.001 -> 14.0
        else -> 16.0
    }
}
