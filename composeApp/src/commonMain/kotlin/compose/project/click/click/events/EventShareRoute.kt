package compose.project.click.click.events

import compose.project.click.click.data.models.MapBeacon
import kotlin.math.abs

/** Google Maps HTTP fallback for share text and URI open. */
fun eventMapsHttpUrl(latitude: Double, longitude: Double): String =
    "https://maps.google.com/?q=$latitude,$longitude"

/** Prefer geo: for maps apps; callers may fall back to [eventMapsHttpUrl]. */
fun eventMapsGeoUri(latitude: Double, longitude: Double, label: String?): String {
    val safeLabel = label?.trim()?.takeIf { it.isNotEmpty() }
        ?.replace("(", "")
        ?.replace(")", "")
        ?.take(80)
    return if (safeLabel != null) {
        "geo:$latitude,$longitude?q=$latitude,$longitude($safeLabel)"
    } else {
        "geo:$latitude,$longitude?q=$latitude,$longitude"
    }
}

fun buildEventShareText(
    beacon: MapBeacon,
    scheduleLabel: String?,
    distanceLabel: String?,
): String {
    val title = beacon.metadata.title?.trim()?.takeIf { it.isNotEmpty() }
        ?: beacon.metadata.description?.trim()?.takeIf { it.isNotEmpty() }
        ?: "Click event"
    return buildString {
        append(title)
        scheduleLabel?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append("\n")
            append(it)
        }
        distanceLabel?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append("\n")
            append(it)
        }
        append("\n")
        append(eventMapsHttpUrl(beacon.latitude, beacon.longitude))
    }
}

fun hasFiniteCoordinates(latitude: Double, longitude: Double): Boolean =
    latitude.isFinite() && longitude.isFinite() &&
        abs(latitude) <= 90.0 && abs(longitude) <= 180.0
