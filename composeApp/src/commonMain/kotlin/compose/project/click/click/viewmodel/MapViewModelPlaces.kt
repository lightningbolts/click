package compose.project.click.click.viewmodel // pragma: allowlist secret

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.flags.FeatureFlag // pragma: allowlist secret
import compose.project.click.click.data.flags.FeatureFlags // pragma: allowlist secret
import compose.project.click.click.data.places.PlaceFilters // pragma: allowlist secret
import compose.project.click.click.data.places.PlaceOpenRequests // pragma: allowlist secret
import compose.project.click.click.data.places.PlaceSummaryDto // pragma: allowlist secret
import compose.project.click.click.data.places.PlacesMapCache // pragma: allowlist secret
import compose.project.click.click.ui.utils.MapPlacesLayer // pragma: allowlist secret
import compose.project.click.click.ui.utils.PlacesFetchMark // pragma: allowlist secret
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Loads Click Places around [latitude]/[longitude] for the map's Places layer
 * (`GET /api/places/nearby`, 5 km like iOS `BeaconRepository.nearbyPlaces`). The route is rate
 * limited, so a refresh waits until the map moved [MapPlacesLayer.REFETCH_DISTANCE_METERS] or
 * [MapPlacesLayer.REFETCH_AFTER_MS] passed. Filtering and presence are the server's.
 */
internal fun MapViewModel.refreshMapPlacesAround(
    latitude: Double,
    longitude: Double,
    force: Boolean = false,
) {
    if (!MapPlacesLayer.isVisible(_selectedLayerFilters.value, FeatureFlags.isEnabled(FeatureFlag.CLICK_PLACES))) return
    val now = Clock.System.now().toEpochMilliseconds()
    if (!force && !MapPlacesLayer.shouldRefetch(lastPlacesFetch, latitude, longitude, now)) return
    // A request already on its way for about here answers this one too.
    if (!force &&
        placesFetchJob?.isActive == true &&
        !MapPlacesLayer.shouldRefetch(placesFetchInFlight, latitude, longitude, now)
    ) {
        return
    }
    val mark = PlacesFetchMark(latitude, longitude, now)
    placesFetchInFlight = mark
    placesFetchJob?.cancel()
    placesFetchJob =
        viewModelScope.launch {
            placesRepository
                .nearby(latitude, longitude, MapPlacesLayer.NEARBY_RADIUS_METERS)
                .onSuccess { rows ->
                    // Superseded (newer fetch, or the user changed): drop it.
                    if (placesFetchInFlight !== mark) return@onSuccess
                    // The full throttle starts only after a success.
                    lastPlacesFetch = mark
                    _mapPlaces.update { current -> MapPlacesLayer.merge(current, rows) }
                    PlacesMapCache.update(_mapPlaces.value)
                }.onFailure {
                    if (placesFetchInFlight !== mark) return@onFailure
                    // Offline, 5xx or 429: try again after a short pause (or at once on
                    // pull-to-refresh) instead of the full 2 min, without hammering the rate limit.
                    lastPlacesFetch =
                        mark.copy(atEpochMs = mark.atEpochMs - MapPlacesLayer.REFETCH_AFTER_MS + PLACES_RETRY_AFTER_FAILURE_MS)
                }
        }
}

/** After a failed `places/nearby`, the next camera move or refresh may ask again this soon. */
internal const val PLACES_RETRY_AFTER_FAILURE_MS: Long = 15_000L

/** Places the map draws right now (same rule as [MapViewModel.visibleMapPlaces]). */
internal fun MapViewModel.currentVisibleMapPlaces(): List<PlaceSummaryDto> =
    _placeFilters.value.apply(
        MapPlacesLayer.visiblePlaces(
            _mapPlaces.value,
            _selectedLayerFilters.value,
            FeatureFlags.isEnabled(FeatureFlag.CLICK_PLACES),
        ),
    )

/** Drops the previous account's Places and throttle so the next user fetches their own. */
internal fun MapViewModel.resetMapPlaces() {
    placesFetchJob?.cancel()
    placesFetchJob = null
    placesFetchInFlight = null
    lastPlacesFetch = null
    _mapPlaces.value = emptyList()
}

/** A Place pin (or its row in a stack list) opens the Place page with `source=map`. */
internal fun MapViewModel.openPlaceFromPin(placeId: String) {
    _selection.value = MapSelection.None
    PlaceOpenRequests.open(placeId, source = "map")
}

/** Reads the saved Place filters (`places.filters.v1`); a corrupt value reads as none. */
internal suspend fun MapViewModel.hydratePlaceFilters() {
    val stored = runCatching { tokenStorage.getPlaceFilters() }.getOrNull()
    // A change made before the read finished wins.
    if (_placeFilters.value == PlaceFilters.NONE) _placeFilters.value = PlaceFilters.decode(stored)
}

/** Applies and saves Place filters (iOS `placeFilters.didSet { save() }`). */
internal fun MapViewModel.setPlaceFilters(filters: PlaceFilters) {
    if (_placeFilters.value == filters) return
    _placeFilters.value = filters
    viewModelScope.launch {
        runCatching { tokenStorage.savePlaceFilters(if (filters.isActive) filters.encode() else null) }
    }
}
