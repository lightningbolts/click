package compose.project.click.click.events

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
