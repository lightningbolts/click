@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.places.PlaceOrdering // pragma: allowlist secret
import compose.project.click.click.data.places.PlaceSummaryDto // pragma: allowlist secret
import compose.project.click.click.events.eventSchedule // pragma: allowlist secret
import compose.project.click.click.events.formatEventScheduleRange // pragma: allowlist secret
import compose.project.click.click.events.isActiveForDiscoveryFeed // pragma: allowlist secret
import compose.project.click.click.ui.utils.CommunityHubPin // pragma: allowlist secret
import compose.project.click.click.ui.utils.ConnectionMapPoint // pragma: allowlist secret
import compose.project.click.click.ui.utils.MapRenderData // pragma: allowlist secret
import compose.project.click.click.ui.utils.discoveryFeedSubtitle // pragma: allowlist secret
import compose.project.click.click.ui.utils.haversineDistance // pragma: allowlist secret
import kotlinx.datetime.Clock

internal sealed class DiscoveryFeedItem {
    abstract val sortDistanceM: Double
    abstract val sortRecentEpochMs: Long
    abstract val key: String

    data class Hub(
        val hub: CommunityHubPin,
        val distanceM: Double,
        val ttlLabel: String,
    ) : DiscoveryFeedItem() {
        override val sortDistanceM: Double = distanceM
        override val sortRecentEpochMs: Long = 0L
        override val key: String = "hub-${hub.hubId}"
    }

    data class Beacon(
        val beacon: MapBeacon,
        val distanceM: Double,
        val ttlLabel: String,
    ) : DiscoveryFeedItem() {
        override val sortDistanceM: Double = distanceM
        override val sortRecentEpochMs: Long = beacon.createdAtEpochMs ?: 0L
        override val key: String = "beacon-${beacon.id}"
    }

    /** A Click Place (G5); present only while `click_places` is on and the Places layer shows. */
    data class Place(
        val place: PlaceSummaryDto,
        val distanceM: Double,
    ) : DiscoveryFeedItem() {
        override val sortDistanceM: Double = distanceM
        override val sortRecentEpochMs: Long = 0L
        override val key: String = "place-${place.id}"
    }

    data class Connection(
        val point: ConnectionMapPoint,
        val distanceM: Double,
    ) : DiscoveryFeedItem() {
        override val sortDistanceM: Double = distanceM
        override val sortRecentEpochMs: Long = point.connection.created
        override val key: String = "conn-${point.connection.id}"
    }
}

internal fun buildDiscoveryFeedItems(
    hubs: List<CommunityHubPin>,
    beacons: List<MapBeacon>,
    renderData: MapRenderData,
    userLat: Double?,
    userLon: Double?,
    places: List<PlaceSummaryDto> = emptyList(),
): List<DiscoveryFeedItem> {
    val now = Clock.System.now().toEpochMilliseconds()

    fun dist(
        lat: Double,
        lon: Double,
    ): Double =
        if (userLat != null && userLon != null) {
            haversineDistance(userLat, userLon, lat, lon)
        } else {
            Double.MAX_VALUE
        }

    val hubRows =
        hubs.map { hub ->
            DiscoveryFeedItem.Hub(
                hub = hub,
                distanceM = dist(hub.latitude, hub.longitude),
                ttlLabel = "Ephemeral · ${hub.activeUserCount} here",
            )
        }

    val beaconRows =
        beacons
            .filter { b -> b.isActiveForDiscoveryFeed(now) }
            .map { beacon ->
                val ttlLabel =
                    when (beacon.kind) {
                        MapBeaconKind.EVENT -> {
                            val scheduleLabel = beacon.eventSchedule()?.let { formatEventScheduleRange(it) }
                            val desc =
                                beacon.metadata.description
                                    ?.trim()
                                    ?.takeIf { it.isNotEmpty() }
                            when {
                                scheduleLabel != null -> scheduleLabel
                                desc != null -> if (desc.length > 56) desc.take(55) + "…" else desc
                                else -> "Scheduled event"
                            }
                        }
                        else -> beacon.discoveryFeedSubtitle(now)
                    }
                DiscoveryFeedItem.Beacon(
                    beacon = beacon,
                    distanceM = dist(beacon.latitude, beacon.longitude),
                    ttlLabel = ttlLabel,
                )
            }

    val connectionPoints =
        when (renderData) {
            is MapRenderData.IndividualPins -> renderData.points
            is MapRenderData.Clusters -> renderData.clusters.flatMap { it.points }
        }
    val connRows =
        connectionPoints.map { point ->
            DiscoveryFeedItem.Connection(
                point = point,
                distanceM = dist(point.latitude, point.longitude),
            )
        }

    val placeRows =
        places.map { place ->
            DiscoveryFeedItem.Place(
                place = place,
                distanceM =
                    if (userLat != null && userLon != null) {
                        dist(place.latitude, place.longitude)
                    } else {
                        place.distanceMeters ?: Double.MAX_VALUE
                    },
            )
        }

    return connRows + hubRows + beaconRows + placeRows
}

internal data class DiscoveryFeedSection(
    val title: String,
    val items: List<DiscoveryFeedItem>,
)

/** Section title for Click Places (iOS `MapLayer.places.label`). */
internal const val NEARBY_PLACES_SECTION_TITLE: String = "Places"

/**
 * Sections in list order. Places sit after Events (before them when there are none), as iOS
 * `MapFeatureModel.relevanceSections` puts them between events and hubs. With
 * [placesInPulseOrder] (Relevance) Places follow iOS `PlaceOrdering`: live event, then live Pulse
 * (newest first), then distance; otherwise they keep the order they came in.
 */
internal fun groupDiscoveryFeedIntoSections(
    items: List<DiscoveryFeedItem>,
    placesInPulseOrder: Boolean = false,
): List<DiscoveryFeedSection> {
    if (items.isEmpty()) return emptyList()
    val sections = mutableListOf<DiscoveryFeedSection>()
    val hubs = items.filterIsInstance<DiscoveryFeedItem.Hub>()
    if (hubs.isNotEmpty()) {
        sections += DiscoveryFeedSection(title = "Community hubs", items = hubs)
    }
    val beacons = items.filterIsInstance<DiscoveryFeedItem.Beacon>()
    if (beacons.isNotEmpty()) {
        beacons
            .groupBy { it.beacon.kind }
            .entries
            .sortedBy { (kind, _) -> kind.ordinal }
            .forEach { (kind, rows) ->
                val pluralTitle =
                    when (kind) {
                        MapBeaconKind.SOUNDTRACK -> "Soundtracks"
                        MapBeaconKind.SOS -> "SOS beacons"
                        MapBeaconKind.HAZARD -> "Hazards"
                        MapBeaconKind.UTILITY -> "Utilities"
                        MapBeaconKind.STUDY -> "Study spots"
                        MapBeaconKind.SOCIAL_VIBE -> "Social vibes"
                        MapBeaconKind.EVENT -> "Events"
                        MapBeaconKind.OTHER -> "Beacons"
                    }
                sections +=
                    DiscoveryFeedSection(
                        title = pluralTitle,
                        items = rows,
                    )
            }
    }
    val places = items.filterIsInstance<DiscoveryFeedItem.Place>()
    if (places.isNotEmpty()) {
        val rows =
            if (placesInPulseOrder) {
                PlaceOrdering.sorted(places, { it.place }, { it.distanceM })
            } else {
                places
            }
        val eventsIndex = sections.indexOfFirst { it.title == "Events" }
        sections.add(if (eventsIndex >= 0) eventsIndex + 1 else 0, DiscoveryFeedSection(NEARBY_PLACES_SECTION_TITLE, rows))
    }
    return sections
}
