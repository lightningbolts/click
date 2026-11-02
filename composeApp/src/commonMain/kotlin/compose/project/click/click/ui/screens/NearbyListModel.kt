package compose.project.click.click.ui.screens // pragma: allowlist secret

import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import compose.project.click.click.events.eventSchedule // pragma: allowlist secret
import compose.project.click.click.ui.utils.displayDynamicTitle // pragma: allowlist secret
import compose.project.click.click.ui.utils.displayTypeTitle // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapLayerFilter // pragma: allowlist secret
import kotlin.math.max
import kotlin.math.pow

/**
 * How Nearby orders the rows in each section. Mirrors iOS `NearbySort`
 * (click-ios `Click/Features/Map/MapFeatureModel.swift`): Relevance is the curated order (events
 * by start, so live ones lead, everything else nearest first).
 */
internal enum class NearbySort(
    val label: String,
    /** Stored value; the same raw values as iOS `NearbySort`. */
    val storageValue: String,
) {
    Relevance("Relevance", "relevance"),
    Distance("Distance", "distance"),
    Rising("Rising", "rising"),
    New("New", "new"),
    Alphabetical("Alphabetical", "alphabetical"),
    ;

    companion object {
        /** A stored choice, or Relevance when there is none or it is unknown (iOS default). */
        fun fromStorage(raw: String?): NearbySort = entries.firstOrNull { it.storageValue == raw?.trim() } ?: Relevance
    }
}

/**
 * Keeps the Nearby sort across restarts in the app's existing settings store ([TokenStorage]), as
 * iOS keeps it in `UserDefaults` (`MapFeatureModel.sort`). A failed read or write is ignored.
 */
internal object NearbySortPreference {
    suspend fun load(storage: TokenStorage): NearbySort = NearbySort.fromStorage(runCatching { storage.getNearbySort() }.getOrNull())

    suspend fun save(
        storage: TokenStorage,
        sort: NearbySort,
    ) {
        runCatching { storage.saveNearbySort(sort.storageValue) }
    }
}

/** Row title used for search, A–Z order and stack ordering. */
internal val DiscoveryFeedItem.nearbyTitle: String
    get() =
        when (this) {
            is DiscoveryFeedItem.Beacon -> beacon.displayDynamicTitle()
            is DiscoveryFeedItem.Hub -> hub.name
            is DiscoveryFeedItem.Place -> place.name
            is DiscoveryFeedItem.Connection -> point.displayName
        }

/** People behind the row: going (events), in the hub right now. iOS `MapItem.peopleCount`. */
internal val DiscoveryFeedItem.nearbyPeopleCount: Int?
    get() =
        when (this) {
            is DiscoveryFeedItem.Beacon -> if (beacon.kind == MapBeaconKind.EVENT) beacon.rsvpCount else null
            is DiscoveryFeedItem.Hub -> hub.activeUserCount
            is DiscoveryFeedItem.Place -> place.hereNowCount
            is DiscoveryFeedItem.Connection -> null
        }

/** When it was posted; only beacons carry one (iOS `MapItem.createdAt`). */
internal val DiscoveryFeedItem.nearbyCreatedAtEpochMs: Long?
    get() = (this as? DiscoveryFeedItem.Beacon)?.beacon?.createdAtEpochMs

/**
 * Momentum for "Rising": people, decayed by age (Hacker News gravity), so a new event filling up
 * outranks an old one with more RSVPs. Hub counts are "right now": no decay.
 */
internal fun nearbyRisingScore(
    peopleCount: Int?,
    createdAtEpochMs: Long?,
    nowEpochMs: Long,
): Double {
    val people = peopleCount ?: return 0.0
    if (people <= 0) return 0.0
    val hours = createdAtEpochMs?.let { max(0L, nowEpochMs - it) / 3_600_000.0 } ?: 0.0
    return people / (hours + 2.0).pow(1.5)
}

/**
 * Case-insensitive natural order (digit runs compare by value), close to Foundation's
 * `localizedStandardCompare` that iOS sorts titles with.
 */
internal fun naturalCompare(
    a: String,
    b: String,
): Int {
    val x = foldForSearch(a)
    val y = foldForSearch(b)
    var i = 0
    var j = 0
    while (i < x.length && j < y.length) {
        val cx = x[i]
        val cy = y[j]
        if (cx.isDigit() && cy.isDigit()) {
            val si = i
            val sj = j
            while (i < x.length && x[i].isDigit()) i++
            while (j < y.length && y[j].isDigit()) j++
            val nx = x.substring(si, i).trimStart('0')
            val ny = y.substring(sj, j).trimStart('0')
            if (nx.length != ny.length) return nx.length - ny.length
            val c = nx.compareTo(ny)
            if (c != 0) return c
        } else {
            if (cx != cy) return cx.compareTo(cy)
            i++
            j++
        }
    }
    return (x.length - i) - (y.length - j)
}

/**
 * Rows in [sort] order. Ties go nearest first, then A–Z, so rows don't swap places between
 * rebuilds; without a location every distance is equal and distance order is A–Z. Port of iOS
 * `MapFeatureModel.sorted(_:by:from:now:)`.
 */
internal fun sortNearbyItems(
    items: List<DiscoveryFeedItem>,
    sort: NearbySort,
    nowEpochMs: Long,
): List<DiscoveryFeedItem> {
    class Keyed(
        val item: DiscoveryFeedItem,
        val title: String,
        val distance: Double,
        val created: Long,
        val rising: Double,
        val eventStart: Long,
    )
    val keyed =
        items.map { item ->
            Keyed(
                item = item,
                title = item.nearbyTitle,
                distance = item.sortDistanceM,
                created = item.nearbyCreatedAtEpochMs ?: Long.MIN_VALUE,
                rising = nearbyRisingScore(item.nearbyPeopleCount, item.nearbyCreatedAtEpochMs, nowEpochMs),
                eventStart =
                    (item as? DiscoveryFeedItem.Beacon)
                        ?.beacon
                        ?.takeIf { it.kind == MapBeaconKind.EVENT }
                        ?.eventSchedule()
                        ?.startEpochMs
                        ?: Long.MAX_VALUE,
            )
        }
    val alphabetical = Comparator<Keyed> { a, b -> naturalCompare(a.title, b.title) }
    val nearest = compareBy<Keyed> { it.distance }.then(alphabetical)
    val newest = compareByDescending<Keyed> { it.created }.then(nearest)
    val comparator: Comparator<Keyed> =
        when (sort) {
            // Events by start (a live one started earliest), then everything else nearest first.
            NearbySort.Relevance -> compareBy<Keyed> { it.eventStart }.then(nearest)
            NearbySort.Distance -> nearest
            NearbySort.Alphabetical -> alphabetical.then(compareBy { it.distance })
            NearbySort.New -> newest
            NearbySort.Rising -> compareByDescending<Keyed> { it.rising }.then(newest)
        }
    return keyed.sortedWith(comparator).map { it.item }
}

private val diacriticFolds: Map<Char, Char> =
    buildMap {
        "àáâãäåāăą".forEach { put(it, 'a') }
        "çćĉċč".forEach { put(it, 'c') }
        "ďđ".forEach { put(it, 'd') }
        "èéêëēĕėęě".forEach { put(it, 'e') }
        "ĝğġģ".forEach { put(it, 'g') }
        "ìíîïĩīĭįı".forEach { put(it, 'i') }
        "ñńņňŉ".forEach { put(it, 'n') }
        "òóôõöøōŏő".forEach { put(it, 'o') }
        "ŕŗř".forEach { put(it, 'r') }
        "śŝşš".forEach { put(it, 's') }
        "ţťŧ".forEach { put(it, 't') }
        "ùúûüũūŭůűų".forEach { put(it, 'u') }
        "ýÿŷ".forEach { put(it, 'y') }
        "źżž".forEach { put(it, 'z') }
    }

/** Lowercased with common Latin diacritics removed, like `localizedStandardContains`. */
internal fun foldForSearch(text: String): String =
    buildString(text.length) {
        text.lowercase().forEach { c -> append(diacriticFolds[c] ?: c) }
    }

/** Every field a Nearby search looks in for this row (iOS `NearbyListView.matches`, plus Android extras). */
internal fun DiscoveryFeedItem.nearbySearchFields(): List<String> =
    when (this) {
        is DiscoveryFeedItem.Beacon ->
            listOfNotNull(
                nearbyTitle,
                beacon.displayTypeTitle(),
                ttlLabel,
                beacon.metadata.locationName,
                beacon.metadata.formattedAddress,
                beacon.metadata.description,
                beacon.metadata.trackName,
                beacon.metadata.artistName ?: beacon.metadata.artist,
                beacon.creatorDisplayName,
            )
        is DiscoveryFeedItem.Hub -> listOf(nearbyTitle, "Hub", ttlLabel)
        // iOS `NearbyListView.matches`: category, address, city and the next event's title.
        is DiscoveryFeedItem.Place ->
            listOfNotNull(
                nearbyTitle,
                NEARBY_PLACES_SECTION_TITLE,
                place.placeCategory.label,
                place.addressLine,
                place.city,
                place.nextEvent?.title,
            )
        is DiscoveryFeedItem.Connection -> listOf(nearbyTitle, point.locationLabel)
    }

/** Whether [query] (trimmed; blank matches everything) appears in any of the row's fields. */
internal fun DiscoveryFeedItem.matchesNearbyQuery(query: String): Boolean {
    val q = foldForSearch(query.trim())
    if (q.isEmpty()) return true
    return nearbySearchFields().any { foldForSearch(it).contains(q) }
}

/** Sort, then search, then section: the Nearby list as rendered. */
internal fun nearbySections(
    items: List<DiscoveryFeedItem>,
    sort: NearbySort,
    query: String,
    nowEpochMs: Long,
): List<DiscoveryFeedSection> =
    groupDiscoveryFeedIntoSections(
        sortNearbyItems(items, sort, nowEpochMs).filter { it.matchesNearbyQuery(query) },
        placesInPulseOrder = sort == NearbySort.Relevance,
    )

/** Empty-list copy while searching (iOS `NearbyListView.list`). */
internal fun nearbyNoResultsTitle(query: String): String = "No results for “${query.trim()}”"

internal const val NEARBY_NO_RESULTS_HINT: String = "Try another name, place or filter."

internal const val NEARBY_SEARCH_PROMPT: String = "Search places, events, people"

/** Layer chips above the Nearby list; Places joins (after Events) only while `click_places` is on. */
internal fun nearbyLayerChips(placesEnabled: Boolean): List<MapLayerFilter> =
    listOfNotNull(
        MapLayerFilter.EVENTS,
        MapLayerFilter.PLACES.takeIf { placesEnabled },
        MapLayerFilter.COMMUNITY_HUBS,
        MapLayerFilter.SOCIAL_VIBES,
        MapLayerFilter.SOUNDTRACKS,
        MapLayerFilter.ALERTS_UTILITIES,
        MapLayerFilter.MY_CONNECTIONS,
    )
