package compose.project.click.click.data.places

import compose.project.click.click.util.formatClockTime
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Shared Places copy (click-web spec §4.11, web `lib/places/labels.ts`). Mirrors iOS
 * `Click/Features/Places/PlaceCopy.swift`. No thresholds: one report reads "1 report".
 */
object PlaceCopy {
    const val NO_PULSE = "No Pulse yet — be the first when you're here"

    fun reports(n: Int): String = if (n == 1) "1 report" else "$n reports"

    fun age(
        epochMs: Long?,
        nowMs: Long,
    ): String {
        if (epochMs == null) return ""
        val minutes = max(0L, (nowMs - epochMs) / 60_000L)
        if (minutes < 1) return "just now"
        if (minutes < 60) return "$minutes min ago"
        val hours = minutes / 60
        if (hours < 24) return "$hours h ago"
        return "${hours / 24} d ago"
    }

    /** "Lively · 1 report · 4 min ago" / "Last Pulse: Chill · 3 h ago" / no Pulse. */
    fun pulseLine(
        pulse: PulseSummaryDto,
        nowMs: Long,
    ): String {
        val label = pulse.energy ?: return NO_PULSE
        return when {
            pulse.isLive -> "${label.title} · ${reports(pulse.reportCount)} · ${age(pulse.newestAtMs, nowMs)}"
            pulse.isStale -> "Last Pulse: ${label.title} · ${age(pulse.newestAtMs, nowMs)}"
            else -> NO_PULSE
        }
    }

    /** "1 report · 4 min ago" (the energy pill carries the label). */
    fun liveDetail(
        pulse: PulseSummaryDto,
        nowMs: Long,
    ): String = "${reports(pulse.reportCount)} · ${age(pulse.newestAtMs, nowMs)}"

    fun confidence(raw: String?): String? =
        when (raw) {
            "high" -> "Strong read"
            "medium" -> "Fair read"
            "low" -> "Early read"
            else -> null
        }

    fun pattern(pattern: PlacePatternDto): String? {
        val label = pattern.energy ?: return null
        return "Usually ${label.title} around now · ${reports(pattern.reportCount)} over ${pattern.weeks} weeks"
    }

    /** null hides the row. */
    fun hereNow(count: Int): String? = if (count > 0) "$count here now" else null

    /**
     * One line under a Place on the map, the stack list and search (iOS `PlaceCopy.mapSubtitle`):
     * "Lively · 3 reports", "Live: Trivia", "Tonight 8:00 PM · Trivia", else the category.
     */
    fun mapSubtitle(
        place: PlaceSummaryDto,
        nowMs: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): String {
        val energy = place.pulse.energy
        if (place.pulse.isLive && energy != null) return "${energy.title} · ${reports(place.pulse.reportCount)}"
        val event = place.nextEvent
        if (event != null) {
            if (event.isLive) return "Live: ${event.title}"
            val start = event.startsAtMs
            if (start != null) {
                val startDate = Instant.fromEpochMilliseconds(start).toLocalDateTime(timeZone).date
                val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(timeZone).date
                val day =
                    if (startDate == today) {
                        "Tonight"
                    } else {
                        startDate.dayOfWeek.name
                            .lowercase()
                            .replaceFirstChar { it.uppercase() }
                            .take(3)
                    }
                return "$day ${formatClockTime(start, timeZone)} · ${event.title}"
            }
        }
        return place.placeCategory.label
    }

    /** Pin badge: "LIVE" for a live event, else its start time when there's an event today (iOS `pinBadge`). */
    fun pinBadge(
        place: PlaceSummaryDto,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): String? {
        val event = place.nextEvent ?: return null
        if (event.isLive) return "LIVE"
        val start = event.startsAtMs ?: return null
        return if (place.eventsTodayCount > 0) formatClockTime(start, timeZone) else null
    }

    /** "Maya, Jordan and 1 other" (names arrive sorted by name). */
    fun names(
        names: List<String>,
        total: Int,
    ): String {
        val shown = names.take(2)
        val others = max(0, total - shown.size)
        val joined = if (shown.size == 2) "${shown[0]}, ${shown[1]}" else shown.firstOrNull().orEmpty()
        if (others == 0) return if (shown.size == 2) "${shown[0]} and ${shown[1]}" else joined
        val tail = if (others == 1) "1 other" else "$others others"
        return if (joined.isEmpty()) tail else "$joined and $tail"
    }

    /** "Café · Seattle". */
    fun categoryCity(summary: PlaceSummaryDto): String = listOfNotNull(summary.placeCategory.label, summary.city).joinToString(" · ")

    /** "Seats: Plenty 2 · Some 1 · None 0" (iOS `PlaceDetailContent.categoryLine`). */
    fun categoryLine(
        question: String,
        counts: List<Int>,
    ): String {
        val labels =
            when (question) {
                "seats" -> listOf("Plenty", "Some", "None")
                "equipment" -> listOf("None", "Some", "Long")
                else -> listOf("None", "Short", "Long")
            }
        val title =
            when (question) {
                "seats" -> "Seats"
                "line" -> "Line"
                "wait" -> "Wait"
                else -> "Equipment wait"
            }
        // Short arrays pad with zeros so every choice shows (like the energy bars).
        val padded = labels.indices.map { counts.getOrElse(it) { 0 } }
        return "$title: " + labels.zip(padded).joinToString(" · ") { (label, count) -> "$label $count" }
    }

    fun easyToTalk(pulse: PulseSummaryDto): String? {
        val t = pulse.talkable ?: return null
        if (t.yes + t.no <= 0) return null
        return "Easy to talk: ${t.yes} yes · ${t.no} no"
    }

    /** "Maya is here" / "Maya and Jordan are here". */
    fun hereNowConnections(people: List<PlacePersonDto>): String? {
        if (people.isEmpty()) return null
        val verb = if (people.size == 1) "is" else "are"
        return "${names(people.map { it.name }, people.size)} $verb here"
    }

    fun clicksBeenHere(been: ClicksBeenHereDto): String? {
        if (been.count <= 0) return null
        val verb = if (been.count == 1) "has" else "have"
        return "${names(been.names, been.count)} $verb been here"
    }

    fun youMetHere(met: YouMetHereDto): String? {
        if (met.total <= 0) return null
        return "You met ${names(met.people.map { it.name }, met.total)} here"
    }

    /** "You've checked in 3 times · Last on Sep 28". */
    fun checkInHistory(
        history: PlaceOwnHistoryDto,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): String? {
        if (history.checkInCount <= 0) return null
        val times = if (history.checkInCount == 1) "once" else "${history.checkInCount} times"
        val last = parseInstantMs(history.lastCheckInAt)?.let { " · Last on ${monthDay(it, timeZone)}" }.orEmpty()
        return "You've checked in $times$last"
    }

    fun clicksMadeHere(count: Int): String? =
        when {
            count <= 0 -> null
            count == 1 -> "1 Click made here"
            else -> "$count Clicks made here"
        }

    /** "Checked in · until 9:30 PM". */
    fun checkedIn(
        expiresAtMs: Long?,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): String = expiresAtMs?.let { "Checked in · until ${formatClockTime(it, timeZone)}" } ?: "Checked in"

    /** Me → Places row: "4 visits · Last Sep 28" (iOS `MyPlacesView.visitsLine`). */
    fun visitsLine(
        visit: MyPlaceVisit,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): String {
        val count = if (visit.checkInCount == 1) "1 visit" else "${visit.checkInCount} visits"
        val last = visit.lastCheckInAtMs ?: return count
        return "$count · Last ${monthDay(last, timeZone)}"
    }

    fun metPeopleHere(count: Int): String? =
        when {
            count <= 0 -> null
            count == 1 -> "Met 1 person here"
            else -> "Met $count people here"
        }

    /** "You're about {d rounded to 50 m / 0.1 mi} away. Check in when you're here." */
    fun outOfBounds(
        distanceMeters: Double?,
        metric: Boolean,
    ): String {
        if (distanceMeters == null || !distanceMeters.isFinite() || distanceMeters <= 0) {
            return "You're not here yet. Check in when you're here."
        }
        val away =
            if (metric) {
                val rounded = max(50.0, (distanceMeters / 50).roundToLong() * 50.0)
                if (rounded >= 1000) "${oneDecimal(rounded / 1000)} km" else "${rounded.roundToInt()} m"
            } else {
                val miles = max(0.1, (distanceMeters / 1609.344 * 10).roundToLong() / 10.0)
                "${oneDecimal(miles)} mi"
            }
        return "You're about $away away. Check in when you're here."
    }

    /** Public share link; never includes an anchor token. */
    fun shareUrl(slug: String): String = "https://joinclick.co/p/$slug"

    const val MANAGER_NOTE = "You manage this Place. Edit it on joinclick.co/business."
    const val MANAGER_URL = "https://joinclick.co/business/places"

    private fun oneDecimal(value: Double): String {
        val tenths = (value * 10).roundToLong()
        return "${tenths / 10}.${tenths % 10}"
    }

    private fun monthDay(
        epochMs: Long,
        timeZone: TimeZone,
    ): String {
        val dt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(timeZone)
        val month =
            dt.month.name
                .lowercase()
                .replaceFirstChar { it.uppercase() }
                .take(3)
        return "$month ${dt.dayOfMonth}"
    }
}
