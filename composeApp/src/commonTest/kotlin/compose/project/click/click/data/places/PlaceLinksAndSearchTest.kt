package compose.project.click.click.data.places

import compose.project.click.click.data.models.LocationPreferences
import compose.project.click.click.data.models.parseMapBeaconRows
import compose.project.click.click.deeplink.AppDeepLink
import compose.project.click.click.deeplink.AppDeepLinkRouter
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `/p/{slug}` links and QR anchors (iOS `AppRouter.parseIncomingURL`), search matching (iOS
 * `SearchIndex.local`), map copy, event → Place links and the Place visits privacy column.
 */
class PlaceLinksAndSearchTest {
    private val utc = TimeZone.UTC

    @AfterTest
    fun reset() {
        PlaceOpenRequests.dismiss()
        PlacesMapCache.clear()
    }

    // --- Deep links ---

    @Test
    fun customScheme_opensPlace() {
        assertEquals(AppDeepLink.Place("cafe-allegro", null), AppDeepLinkRouter.parse("click://p/cafe-allegro"))
        assertEquals(AppDeepLink.Place("cafe-allegro", "tok123"), AppDeepLinkRouter.parse("click://p/cafe-allegro?t=tok123"))
    }

    @Test
    fun universalLink_opensPlaceWithAnchorToken() {
        assertEquals(
            AppDeepLink.Place("cafe-allegro", "a b"),
            AppDeepLinkRouter.parse("https://joinclick.co/p/cafe-allegro?t=a%20b"),
        )
        assertEquals(AppDeepLink.Place("cafe-allegro", null), AppDeepLinkRouter.parse("https://www.joinclick.co/p/cafe-allegro/"))
        assertEquals(AppDeepLink.Place("x", null), AppDeepLinkRouter.parse("https://click-us.vercel.app/p/x?t="))
    }

    @Test
    fun placeLinks_rejectOtherShapes() {
        assertNull(AppDeepLinkRouter.parse("https://joinclick.co/p"))
        assertNull(AppDeepLinkRouter.parse("https://joinclick.co/p/"))
        assertNull(AppDeepLinkRouter.parse("https://joinclick.co/privacy"))
        assertNull(AppDeepLinkRouter.parse("https://evil.example/p/cafe"))
        assertNull(AppDeepLinkRouter.parse("click://p"))
    }

    @Test
    fun scanner_opensPlacePostersOnlyWithPlacesOn() {
        val poster = "https://joinclick.co/p/cafe-allegro?t=qr-token"
        assertEquals(AppDeepLink.Place("cafe-allegro", "qr-token"), AppDeepLinkRouter.placeLinkFromScan(poster, placesEnabled = true))
        assertNull(AppDeepLinkRouter.placeLinkFromScan(poster, placesEnabled = false))
        assertNull(AppDeepLinkRouter.placeLinkFromScan("https://joinclick.co/c/6a1f0000-0000-4000-8000-000000000001", true))
        assertNull(AppDeepLinkRouter.placeLinkFromScan("WIFI:S:cafe;;", true))
    }

    @Test
    fun openRequests_trimAndIgnoreBlank() {
        PlaceOpenRequests.open(PlaceOpenRequest(idOrSlug = "  ", source = "map"))
        assertNull(PlaceOpenRequests.pending.value)
        PlaceOpenRequests.open(PlaceOpenRequest(idOrSlug = " cafe ", anchorToken = " ", source = "link"))
        assertEquals(PlaceOpenRequest("cafe", null, "link"), PlaceOpenRequests.pending.value)
        PlaceOpenRequests.dismiss()
        assertNull(PlaceOpenRequests.pending.value)
    }

    // --- Search ---

    private fun place(
        id: String,
        name: String,
        category: String? = null,
        city: String? = null,
        address: String? = null,
        hereNow: Int = 0,
    ) = PlaceSummaryDto(
        id = id,
        name = name,
        category = category,
        city = city,
        addressLine = address,
        latitude = 47.0,
        longitude = -122.0,
        hereNowCount = hereNow,
    )

    @Test
    fun search_matchesNameCategoryCityAndAddress() {
        val places =
            listOf(
                place("1", "Café Allegro", category = "cafe", city = "Seattle"),
                place("2", "The Pub", category = "bar", address = "12 Allegro Lane"),
                place("3", "Gym One", category = "gym", city = "Tacoma"),
                place("1", "Café Allegro", category = "cafe", city = "Seattle"),
            )
        assertEquals(listOf("1", "2"), matchPlaces("allegro", places).map { it.id })
        assertEquals(listOf("1"), matchPlaces("CAFÉ", places).map { it.id })
        assertEquals(listOf("3"), matchPlaces(" tacoma ", places).map { it.id })
        assertEquals(listOf("2"), matchPlaces("bar", places).map { it.id })
        assertTrue(matchPlaces("   ", places).isEmpty())
    }

    @Test
    fun searchSubtitle_readsLikeIos() {
        assertEquals("Place · Café · Seattle · 3 here now", placeSearchSubtitle(place("1", "A", "cafe", "Seattle", hereNow = 3)))
        assertEquals("Place · Bar", placeSearchSubtitle(place("1", "A", "bar", " ")))
    }

    // --- Map copy ---

    private val nowMs = Instant.parse("2026-10-01T18:00:00Z").toEpochMilliseconds()

    @Test
    fun mapSubtitle_prefersLivePulseThenEventThenCategory() {
        val base = place("1", "A", category = "cafe")
        assertEquals(
            "Lively · 3 reports",
            PlaceCopy.mapSubtitle(base.copy(pulse = PulseSummaryDto(state = "live", label = "lively", reportCount = 3)), nowMs, utc),
        )
        assertEquals(
            "Live: Trivia",
            PlaceCopy.mapSubtitle(base.copy(nextEvent = PlaceEventRefDto("e", "Trivia", isLive = true)), nowMs, utc),
        )
        val tonight = base.copy(nextEvent = PlaceEventRefDto("e", "Trivia", startsAt = "2026-10-01T20:00:00Z"))
        assertTrue(PlaceCopy.mapSubtitle(tonight, nowMs, utc).startsWith("Tonight "))
        assertTrue(PlaceCopy.mapSubtitle(tonight, nowMs, utc).endsWith(" · Trivia"))
        val friday = base.copy(nextEvent = PlaceEventRefDto("e", "Trivia", startsAt = "2026-10-02T20:00:00Z"))
        assertTrue(PlaceCopy.mapSubtitle(friday, nowMs, utc).startsWith("Fri "))
        assertEquals("Café", PlaceCopy.mapSubtitle(base.copy(pulse = PulseSummaryDto(state = "stale", label = "chill")), nowMs, utc))
    }

    @Test
    fun pinBadge_isLiveOrTodaysStart() {
        val base = place("1", "A")
        assertEquals("LIVE", PlaceCopy.pinBadge(base.copy(nextEvent = PlaceEventRefDto("e", "T", isLive = true)), utc))
        val later = base.copy(nextEvent = PlaceEventRefDto("e", "T", startsAt = "2026-10-01T20:00:00Z"))
        assertNull(PlaceCopy.pinBadge(later, utc))
        assertTrue(PlaceCopy.pinBadge(later.copy(eventsTodayCount = 1), utc)?.isNotBlank() == true)
        assertNull(PlaceCopy.pinBadge(base, utc))
    }

    // --- Event → Place ---

    @Test
    fun eventPayload_carriesItsPlace() {
        val rows =
            parseMapBeaconRows(
                Json.parseToJsonElement(
                    """
                    [{"id":"e1","kind":"event","lat":47.6,"lon":-122.3,"venue_id":"p1",
                      "place":{"id":"p1","slug":"cafe-allegro","name":"Café Allegro","photo_url":"https://x/p.jpg","city":"Seattle"}},
                     {"id":"e2","kind":"event","lat":47.6,"lon":-122.3,"venue_id":"p2","place":null},
                     {"id":"e3","kind":"event","lat":47.6,"lon":-122.3,"place":{"slug":"no-id"}}]
                    """.trimIndent(),
                ),
            )
        val first = rows[0]
        assertEquals("p1", first.venueId)
        assertEquals("cafe-allegro", first.place?.slug)
        assertEquals("Seattle", first.place?.city)
        assertEquals("p1", eventHostPlace(first, placesEnabled = true)?.id)
        assertNull(eventHostPlace(first, placesEnabled = false))
        assertEquals("p2", rows[1].venueId)
        assertNull(rows[1].place)
        assertNull(rows[2].place)
    }

    // --- Privacy ---

    @Test
    fun placeVisitsColumn_onlyWithPlaces() {
        assertFalse(LocationPreferences.PLACE_VISITS_COLUMN in LocationPreferences.columns(includePlaceVisits = false))
        assertTrue(LocationPreferences.PLACE_VISITS_COLUMN in LocationPreferences.columns(includePlaceVisits = true))
        val prefs = LocationPreferences(placeVisitsVisible = true)
        assertFalse(LocationPreferences.PLACE_VISITS_COLUMN in prefs.writeRow(includePlaceVisits = false))
        assertEquals(true, prefs.writeRow(includePlaceVisits = true)[LocationPreferences.PLACE_VISITS_COLUMN])
        assertEquals(4, prefs.writeRow(includePlaceVisits = true).size)
    }

    @Test
    fun placeVisits_decodesAndDefaultsOff() {
        val json = Json { ignoreUnknownKeys = true }
        assertFalse(json.decodeFromString<LocationPreferences>("""{"location_connection_snap_enabled":true}""").placeVisitsVisible)
        assertTrue(json.decodeFromString<LocationPreferences>("""{"place_visits_visible_to_connections":true}""").placeVisitsVisible)
    }
}
