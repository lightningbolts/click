package compose.project.click.click.ui.utils

import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.GeoLocation
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
 * Represents a cluster of nearby connections (Hub)
 */
data class MapCluster(
    val id: String,
    val centerLat: Double,
    val centerLon: Double,
    val points: List<ConnectionMapPoint>,
    val count: Int = points.size
) {
    val boundingBox: BoundingBox
        get() {
            val minLat = points.minOfOrNull { it.latitude } ?: centerLat
            val maxLat = points.maxOfOrNull { it.latitude } ?: centerLat
            val minLon = points.minOfOrNull { it.longitude } ?: centerLon
            val maxLon = points.maxOfOrNull { it.longitude } ?: centerLon
            return BoundingBox(minLat, maxLat, minLon, maxLon)
        }
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
    data class Clusters(val clusters: List<MapCluster>) : MapRenderData()
    data class IndividualPins(val points: List<ConnectionMapPoint>) : MapRenderData()
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
    val timeState = calculateTimeState(created)
    
    val displayName = semantic_location 
        ?: context_tag 
        ?: "Connection"
    
    val formattedDate = formatTimestamp(created)
    
    return ConnectionMapPoint(
        connection = this,
        latitude = geo_location.lat,
        longitude = geo_location.lon,
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
): List<MapCluster> {
    if (points.isEmpty()) return emptyList()
    
    val clusters = mutableListOf<MapCluster>()
    val assigned = mutableSetOf<String>()
    
    for (point in points) {
        if (point.connection.id in assigned) continue
        
        // Find all points within radius
        val nearbyPoints = points.filter { other ->
            other.connection.id !in assigned &&
            haversineDistance(
                point.latitude, point.longitude,
                other.latitude, other.longitude
            ) <= clusterRadiusMeters
        }
        
        // Mark as assigned
        nearbyPoints.forEach { assigned.add(it.connection.id) }
        
        // Calculate cluster center
        val centerLat = nearbyPoints.map { it.latitude }.average()
        val centerLon = nearbyPoints.map { it.longitude }.average()
        
        clusters.add(
            MapCluster(
                id = "cluster_${clusters.size}",
                centerLat = centerLat,
                centerLon = centerLon,
                points = nearbyPoints
            )
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
    zoomLevel: Double,
    clusterThreshold: Double = 12.0
): MapRenderData {
    val points = connections.mapNotNull { conn ->
        try {
            conn.toMapPoint()
        } catch (e: Exception) {
            null
        }
    }
    
    return if (zoomLevel >= clusterThreshold) {
        MapRenderData.IndividualPins(points)
    } else {
        // Adjust cluster radius based on zoom
        // At lower zoom levels, use larger radius
        val clusterRadius = when {
            zoomLevel < 6 -> 10000.0  // 10km
            zoomLevel < 8 -> 5000.0   // 5km
            zoomLevel < 10 -> 1000.0  // 1km
            else -> 500.0             // 500m
        }
        MapRenderData.Clusters(clusterPoints(points, clusterRadius))
    }
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
