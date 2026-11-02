package compose.project.click.click.ui.screens

import compose.project.click.click.data.models.MapBeacon
import compose.project.click.click.data.models.MapBeaconKind
import compose.project.click.click.data.models.MapBeaconMetadata
import compose.project.click.click.data.places.PlaceEventRefDto
import compose.project.click.click.data.places.PlaceSummaryDto
import compose.project.click.click.data.places.PulseSummaryDto
import compose.project.click.click.ui.utils.MapRenderData
import compose.project.click.click.viewmodel.MapLayerFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Places section of the Nearby list (iOS `MapFeatureModel.relevanceSections`, `NearbyListView`). */
class NearbyPlacesTest {
    private val now = 1_800_000_000_000L

    private fun summary(
        id: String,
        name: String = id,
        distance: Double? = null,
        liveEvent: Boolean = false,
        pulseAt: String? = null,
        hereNow: Int = 0,
    ) = PlaceSummaryDto(
        id = id,
        name = name,
        category = "cafe",
        latitude = 47.0,
        longitude = -122.0,
        distanceMeters = distance,
        city = "Seattle",
        addressLine = "4214 University Way NE",
        hereNowCount = hereNow,
        nextEvent = if (liveEvent) PlaceEventRefDto(beaconId = "e-$id", title = "Open mic", isLive = true) else null,
        pulse = if (pulseAt != null) PulseSummaryDto(state = "live", newestAt = pulseAt) else PulseSummaryDto(),
    )

    private fun place(
        id: String,
        distance: Double,
        liveEvent: Boolean = false,
        pulseAt: String? = null,
    ) = DiscoveryFeedItem.Place(summary(id, liveEvent = liveEvent, pulseAt = pulseAt), distance)

    private fun event(
        id: String,
        distance: Double,
    ) = DiscoveryFeedItem.Beacon(
        beacon = MapBeacon(id = id, kind = MapBeaconKind.EVENT, latitude = 0.0, longitude = 0.0, metadata = MapBeaconMetadata(title = id)),
        distanceM = distance,
        ttlLabel = "",
    )

    private fun soundtrack(id: String) =
        DiscoveryFeedItem.Beacon(
            beacon =
                MapBeacon(
                    id = id,
                    kind = MapBeaconKind.SOUNDTRACK,
                    latitude = 0.0,
                    longitude = 0.0,
                    metadata = MapBeaconMetadata(title = id),
                ),
            distanceM = 10.0,
            ttlLabel = "",
        )

    @Test
    fun section_sitsAfterEventsOrFirstWithoutThem() {
        val withEvents = groupDiscoveryFeedIntoSections(listOf(soundtrack("s"), event("e", 5.0), place("p", 1.0)))
        assertEquals(listOf("Soundtracks", "Events", "Places"), withEvents.map { it.title })
        val withoutEvents = groupDiscoveryFeedIntoSections(listOf(soundtrack("s"), place("p", 1.0)))
        assertEquals(listOf("Places", "Soundtracks"), withoutEvents.map { it.title })
        assertTrue(groupDiscoveryFeedIntoSections(listOf(soundtrack("s"))).none { it.title == NEARBY_PLACES_SECTION_TITLE })
    }

    @Test
    fun relevance_ordersPlacesByLiveEventThenPulseThenDistance() {
        val items =
            listOf(
                place("near", 10.0),
                place("pulse", 900.0, pulseAt = "2026-10-01T19:00:00Z"),
                place("event", 2_000.0, liveEvent = true),
                place("far", 500.0),
            )
        val relevance = nearbySections(items, NearbySort.Relevance, "", now).single()
        assertEquals(listOf("event", "pulse", "near", "far"), relevance.items.map { (it as DiscoveryFeedItem.Place).place.id })
        // Any other order applies to Places like every row.
        val distance = nearbySections(items, NearbySort.Distance, "", now).single()
        assertEquals(listOf("near", "far", "pulse", "event"), distance.items.map { (it as DiscoveryFeedItem.Place).place.id })
    }

    @Test
    fun search_matchesNameCategoryAddressCityAndEvent() {
        val item = DiscoveryFeedItem.Place(summary("p1", name = "Café Allegro", liveEvent = true), 10.0)
        assertTrue(item.matchesNearbyQuery("allegro"))
        assertTrue(item.matchesNearbyQuery("cafe"))
        assertTrue(item.matchesNearbyQuery("university way"))
        assertTrue(item.matchesNearbyQuery("seattle"))
        assertTrue(item.matchesNearbyQuery("open mic"))
        assertTrue(item.matchesNearbyQuery("places"))
        assertFalse(item.matchesNearbyQuery("trivia"))
        assertEquals("Café Allegro", item.nearbyTitle)
    }

    @Test
    fun rising_usesHereNowCount() {
        val item = DiscoveryFeedItem.Place(summary("p1", hereNow = 4), 10.0)
        assertEquals(4, item.nearbyPeopleCount)
        assertEquals("place-p1", item.key)
    }

    @Test
    fun feedItems_measureFromTheUserElseUseTheServerDistance() {
        val p = summary("p1", distance = 321.0)
        val fromServer =
            buildDiscoveryFeedItems(emptyList(), emptyList(), MapRenderData.IndividualPins(emptyList()), null, null, places = listOf(p))
        assertEquals(321.0, (fromServer.single() as DiscoveryFeedItem.Place).distanceM)
        val fromUser =
            buildDiscoveryFeedItems(emptyList(), emptyList(), MapRenderData.IndividualPins(emptyList()), 47.0, -122.0, places = listOf(p))
        assertEquals(0.0, (fromUser.single() as DiscoveryFeedItem.Place).distanceM, 0.5)
        val unknown =
            buildDiscoveryFeedItems(
                emptyList(),
                emptyList(),
                MapRenderData.IndividualPins(emptyList()),
                null,
                null,
                places = listOf(p.copy(distanceMeters = null)),
            )
        assertEquals(Double.MAX_VALUE, (unknown.single() as DiscoveryFeedItem.Place).distanceM)
    }

    @Test
    fun layerChips_listPlacesOnlyWithTheFlag() {
        assertFalse(MapLayerFilter.PLACES in nearbyLayerChips(placesEnabled = false))
        val on = nearbyLayerChips(placesEnabled = true)
        assertEquals(MapLayerFilter.PLACES, on[on.indexOf(MapLayerFilter.EVENTS) + 1])
    }
}
