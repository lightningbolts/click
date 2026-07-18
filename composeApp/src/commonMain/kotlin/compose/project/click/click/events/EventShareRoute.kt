package compose.project.click.click.events

import compose.project.click.click.data.models.MapBeacon
import kotlin.math.abs

/** HTTPS Maps URL — preferred open target (geo: fails on iOS without a registered handler). */
fun eventMapsHttpUrl(latitude: Double, longitude: Double): String =
    "https://maps.google.com/?q=$latitude,$longitude"

/** Apple Maps HTTPS (optional alternate). */
fun eventMapsAppleHttpUrl(latitude: Double, longitude: Double, label: String?): String {
    val q = label?.trim()?.takeIf { it.isNotEmpty() }
        ?.replace(" ", "+")
        ?.take(80)
        ?: "$latitude,$longitude"
    return "https://maps.apple.com/?ll=$latitude,$longitude&q=$q"
}

/** Legacy geo: URI — unreliable on iOS; prefer [eventMapsHttpUrl]. */
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

/** Open directions via HTTPS maps (Google first, Apple Maps fallback). Avoids geo: NSOSStatus -10814 on iOS. */
fun openEventMapsRoute(
    openUri: (String) -> Unit,
    latitude: Double,
    longitude: Double,
    label: String?,
) {
    if (!hasFiniteCoordinates(latitude, longitude)) return
    val google = eventMapsHttpUrl(latitude, longitude)
    val apple = eventMapsAppleHttpUrl(latitude, longitude, label)
    runCatching { openUri(google) }
        .onFailure { runCatching { openUri(apple) } }
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
