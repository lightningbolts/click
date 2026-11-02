package compose.project.click.click.viewmodel // pragma: allowlist secret

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.flags.FeatureFlag // pragma: allowlist secret
import compose.project.click.click.data.flags.FeatureFlags // pragma: allowlist secret
import compose.project.click.click.data.places.PlaceOpenRequests // pragma: allowlist secret
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
    lastPlacesFetch = PlacesFetchMark(latitude, longitude, now)
    placesFetchJob?.cancel()
    placesFetchJob =
        viewModelScope.launch {
            placesRepository
                .nearby(latitude, longitude, MapPlacesLayer.NEARBY_RADIUS_METERS)
                .onSuccess { rows ->
                    _mapPlaces.update { current -> MapPlacesLayer.merge(current, rows) }
                    PlacesMapCache.update(_mapPlaces.value)
                }
        }
}

/** A Place pin (or its row in a stack list) opens the Place page with `source=map`. */
internal fun MapViewModel.openPlaceFromPin(placeId: String) {
    _selection.value = MapSelection.None
    PlaceOpenRequests.open(placeId, source = "map")
}
