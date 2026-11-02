package compose.project.click.click.ui.screens

import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.MapBeacon
import compose.project.click.click.data.models.MapBeaconKind
import compose.project.click.click.data.models.MapBeaconMetadata
import compose.project.click.click.ui.utils.CommunityHubPin
import compose.project.click.click.ui.utils.ConnectionMapPoint
import compose.project.click.click.ui.utils.TimeState
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NearbyListModelTest {
    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    private fun beacon(
        id: String,
        title: String,
        distance: Double,
        kind: MapBeaconKind = MapBeaconKind.SOCIAL_VIBE,
        createdAt: Long? = null,
        rsvp: Int? = null,
        location: String? = null,
        startAt: String? = null,
        endAt: String? = null,
    ): DiscoveryFeedItem.Beacon {
        val raw =
            if (startAt != null && endAt != null) {
                buildJsonObject {
                    put("event_start_at", JsonPrimitive(startAt))
                    put("event_end_at", JsonPrimitive(endAt))
                }
            } else {
                null
            }
        return DiscoveryFeedItem.Beacon(
            beacon =
                MapBeacon(
                    id = id,
                    kind = kind,
                    latitude = 0.0,
                    longitude = 0.0,
                    metadata = MapBeaconMetadata(title = title, locationName = location, raw = raw),
                    createdAtEpochMs = createdAt,
                    rsvpCount = rsvp,
                ),
            distanceM = distance,
            ttlLabel = "",
        )
    }

    private fun hub(
        name: String,
        distance: Double,
        here: Int = 0,
    ) = DiscoveryFeedItem.Hub(
        hub = CommunityHubPin(hubId = name, name = name, latitude = 0.0, longitude = 0.0, radiusMeters = 100, activeUserCount = here),
        distanceM = distance,
        ttlLabel = "Ephemeral · $here here",
    )

    private fun ids(items: List<DiscoveryFeedItem>) = items.map { it.nearbyTitle }

    @Test
    fun distance_nearestFirstThenAlphabetical() {
        val items = listOf(beacon("1", "Bravo", 500.0), beacon("2", "Alpha", 500.0), beacon("3", "Zulu", 10.0))
        assertEquals(listOf("Zulu", "Alpha", "Bravo"), ids(sortNearbyItems(items, NearbySort.Distance, now)))
    }

    @Test
    fun distance_withoutLocationFallsBackToAlphabetical() {
        val far = Double.MAX_VALUE
        val items = listOf(beacon("1", "beta", far), beacon("2", "Alpha", far))
        assertEquals(listOf("Alpha", "beta"), ids(sortNearbyItems(items, NearbySort.Distance, now)))
    }

    @Test
    fun alphabetical_isCaseInsensitiveAndNatural() {
        val items = listOf(beacon("1", "Room 10", 1.0), beacon("2", "room 9", 1.0), beacon("3", "Atrium", 1.0))
        assertEquals(listOf("Atrium", "room 9", "Room 10"), ids(sortNearbyItems(items, NearbySort.Alphabetical, now)))
    }

    @Test
    fun new_newestFirstUndatedLast() {
        val items =
            listOf(
                beacon("1", "Old", 1.0, createdAt = now - 5 * hour),
                hub("Hub", 1.0),
                beacon("2", "Fresh", 1.0, createdAt = now - hour),
            )
        assertEquals(listOf("Fresh", "Old", "Hub"), ids(sortNearbyItems(items, NearbySort.New, now)))
    }

    @Test
    fun rising_newEventFillingUpBeatsOldOneWithMore() {
        val items =
            listOf(
                beacon("1", "Old big", 1.0, kind = MapBeaconKind.EVENT, createdAt = now - 48 * hour, rsvp = 40),
                beacon("2", "New small", 1.0, kind = MapBeaconKind.EVENT, createdAt = now - hour, rsvp = 10),
                beacon("3", "Nobody", 1.0, kind = MapBeaconKind.EVENT, createdAt = now, rsvp = 0),
            )
        assertEquals(listOf("New small", "Old big", "Nobody"), ids(sortNearbyItems(items, NearbySort.Rising, now)))
    }

    @Test
    fun rising_scoreMatchesGravityFormula() {
        assertEquals(10.0 / 3.0.pow(1.5), nearbyRisingScore(10, now - hour, now), 1e-9)
        assertEquals(0.0, nearbyRisingScore(null, now, now))
        assertEquals(0.0, nearbyRisingScore(0, now, now))
        // Hubs have no posted date: no decay beyond the +2 h floor.
        assertEquals(4.0 / 2.0.pow(1.5), nearbyRisingScore(4, null, now), 1e-9)
    }

    @Test
    fun relevance_eventsBySoonestThenNearest() {
        val items =
            listOf(
                beacon("1", "Later", 1.0, kind = MapBeaconKind.EVENT, startAt = "2027-02-01T10:00:00Z", endAt = "2027-02-01T12:00:00Z"),
                beacon("2", "Vibe far", 900.0),
                beacon("3", "Sooner", 800.0, kind = MapBeaconKind.EVENT, startAt = "2027-01-01T10:00:00Z", endAt = "2027-01-01T12:00:00Z"),
                beacon("4", "Vibe near", 5.0),
            )
        assertEquals(listOf("Sooner", "Later", "Vibe near", "Vibe far"), ids(sortNearbyItems(items, NearbySort.Relevance, now)))
    }

    @Test
    fun search_matchesTitleLocationAndKind_ignoringCaseAndAccents() {
        val cafe = beacon("1", "Café Nights", 1.0, location = "Pike Place")
        assertTrue(cafe.matchesNearbyQuery("cafe"))
        assertTrue(cafe.matchesNearbyQuery("  PIKE "))
        assertTrue(cafe.matchesNearbyQuery("social vibe"))
        assertTrue(cafe.matchesNearbyQuery(""))
        assertFalse(cafe.matchesNearbyQuery("museum"))
        assertTrue(hub("Library", 1.0).matchesNearbyQuery("hub"))
    }

    @Test
    fun search_matchesConnectionNameAndPlace() {
        val conn =
            DiscoveryFeedItem.Connection(
                point =
                    ConnectionMapPoint(
                        connection = Connection(id = "c1", created = 1L, expiry = 9_999_999_999L, user_ids = listOf("u1", "u2")),
                        latitude = 0.0,
                        longitude = 0.0,
                        timeState = TimeState.LIVE,
                        opacity = 1f,
                        shouldPulse = false,
                        displayName = "Alex",
                        locationLabel = "Campus",
                        formattedDate = "Today",
                    ),
                distanceM = 10.0,
            )
        assertTrue(conn.matchesNearbyQuery("alex"))
        assertTrue(conn.matchesNearbyQuery("campus"))
    }

    @Test
    fun sections_sortThenFilter() {
        val items = listOf(beacon("1", "Zeta jam", 1.0), beacon("2", "Alpha jam", 2.0), beacon("3", "Quiet", 0.5))
        val sections = nearbySections(items, NearbySort.Alphabetical, "jam", now)
        assertEquals(1, sections.size)
        assertEquals(listOf("Alpha jam", "Zeta jam"), ids(sections.single().items))
    }

    @Test
    fun noResultsCopyMatchesIos() {
        assertEquals("No results for “pizza”", nearbyNoResultsTitle(" pizza "))
        assertEquals("Try another name, place or filter.", NEARBY_NO_RESULTS_HINT)
        assertEquals("Search places, events, people", NEARBY_SEARCH_PROMPT)
    }

    @Test
    fun sortLabelsMatchIos() {
        assertEquals(listOf("Relevance", "Distance", "Rising", "New", "Alphabetical"), NearbySort.entries.map { it.label })
    }

    @Test
    fun naturalCompare_handlesDigitsAndCase() {
        assertTrue(naturalCompare("item 2", "Item 10") < 0)
        assertEquals(0, naturalCompare("ABC", "abc"))
        assertTrue(naturalCompare("abc", "abcd") < 0)
    }
}
