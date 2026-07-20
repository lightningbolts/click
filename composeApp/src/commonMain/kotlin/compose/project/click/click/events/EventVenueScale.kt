package compose.project.click.click.events

import compose.project.click.click.data.models.MapBeacon
import kotlinx.serialization.json.JsonPrimitive

/** Creator-selected check-in footprint (separate from topic categories). */
enum class EventVenueScale(val apiValue: String, val radiusMeters: Int, val label: String) {
    Intimate("intimate", 75, "Intimate"),
    Neighborhood("neighborhood", 250, "Neighborhood"),
    Venue("venue", 750, "Venue"),
    Campus("campus", 2500, "Campus"),
    ;
    companion object {
        val DEFAULT = Neighborhood

        fun fromApi(raw: String?): EventVenueScale {
            val key = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.apiValue == key } ?: DEFAULT
        }
    }
}

const val EVENT_VENUE_SCALE_METADATA_KEY = "venue_scale"
const val EVENT_CHECK_IN_RADIUS_METADATA_KEY = "check_in_radius_meters"

/** Mirrors click-web `resolveCheckInRadiusMeters` — clamp [25, 5000]. */
fun resolveEventCheckInRadiusMeters(
    venueScaleRaw: String?,
    checkInRadiusMetersRaw: Double?,
): Double {
    val fromExplicit = checkInRadiusMetersRaw?.takeIf { it.isFinite() && it > 0 }
    if (fromExplicit != null) return fromExplicit.coerceIn(25.0, 5_000.0)
    return EventVenueScale.fromApi(venueScaleRaw).radiusMeters.toDouble().coerceIn(25.0, 5_000.0)
}

fun MapBeacon.resolveEventCheckInRadiusMeters(): Double {
    val raw = metadata.raw
    val explicit = raw?.get(EVENT_CHECK_IN_RADIUS_METADATA_KEY)?.let { el ->
        when (el) {
            is JsonPrimitive ->
                el.content.toDoubleOrNull() ?: el.content.toLongOrNull()?.toDouble()
            else -> null
        }
    }
    val scale = raw?.get(EVENT_VENUE_SCALE_METADATA_KEY)?.let { el ->
        (el as? JsonPrimitive)?.content
    }
    return resolveEventCheckInRadiusMeters(scale, explicit)
}
