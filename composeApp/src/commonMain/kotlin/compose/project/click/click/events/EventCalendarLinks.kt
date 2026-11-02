package compose.project.click.click.events // pragma: allowlist secret

import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import io.ktor.http.encodeURLParameter
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Add to Calendar and directions for an event (iOS `EventCalendar.swift`, `DirectionsDialog.swift`).
 * Only the event's own details leave the device: never the viewer's location.
 */
data class EventCalendarDraft(
    val title: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val location: String?,
    val details: String,
)

/** The draft for [this] event, or null without a schedule (non-event beacons). */
fun MapBeacon.calendarDraftOrNull(shareUrl: String = buildEventShareUrl(id)): EventCalendarDraft? {
    val schedule = eventSchedule() ?: return null
    val title =
        metadata.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: metadata.description
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.take(80)
            ?: "Click event"
    val location =
        listOfNotNull(metadata.locationName, metadata.formattedAddress)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(", ")
            .takeIf { it.isNotEmpty() }
    val details =
        listOfNotNull(metadata.description?.trim()?.takeIf { it.isNotEmpty() }, shareUrl)
            .joinToString("\n\n")
    return EventCalendarDraft(title, schedule.startEpochMs, schedule.endEpochMs, location, details)
}

/** `yyyyMMddTHHmmssZ` in UTC (Google Calendar's `dates`). */
internal fun compactUtc(epochMs: Long): String {
    val t = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.UTC)

    fun two(n: Int) = n.toString().padStart(2, '0')
    return "${t.year.toString().padStart(4, '0')}${two(t.monthNumber)}${two(t.dayOfMonth)}T" +
        "${two(t.hour)}${two(t.minute)}${two(t.second)}Z"
}

/** ISO-8601 in UTC without fractional seconds (Outlook's `startdt`/`enddt`). */
internal fun isoUtc(epochMs: Long): String =
    compactUtc(epochMs).let {
        "${it.substring(
            0,
            4,
        )}-${it.substring(4, 6)}-${it.substring(6, 8)}T${it.substring(9, 11)}:${it.substring(11, 13)}:${it.substring(13, 15)}Z"
    }

private fun query(params: List<Pair<String, String?>>): String =
    params
        .filter { !it.second.isNullOrBlank() }
        .joinToString("&") { (k, v) -> "$k=${v!!.encodeURLParameter()}" }

/** Google Calendar's prefilled "add event" page. */
fun googleCalendarUrl(draft: EventCalendarDraft): String =
    "https://calendar.google.com/calendar/render?" +
        query(
            listOf(
                "action" to "TEMPLATE",
                "text" to draft.title,
                "dates" to "${compactUtc(draft.startEpochMs)}/${compactUtc(draft.endEpochMs)}",
                "location" to draft.location,
                "details" to draft.details,
            ),
        )

/** Outlook's prefilled "new event" page (Outlook.com; work accounts sign in the same way). */
fun outlookCalendarUrl(draft: EventCalendarDraft): String =
    "https://outlook.live.com/calendar/0/action/compose?" +
        query(
            listOf(
                "rru" to "addevent",
                "subject" to draft.title,
                "startdt" to isoUtc(draft.startEpochMs),
                "enddt" to isoUtc(draft.endEpochMs),
                "location" to draft.location,
                "body" to draft.details,
            ),
        )

/** Google Maps directions to a point (the app opens it when installed). */
fun googleMapsDirectionsUrl(
    latitude: Double,
    longitude: Double,
): String = "https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude"
