package compose.project.click.click.ui.utils

import compose.project.click.click.data.models.MapBeacon
import compose.project.click.click.data.models.MapBeaconKind
import compose.project.click.click.data.models.MapBeaconMetadata
import compose.project.click.click.data.places.PlaceEventRefDto
import compose.project.click.click.data.places.PlaceSummaryDto
import compose.project.click.click.data.places.PulseSummaryDto
import compose.project.click.click.ui.components.MapPin
import compose.project.click.click.ui.components.MapPinKind
import compose.project.click.click.ui.theme.BeaconPinShape
import compose.project.click.click.viewmodel.MapLayerFilter
import compose.project.click.click.viewmodel.defaultMapLayerFilters
import compose.project.click.click.viewmodel.visibleMapLayerFilters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Places layer: pins, stacking rank, the event merge and fetch policy (iOS `MapFeatureModel`). */
class MapPlacesLayerTest {
    private val now = 1_800_000_000_000L
    private val lat = 47.6588
    private val lon = -122.3131

    // ~100 m of latitude.
    private val hundredMeters = 0.0009

    private fun place(
        id: String = "p1",
        name: String = "Café Allegro",
        latitude: Double = lat,
        longitude: Double = lon,
        radius: Int = 75,
        pulse: PulseSummaryDto = PulseSummaryDto(),
        nextEvent: PlaceEventRefDto? = null,
        eventsToday: Int = 0,
        photo: String? = null,
    ) = PlaceSummaryDto(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radius,
        pulse = pulse,
        nextEvent = nextEvent,
        eventsTodayCount = eventsToday,
        photoUrl = photo,
    )

    private fun event(
        id: String,
        venueId: String?,
        latitude: Double = lat,
        kind: MapBeaconKind = MapBeaconKind.EVENT,
    ) = MapBeacon(
        id = id,
        kind = kind,
        latitude = latitude,
        longitude = lon,
        metadata = MapBeaconMetadata(title = id),
        venueId = venueId,
    )

    @Test
    fun placePin_isARoundedSquareKeyedByPlace() {
        val pin = MapPin.fromPlace(place(photo = " https://x/p.jpg "))
        assertEquals("place:p1", pin.id)
        assertEquals(MapPinKind.PLACE, pin.kind)
        assertEquals(BeaconPinShape.ROUNDED_SQUARE, pin.pinShape)
        assertEquals("https://x/p.jpg", pin.imageUrl)
        assertEquals("CA", pin.avatarInitials)
        assertEquals("Café Allegro", pin.caption)
        assertNull(pin.beaconKind)
        assertEquals("p1", MapPlacesLayer.placeIdFromPinId(pin.id))
        assertNull(MapPlacesLayer.placeIdFromPinId("hub:p1"))
        assertNull(MapPlacesLayer.placeIdFromPinId("place:"))
    }

    @Test
    fun placePin_liveBadgeIsACornerBadgeAndCaptionStaysTheName() {
        val live = MapPin.fromPlace(place(nextEvent = PlaceEventRefDto(beaconId = "e1", title = "Trivia", isLive = true)))
        assertEquals("LIVE", live.cornerBadge)
        assertEquals("Café Allegro", live.caption)
        assertNull(MapPin.fromPlace(place()).cornerBadge)
    }

    @Test
    fun placePin_ringFollowsLivePulseOnly() {
        val livePulse = MapPin.fromPlace(place(pulse = PulseSummaryDto(state = "live", label = "packed")))
        val stalePulse = MapPin.fromPlace(place(pulse = PulseSummaryDto(state = "stale", label = "packed")))
        val liveChill = MapPin.fromPlace(place(pulse = PulseSummaryDto(state = "live", label = "chill")))
        assertTrue(livePulse.ringArgb != null)
        assertNull(stalePulse.ringArgb)
        assertTrue(livePulse.ringArgb != liveChill.ringArgb)
        // The fill no longer carries the energy: it is the Place's stable placeholder.
        assertEquals(livePulse.avatarFillArgb, stalePulse.avatarFillArgb)
    }

    @Test
    fun stackRank_placesSitBetweenEventsAndHubs() {
        val placePin = MapPin.fromPlace(place())
        val hub = MapPin(id = "hub:h", title = "Hub", latitude = lat, longitude = lon, kind = MapPinKind.COMMUNITY_HUB)
        val eventPin =
            MapPin(
                id = "beacon:e",
                title = "Event",
                latitude = lat,
                longitude = lon,
                kind = MapPinKind.BEACON_SOCIAL,
                beaconKind = MapBeaconKind.EVENT,
            )
        val person = MapPin(id = "c1", title = "Maya", latitude = lat, longitude = lon)
        assertEquals(2, mapPinStackRank(placePin, now))
        val stack = stackMapPins(listOf(person, hub, placePin, eventPin), 16.0, now).single()
        assertEquals(listOf("beacon:e", "place:p1", "hub:h", "c1"), stack.members.map { it.id })
    }

    @Test
    fun stackTitle_countsPlaces() {
        val pins = listOf(MapPin.fromPlace(place("a")), MapPin.fromPlace(place("b")))
        assertEquals("2 Places", mapPinStackTitle(pins))
    }

    @Test
    fun eventMerge_dropsEventsInsideTheirPlace() {
        val places = listOf(place(radius = 75))
        val atPlace = event("inside", venueId = "p1", latitude = lat + hundredMeters)
        val farAway = event("far", venueId = "p1", latitude = lat + hundredMeters * 5)
        val otherVenue = event("other", venueId = "p2")
        val noVenue = event("none", venueId = null)
        val social = event("social", venueId = "p1", kind = MapBeaconKind.SOCIAL_VIBE)
        val kept = MapPlacesLayer.beaconsOutsidePlaces(listOf(atPlace, farAway, otherVenue, noVenue, social), places)
        assertEquals(listOf("far", "other", "none", "social"), kept.map { it.id })
    }

    @Test
    fun eventMerge_keepsEverythingWithoutPlaces() {
        val events = listOf(event("inside", venueId = "p1"))
        assertEquals(events, MapPlacesLayer.beaconsOutsidePlaces(events, emptyList()))
    }

    @Test
    fun visibility_needsFlagAndLayer() {
        val places = listOf(place())
        assertTrue(MapPlacesLayer.isVisible(setOf(MapLayerFilter.PLACES), placesEnabled = true))
        assertTrue(MapPlacesLayer.isVisible(setOf(MapLayerFilter.ALL), placesEnabled = true))
        assertFalse(MapPlacesLayer.isVisible(setOf(MapLayerFilter.ALL), placesEnabled = false))
        assertFalse(MapPlacesLayer.isVisible(setOf(MapLayerFilter.EVENTS), placesEnabled = true))
        assertEquals(places, MapPlacesLayer.visiblePlaces(places, defaultMapLayerFilters(), placesEnabled = true))
        assertTrue(MapPlacesLayer.visiblePlaces(places, defaultMapLayerFilters(), placesEnabled = false).isEmpty())
    }

    @Test
    fun layerMenu_listsPlacesOnlyWithTheFlag() {
        assertTrue(MapLayerFilter.PLACES in visibleMapLayerFilters(placesEnabled = true))
        assertFalse(MapLayerFilter.PLACES in visibleMapLayerFilters(placesEnabled = false))
        assertEquals(MapLayerFilter.entries.size - 1, visibleMapLayerFilters(placesEnabled = false).size)
    }

    @Test
    fun refetch_waitsForDistanceOrTime() {
        val mark = PlacesFetchMark(lat, lon, now)
        assertTrue(MapPlacesLayer.shouldRefetch(null, lat, lon, now))
        assertFalse(MapPlacesLayer.shouldRefetch(mark, lat + hundredMeters, lon, now + 1_000))
        assertTrue(MapPlacesLayer.shouldRefetch(mark, lat + hundredMeters * 20, lon, now + 1_000))
        assertTrue(MapPlacesLayer.shouldRefetch(mark, lat, lon, now + MapPlacesLayer.REFETCH_AFTER_MS))
    }

    @Test
    fun merge_freshRowsReplaceAndLeadTheList() {
        val old = listOf(place("a", name = "Old A"), place("b"))
        val merged = MapPlacesLayer.merge(old, listOf(place("a", name = "New A"), place("c"), place("c")))
        assertEquals(listOf("a", "c", "b"), merged.map { it.id })
        assertEquals("New A", merged.first().name)
    }

    @Test
    fun merge_capsTheList() {
        val many = (0 until MapPlacesLayer.MAX_PLACES + 10).map { place("p$it") }
        assertEquals(MapPlacesLayer.MAX_PLACES, MapPlacesLayer.merge(emptyList(), many).size)
    }
}
