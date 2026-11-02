package compose.project.click.click.ui.utils // pragma: allowlist secret

import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.places.PlaceSummaryDto // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapLayerFilter // pragma: allowlist secret

/** Where and when the map last asked `GET /api/places/nearby`. */
data class PlacesFetchMark(
    val latitude: Double,
    val longitude: Double,
    val atEpochMs: Long,
)

/**
 * The map's Click Places layer (spec §6.5). Mirrors iOS `MapFeatureModel` (click-ios
 * `Click/Features/Map/MapFeatureModel.swift`) and `BeaconRepository.nearbyPlaces`.
 */
object MapPlacesLayer {
    /** iOS `BeaconRepository.placesRadiusMeters`. */
    const val NEARBY_RADIUS_METERS: Int = 5_000

    /** How far past a Place's radius its event may sit and still merge (geocoding drift). */
    const val MERGE_SLACK_METERS: Double = 100.0

    /** The nearby route is rate limited: pan this far, or wait this long, before asking again. */
    const val REFETCH_DISTANCE_METERS: Double = 1_500.0
    const val REFETCH_AFTER_MS: Long = 120_000L

    /** Places kept on the map at once; the oldest fall off first. */
    const val MAX_PLACES: Int = 400

    private const val PIN_PREFIX = "place:"

    fun pinId(placeId: String): String = "$PIN_PREFIX$placeId"

    fun placeIdFromPinId(pinId: String): String? =
        pinId
            .takeIf {
                it.startsWith(PIN_PREFIX)
            }?.removePrefix(PIN_PREFIX)
            ?.takeIf { it.isNotBlank() }

    /** Places show only with `click_places` on and the Places layer (or All) selected. */
    fun isVisible(
        layers: Set<MapLayerFilter>,
        placesEnabled: Boolean,
    ): Boolean = placesEnabled && (MapLayerFilter.ALL in layers || MapLayerFilter.PLACES in layers)

    fun visiblePlaces(
        places: List<PlaceSummaryDto>,
        layers: Set<MapLayerFilter>,
        placesEnabled: Boolean,
    ): List<PlaceSummaryDto> = if (isVisible(layers, placesEnabled)) places else emptyList()

    fun shouldRefetch(
        last: PlacesFetchMark?,
        latitude: Double,
        longitude: Double,
        nowEpochMs: Long,
    ): Boolean {
        if (last == null) return true
        if (nowEpochMs - last.atEpochMs >= REFETCH_AFTER_MS) return true
        return haversineDistance(last.latitude, last.longitude, latitude, longitude) >= REFETCH_DISTANCE_METERS
    }

    /** Refreshed rows replace older copies; new rows go first so the cap drops the stalest. */
    fun merge(
        current: List<PlaceSummaryDto>,
        incoming: List<PlaceSummaryDto>,
    ): List<PlaceSummaryDto> {
        val fresh = incoming.distinctBy { it.id }
        val ids = fresh.mapTo(HashSet()) { it.id }
        return (fresh + current.filterNot { it.id in ids }).take(MAX_PLACES)
    }

    /**
     * §6.5 event merge, for map pins only: an official event held at a Place on the map rides in
     * that Place's pin instead of stacking a second pin on it. An event the Place hosts somewhere
     * else, or whose Place isn't on the map, keeps its own pin. Lists and counts keep every event.
     * iOS `MapFeatureModel.mergingPlaceEvents`.
     */
    fun beaconsOutsidePlaces(
        beacons: List<MapBeacon>,
        places: List<PlaceSummaryDto>,
    ): List<MapBeacon> {
        if (places.isEmpty()) return beacons
        val byId = places.associateBy { it.id }
        return beacons.filter { beacon ->
            if (beacon.kind != MapBeaconKind.EVENT) return@filter true
            val place = beacon.venueId?.let { byId[it] } ?: return@filter true
            haversineDistance(beacon.latitude, beacon.longitude, place.latitude, place.longitude) >
                place.radiusMeters + MERGE_SLACK_METERS
        }
    }
}
