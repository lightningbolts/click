package compose.project.click.click.viewmodel

import compose.project.click.click.data.models.AvailabilityIntentRow
import compose.project.click.click.data.models.MapBeacon
import compose.project.click.click.ui.utils.beaconTypeDisplayLabel
import compose.project.click.click.ui.utils.displayDynamicTitle
import compose.project.click.click.ui.utils.displayTypeTitle
import compose.project.click.click.ui.utils.userFacingLabel
import compose.project.click.click.ui.utils.haversineDistance
import kotlin.math.PI
import kotlin.math.cos

internal fun availabilityIntentMatchesQuery(intent: AvailabilityIntentRow, lowerQuery: String): Boolean {
    if (lowerQuery.isBlank()) return false
    val tag = intent.intentTag?.lowercase().orEmpty()
    val tf = intent.timeframe?.lowercase().orEmpty()
    return tag.contains(lowerQuery) ||
        tf.contains(lowerQuery) ||
        (tag.isNotEmpty() && lowerQuery.contains(tag))
}

internal fun mapBeaconSearchHaystack(beacon: MapBeacon): String {
    val meta = beacon.metadata
  return buildList {
        add(beacon.displayDynamicTitle())
        add(beacon.kind.userFacingLabel())
        beacon.sourceBeaconType?.let { add(beaconTypeDisplayLabel(it, beacon.kind)) }
        meta.title?.let { add(it) }
        meta.trackName?.let { add(it) }
        meta.artistName?.let { add(it) }
        meta.artist?.let { add(it) }
        meta.album?.let { add(it) }
        meta.description?.let { add(it) }
        meta.musicUrl?.let { add(it) }
        meta.originalUrl?.let { add(it) }
    }
        .joinToString(" ")
        .lowercase()
}

internal fun mapBeaconMatchesQuery(beacon: MapBeacon, lowerQuery: String): Boolean {
    if (lowerQuery.isBlank()) return false
    return mapBeaconSearchHaystack(beacon).contains(lowerQuery)
}

internal fun isBeaconStillActive(beacon: MapBeacon, nowEpochMs: Long): Boolean {
    val exp = beacon.expiresAtEpochMs ?: return true
    return exp > nowEpochMs
}

internal const val SEARCH_BEACON_RADIUS_METERS = 50_000.0

internal data class SearchBbox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
)

internal fun searchBboxFromCenter(lat: Double, lon: Double, radiusMeters: Double): SearchBbox {
    val latDelta = radiusMeters / 111_000.0
    val lonDelta = radiusMeters / (111_000.0 * cos(lat * PI / 180.0)).coerceAtLeast(0.001)
    return SearchBbox(
        minLat = lat - latDelta,
        maxLat = lat + latDelta,
        minLon = lon - lonDelta,
        maxLon = lon + lonDelta,
    )
}

internal fun beaconDistanceMeters(
    beacon: MapBeacon,
    userLat: Double?,
    userLon: Double?,
): Double? {
    if (userLat == null || userLon == null) return null
    return haversineDistance(userLat, userLon, beacon.latitude, beacon.longitude)
}

internal fun beaconDisplayTitle(beacon: MapBeacon): String = beacon.displayDynamicTitle()

internal fun beaconDisplaySubtitle(beacon: MapBeacon, distanceMeters: Double?): String {
    val meta = beacon.metadata
    val typeLabel = beacon.displayTypeTitle()
  return buildList {
        meta.artistName?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            ?: meta.artist?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        meta.description?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        if (isEmpty()) add(typeLabel)
        distanceMeters?.let { add(formatSearchDistance(it)) }
    }.joinToString(" · ")
}

internal fun formatSearchDistance(meters: Double): String {
    if (!meters.isFinite() || meters < 0) return ""
    return if (meters < 1000) {
        "${meters.toInt()} m away"
    } else {
        val km = meters / 1000.0
        val tenths = ((km * 10.0) + 0.5).toInt().coerceAtLeast(1)
        val whole = tenths / 10
        val frac = tenths % 10
        "$whole.$frac km away"
    }
}

/**
 * Compact snippet around the first case-insensitive [query] match so search rows
 * show the hit instead of the full message body.
 */
internal fun highlightedMessageSnippet(
    content: String,
    query: String,
    maxLen: Int = 140,
): String {
    val trimmed = content.trim().replace(Regex("\\s+"), " ")
    if (trimmed.isEmpty()) return ""
    val needle = query.trim()
    if (needle.isEmpty()) return trimmed.take(maxLen)
    val idx = trimmed.indexOf(needle, ignoreCase = true)
    if (idx < 0) return trimmed.take(maxLen)
    val pad = 28
    val start = (idx - pad).coerceAtLeast(0)
    val end = (idx + needle.length + 48).coerceAtMost(trimmed.length)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < trimmed.length) "…" else ""
    val slice = trimmed.substring(start, end)
    return (prefix + slice + suffix).take(maxLen)
}
