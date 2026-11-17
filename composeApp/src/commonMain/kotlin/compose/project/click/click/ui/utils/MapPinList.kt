package compose.project.click.click.ui.utils // pragma: allowlist secret

import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.places.PlaceSummaryDto // pragma: allowlist secret
import compose.project.click.click.ui.components.MapPin // pragma: allowlist secret
import compose.project.click.click.util.oneToOnePeerPairKey // pragma: allowlist secret

/**
 * The pins the map draws before stacking, most important (highest z) first. The drawn map and the
 * tap handler both build their list here, so a stack's badge and its list always agree.
 *
 * [places] must already be filtered (flag, layer and Place filters; see `MapViewModel.visibleMapPlaces`).
 * Duplicate connections for the same pair of people draw once; an event at a Place on the map
 * rides in the Place's pin (iOS `mergingPlaceEvents`).
 */
fun buildMapPins(
    renderData: MapRenderData,
    communityHubs: List<CommunityHubPin>,
    places: List<PlaceSummaryDto>,
    currentUserId: String?,
    connectedUsers: Map<String, User>,
    coreConnectionIds: Set<String>,
): List<MapPin> {
    val hubPins = communityHubs.map { MapPin.fromCommunityHub(it) } + places.map { MapPin.fromPlace(it) }
    return when (renderData) {
        is MapRenderData.IndividualPins -> {
            val conn =
                renderData.points
                    .distinctBy { oneToOnePeerPairKey(it.connection.user_ids) ?: it.connection.id }
                    .map { point ->
                        val peerId = point.connection.user_ids.firstOrNull { it != currentUserId }
                        val peer = peerId?.let { connectedUsers[it] }
                        MapPin.fromConnectionPoint(
                            point,
                            imageUrl = peer?.image,
                            avatarSeed = peerId ?: point.connection.id,
                            isCore = point.connection.id in coreConnectionIds,
                        )
                    }
            val bc = MapPlacesLayer.beaconsOutsidePlaces(renderData.beacons, places).map { MapPin.fromBeacon(it) }
            (conn + bc + hubPins).sortedByDescending { it.zIndex }
        }
        is MapRenderData.Clusters -> {
            val standalone =
                MapPlacesLayer.beaconsOutsidePlaces(renderData.standaloneBeacons, places).map { MapPin.fromBeacon(it) }
            (standalone + hubPins).sortedByDescending { it.zIndex }
        }
    }
}
