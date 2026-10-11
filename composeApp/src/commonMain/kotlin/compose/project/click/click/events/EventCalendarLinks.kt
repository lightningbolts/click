package compose.project.click.click.events // pragma: allowlist secret

import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import io.ktor.http.encodeURLParameter
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * "Add to" calendar targets for an event page and the Click Pass (sheet 08 G3-19;
 * iOS `EventCalendar.swift`). Android can't learn whether a device-calendar insert was saved,
 * so there is no "In Calendar" state (a documented divergence).
 */
enum class EventCalendarTarget(
    val label: String,
) {
    DEVICE("Device calendar"),
    GOOGLE("Google Calendar"),
    OUTLOOK("Outlook"),
}

/** What a calendar entry carries, from the beacon. */
data class EventCalendarEntry(
    val title: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val location: String?,
    /** The description, a blank line, then the event's public link. */
    val details: String,
)

fun eventCalendarEntry(
    beacon: MapBeacon,
    title: String,
): EventCalendarEntry? {
    val schedule = beacon.eventSchedule() ?: return null
    val place =
        listOfNotNull(beacon.metadata.locationName, beacon.metadata.formattedAddress)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(", ")
            .takeIf { it.isNotEmpty() }
    return EventCalendarEntry(
        title = title,
        startEpochMs = schedule.startEpochMs,
        endEpochMs = schedule.endEpochMs,
        location = place,
        details = eventCalendarDetails(beacon.metadata.description, beacon.id),
    )
}

fun eventCalendarDetails(
    description: String?,
    beaconId: String,
): String =
    listOfNotNull(description?.trim()?.takeIf { it.isNotEmpty() }, buildEventShareUrl(beaconId))
        .joinToString("\n\n")

/** `20261007T010000Z` */
fun compactUtcStamp(epochMs: Long): String {
    val t = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.UTC)

    fun two(v: Int) = v.toString().padStart(2, '0')
    return "${t.year}${two(t.monthNumber)}${two(t.dayOfMonth)}T${two(t.hour)}${two(t.minute)}${two(t.second)}Z"
}

/** `2026-10-07T01:00:00Z` (ISO 8601 without fractions, like iOS `ISO8601DateFormatter`). */
fun isoUtcStamp(epochMs: Long): String {
    val t = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.UTC)

    fun two(v: Int) = v.toString().padStart(2, '0')
    return "${t.year}-${two(t.monthNumber)}-${two(t.dayOfMonth)}T${two(t.hour)}:${two(t.minute)}:${two(t.second)}Z"
}

private fun query(vararg pairs: Pair<String, String?>): String =
    pairs
        .filter { it.second != null }
        .joinToString("&") { (k, v) -> "$k=${v!!.encodeURLParameter()}" }

fun googleCalendarUrl(entry: EventCalendarEntry): String =
    "https://calendar.google.com/calendar/render?" +
        query(
            "action" to "TEMPLATE",
            "text" to entry.title,
            "dates" to "${compactUtcStamp(entry.startEpochMs)}/${compactUtcStamp(entry.endEpochMs)}",
            "location" to entry.location,
            "details" to entry.details,
        )

fun outlookCalendarUrl(entry: EventCalendarEntry): String =
    "https://outlook.live.com/calendar/0/action/compose?" +
        query(
            "rru" to "addevent",
            "subject" to entry.title,
            "startdt" to isoUtcStamp(entry.startEpochMs),
            "enddt" to isoUtcStamp(entry.endEpochMs),
            "location" to entry.location,
            "body" to entry.details,
        )

/** Where a "Get directions" choice goes (sheet 08 G3-20). Never carries the user's own location. */
object EventDirectionsLinks {
    fun googleNavigation(
        latitude: Double,
        longitude: Double,
    ): String = "google.navigation:q=$latitude,$longitude"

    fun googleMapsWeb(
        latitude: Double,
        longitude: Double,
    ): String = "https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude"

    fun geo(
        latitude: Double,
        longitude: Double,
        label: String?,
    ): String {
        val name = label?.trim()?.takeIf { it.isNotEmpty() }
        val suffix = name?.let { "(${it.encodeURLParameter()})" }.orEmpty()
        return "geo:$latitude,$longitude?q=$latitude,$longitude$suffix"
    }
}

/** The text "Copy Address" copies: the address, else the place name, else the coordinate. */
fun eventCopyAddressText(beacon: MapBeacon): String =
    beacon.metadata.formattedAddress
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: beacon.metadata.locationName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        ?: "${beacon.latitude}, ${beacon.longitude}"
