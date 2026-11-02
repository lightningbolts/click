package compose.project.click.click.data.places

import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconPlace // pragma: allowlist secret
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A request to show a Place page: from a map pin, search, an event, Me → Places, a
 * `joinclick.co/p/{slug}` link or a check-in poster's QR (which carries [anchorToken]).
 * Mirrors iOS `AppRoute.place(idOrSlug:anchorToken:)` (click-ios `Click/App/AppRouter.swift`).
 */
data class PlaceOpenRequest(
    val idOrSlug: String,
    val anchorToken: String? = null,
    /** Reported once per open (server allowlist): map, nearby, event, link, qr or history. */
    val source: String? = null,
)

/** The one Place page the app shell shows; every entry point goes through [open]. */
object PlaceOpenRequests {
    private val _pending = MutableStateFlow<PlaceOpenRequest?>(null)
    val pending: StateFlow<PlaceOpenRequest?> = _pending.asStateFlow()

    fun open(request: PlaceOpenRequest) {
        val id = request.idOrSlug.trim()
        if (id.isEmpty()) return
        _pending.value = request.copy(idOrSlug = id, anchorToken = request.anchorToken?.trim()?.takeIf { it.isNotEmpty() })
    }

    fun open(
        idOrSlug: String,
        source: String,
    ) = open(PlaceOpenRequest(idOrSlug = idOrSlug, source = source))

    fun dismiss() {
        _pending.value = null
    }
}

/**
 * The Places the map last loaded around the user (iOS keeps them in the cached discovery), so
 * search can match nearby Places without its own request.
 */
object PlacesMapCache {
    private val _places = MutableStateFlow<List<PlaceSummaryDto>>(emptyList())
    val places: StateFlow<List<PlaceSummaryDto>> = _places.asStateFlow()

    fun update(places: List<PlaceSummaryDto>) {
        _places.value = places
    }

    fun clear() {
        _places.value = emptyList()
    }
}

/**
 * Places whose name, category, city or address contains [query], ignoring case
 * (iOS `SearchIndex.local`, click-ios `Click/Features/Search/GlobalSearchView.swift`).
 */
fun matchPlaces(
    query: String,
    places: List<PlaceSummaryDto>,
): List<PlaceSummaryDto> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()

    fun has(text: String?) = text?.contains(q, ignoreCase = true) == true
    return places
        .filter { has(it.name) || has(it.placeCategory.label) || has(it.city) || has(it.addressLine) }
        .distinctBy { it.id }
}

/** "Place · Café · Seattle · 3 here now" (iOS search row subtitle). */
fun placeSearchSubtitle(place: PlaceSummaryDto): String =
    listOfNotNull(
        "Place",
        place.placeCategory.label,
        place.city?.takeIf { it.isNotBlank() },
        place.hereNowCount.takeIf { it > 0 }?.let { "$it here now" },
    ).joinToString(" · ")

/**
 * The listed Place hosting an event, shown as its host with a link to its page; null with Places
 * off (iOS `BeaconDetailView.placeLink`).
 */
fun eventHostPlace(
    beacon: MapBeacon,
    placesEnabled: Boolean,
): MapBeaconPlace? = if (placesEnabled) beacon.place else null
