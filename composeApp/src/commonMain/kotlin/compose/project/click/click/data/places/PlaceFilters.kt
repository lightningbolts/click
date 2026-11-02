package compose.project.click.click.data.places // pragma: allowlist secret

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * Place filters (click-web spec §4.6, §6.6): the replacement for display thresholds. AND across
 * fields, OR within the set fields. Same semantics as iOS `PlaceFilters`
 * (click-ios `Click/Features/Places/PlaceFilters.swift`) and web `lib/places/filters.ts`; the
 * tests run the shared fixture both of those suites use.
 */
data class PlaceFilters(
    val categories: Set<PlaceCategory> = emptySet(),
    val pulseNow: Boolean = false,
    /** UI choices: Any (0), 2+, 5+, 10+. */
    val minReports: Int = 0,
    val energies: Set<EnergyLabel> = emptySet(),
    val eventsToday: Boolean = false,
    val openNow: Boolean = false,
    val beenHere: Boolean = false,
    val clicksBeenHere: Boolean = false,
    val hasHub: Boolean = false,
    val hereNow: Boolean = false,
) {
    val isActive: Boolean get() = this != NONE

    fun matches(place: PlaceSummaryDto): Boolean {
        val live = place.pulse.isLive
        if (categories.isNotEmpty() && place.placeCategory !in categories) return false
        if (pulseNow && !live) return false
        if (minReports > 0 && !(live && place.pulse.reportCount >= minReports)) return false
        if (energies.isNotEmpty()) {
            val label = place.pulse.energy
            if (!live || label == null || label !in energies) return false
        }
        if (eventsToday && !(place.eventsTodayCount > 0 || place.nextEvent?.isLive == true)) return false
        if (openNow && place.openNow != true) return false
        if (beenHere && place.viewer?.hasHistory != true) return false
        if (clicksBeenHere && (place.viewer?.connectionsBeenHereCount ?: 0) <= 0) return false
        if (hasHub && place.hubId == null) return false
        if (hereNow && place.hereNowCount <= 0) return false
        return true
    }

    /** Matching Places, input order kept. */
    fun apply(places: List<PlaceSummaryDto>): List<PlaceSummaryDto> = if (isActive) places.filter(::matches) else places

    /** JSON stored on the device under [STORAGE_KEY] (same field names as iOS and web). */
    fun encode(): String =
        placesJson.encodeToString(
            StoredPlaceFilters(
                categories = categories.map { it.wire }.sorted(),
                pulseNow = pulseNow,
                minReports = minReports,
                energies = energies.sortedBy { it.value }.map { it.wire },
                eventsToday = eventsToday,
                openNow = openNow,
                beenHere = beenHere,
                clicksBeenHere = clicksBeenHere,
                hasHub = hasHub,
                hereNow = hereNow,
            ),
        )

    companion object {
        val NONE = PlaceFilters()

        /** iOS stores the same value in UserDefaults under this key. */
        const val STORAGE_KEY: String = "places.filters.v1"

        /** Minimum-report choices in the filter sheet (iOS `PlaceFiltersSheet`). */
        val MIN_REPORT_CHOICES: List<Int> = listOf(0, 2, 5, 10)

        /** A missing or corrupt value reads as [NONE]; unknown categories/energies are dropped. */
        fun decode(raw: String?): PlaceFilters {
            if (raw.isNullOrBlank()) return NONE
            val stored = runCatching { placesJson.decodeFromString<StoredPlaceFilters>(raw) }.getOrNull() ?: return NONE
            return PlaceFilters(
                categories = stored.categories.mapNotNull { wire -> PlaceCategory.entries.firstOrNull { it.wire == wire } }.toSet(),
                pulseNow = stored.pulseNow,
                minReports = stored.minReports.coerceAtLeast(0),
                energies = stored.energies.mapNotNull { EnergyLabel.from(it) }.toSet(),
                eventsToday = stored.eventsToday,
                openNow = stored.openNow,
                beenHere = stored.beenHere,
                clicksBeenHere = stored.clicksBeenHere,
                hasHub = stored.hasHub,
                hereNow = stored.hereNow,
            )
        }
    }
}

@Serializable
private data class StoredPlaceFilters(
    val categories: List<String> = emptyList(),
    val pulseNow: Boolean = false,
    val minReports: Int = 0,
    val energies: List<String> = emptyList(),
    val eventsToday: Boolean = false,
    val openNow: Boolean = false,
    val beenHere: Boolean = false,
    val clicksBeenHere: Boolean = false,
    val hasHub: Boolean = false,
    val hereNow: Boolean = false,
)

/**
 * The quick chips above the Nearby list while Places is selected (iOS `PlaceFilterChips`,
 * click-ios `Click/Features/Places/PlaceFilterViews.swift`). Each chip reads and toggles one
 * part of [PlaceFilters]; "More…" opens the full sheet and "Clear" shows once anything is on.
 */
enum class PlaceFilterChip(
    val label: String,
) {
    PULSE_NOW("Pulse now"),
    LIVELY("Lively+"),
    QUIET("Quiet"),
    EVENTS_TODAY("Events today"),
    OPEN_NOW("Open now"),
    BEEN_HERE("Been here"),
    CLICKS_BEEN_HERE("Clicks have been"),
    ;

    fun isOn(filters: PlaceFilters): Boolean =
        when (this) {
            PULSE_NOW -> filters.pulseNow
            LIVELY -> filters.energies == LIVELY_ENERGIES
            QUIET -> filters.energies == QUIET_ENERGIES
            EVENTS_TODAY -> filters.eventsToday
            OPEN_NOW -> filters.openNow
            BEEN_HERE -> filters.beenHere
            CLICKS_BEEN_HERE -> filters.clicksBeenHere
        }

    fun toggle(filters: PlaceFilters): PlaceFilters =
        when (this) {
            PULSE_NOW -> filters.copy(pulseNow = !filters.pulseNow)
            LIVELY -> filters.copy(energies = if (filters.energies == LIVELY_ENERGIES) emptySet() else LIVELY_ENERGIES)
            QUIET -> filters.copy(energies = if (filters.energies == QUIET_ENERGIES) emptySet() else QUIET_ENERGIES)
            EVENTS_TODAY -> filters.copy(eventsToday = !filters.eventsToday)
            OPEN_NOW -> filters.copy(openNow = !filters.openNow)
            BEEN_HERE -> filters.copy(beenHere = !filters.beenHere)
            CLICKS_BEEN_HERE -> filters.copy(clicksBeenHere = !filters.clicksBeenHere)
        }

    companion object {
        val LIVELY_ENERGIES: Set<EnergyLabel> = setOf(EnergyLabel.LIVELY, EnergyLabel.PACKED)
        val QUIET_ENERGIES: Set<EnergyLabel> = setOf(EnergyLabel.CHILL, EnergyLabel.STEADY)
        const val MORE_LABEL: String = "More…"
        const val CLEAR_LABEL: String = "Clear"
    }
}

/** "Any", "2+", "5+", "10+" (iOS `PlaceFiltersSheet` picker). */
fun minReportsLabel(value: Int): String = if (value <= 0) "Any" else "$value+"

/**
 * Places in the Nearby list: live event first, then live Pulse (newest first), then distance.
 * iOS `PlaceOrdering.sorted`.
 */
object PlaceOrdering {
    fun sorted(places: List<PlaceSummaryDto>): List<PlaceSummaryDto> = sorted(places, { it }, { it.distanceMeters })

    /** Same order for rows that wrap a Place, measuring with [distance] (e.g. from the user). */
    fun <T> sorted(
        rows: List<T>,
        place: (T) -> PlaceSummaryDto,
        distance: (T) -> Double?,
    ): List<T> =
        rows.sortedWith { x, y ->
            val a = place(x)
            val b = place(y)
            val aLive = a.nextEvent?.isLive == true
            val bLive = b.nextEvent?.isLive == true
            if (aLive != bLive) return@sortedWith if (aLive) -1 else 1
            val aPulse = if (a.pulse.isLive) a.pulse.newestAtMs else null
            val bPulse = if (b.pulse.isLive) b.pulse.newestAtMs else null
            when {
                aPulse != null && bPulse != null && aPulse != bPulse -> bPulse.compareTo(aPulse)
                aPulse != null && bPulse == null -> -1
                aPulse == null && bPulse != null -> 1
                else -> (distance(x) ?: Double.POSITIVE_INFINITY).compareTo(distance(y) ?: Double.POSITIVE_INFINITY)
            }
        }
}
