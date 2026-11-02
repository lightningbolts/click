package compose.project.click.click.data.places

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Copy follows iOS `PlaceCopy.swift` / web `lib/places/labels.ts`. */
class PlaceCopyTest {
    private val now = Instant.parse("2026-10-01T20:00:00Z").toEpochMilliseconds()
    private val fourMinAgo = "2026-10-01T19:56:00Z"

    @Test
    fun reportsAndAge() {
        assertEquals("1 report", PlaceCopy.reports(1))
        assertEquals("3 reports", PlaceCopy.reports(3))
        assertEquals("just now", PlaceCopy.age(now - 10_000, now))
        assertEquals("4 min ago", PlaceCopy.age(now - 4 * 60_000, now))
        assertEquals("3 h ago", PlaceCopy.age(now - 3 * 3_600_000, now))
        assertEquals("2 d ago", PlaceCopy.age(now - 50 * 3_600_000L, now))
        assertEquals("", PlaceCopy.age(null, now))
    }

    @Test
    fun pulseLines() {
        val live = PulseSummaryDto(state = "live", label = "lively", reportCount = 1, newestAt = fourMinAgo)
        assertEquals("Lively · 1 report · 4 min ago", PlaceCopy.pulseLine(live, now))
        assertEquals("1 report · 4 min ago", PlaceCopy.liveDetail(live, now))
        val stale = PulseSummaryDto(state = "stale", label = "chill", newestAt = "2026-10-01T17:00:00Z")
        assertEquals("Last Pulse: Chill · 3 h ago", PlaceCopy.pulseLine(stale, now))
        assertEquals(PlaceCopy.NO_PULSE, PlaceCopy.pulseLine(PulseSummaryDto(), now))
        assertEquals(PlaceCopy.NO_PULSE, PlaceCopy.pulseLine(PulseSummaryDto(state = "live"), now))
    }

    @Test
    fun smallLabels() {
        assertEquals("Strong read", PlaceCopy.confidence("high"))
        assertEquals("Early read", PlaceCopy.confidence("low"))
        assertNull(PlaceCopy.confidence(null))
        assertEquals("Usually Lively around now · 6 reports over 8 weeks", PlaceCopy.pattern(PlacePatternDto("lively", 6, 8)))
        assertNull(PlaceCopy.pattern(PlacePatternDto("unknown", 6, 8)))
        assertEquals("3 here now", PlaceCopy.hereNow(3))
        assertNull(PlaceCopy.hereNow(0))
        assertEquals("Seats: Plenty 0 · Some 1 · None 0", PlaceCopy.categoryLine("seats", listOf(0, 1, 0)))
        assertEquals("Line: None 2 · Short 0 · Long 1", PlaceCopy.categoryLine("line", listOf(2, 0, 1)))
        assertEquals("Easy to talk: 1 yes · 2 no", PlaceCopy.easyToTalk(PulseSummaryDto(talkable = PulseTalkableDto(1, 2))))
        assertNull(PlaceCopy.easyToTalk(PulseSummaryDto(talkable = PulseTalkableDto(0, 0))))
        assertEquals("https://joinclick.co/p/cafe-allegro", PlaceCopy.shareUrl("cafe-allegro"))
    }

    @Test
    fun names() {
        assertEquals("Maya", PlaceCopy.names(listOf("Maya"), 1))
        assertEquals("Maya and Jordan", PlaceCopy.names(listOf("Maya", "Jordan"), 2))
        assertEquals("Maya, Jordan and 1 other", PlaceCopy.names(listOf("Maya", "Jordan", "Sam"), 3))
        assertEquals("Maya, Jordan and 4 others", PlaceCopy.names(listOf("Maya", "Jordan"), 6))
        assertEquals("3 others", PlaceCopy.names(emptyList(), 3))
        assertEquals("Maya has been here", PlaceCopy.clicksBeenHere(ClicksBeenHereDto(1, listOf("Maya"))))
        assertEquals("Maya and Jordan have been here", PlaceCopy.clicksBeenHere(ClicksBeenHereDto(2, listOf("Maya", "Jordan"))))
        assertNull(PlaceCopy.clicksBeenHere(ClicksBeenHereDto(0)))
        assertEquals("You met Maya here", PlaceCopy.youMetHere(YouMetHereDto(1, listOf(PlacePersonDto("u1", "Maya")))))
        assertEquals("Jordan is here", PlaceCopy.hereNowConnections(listOf(PlacePersonDto("u2", "Jordan"))))
        assertNull(PlaceCopy.hereNowConnections(emptyList()))
    }

    @Test
    fun historyAndVisits() {
        val utc = TimeZone.UTC
        assertEquals(
            "You've checked in 4 times · Last on Sep 28",
            PlaceCopy.checkInHistory(PlaceOwnHistoryDto(4, "2026-09-28T20:00:00Z"), utc),
        )
        assertEquals("You've checked in once", PlaceCopy.checkInHistory(PlaceOwnHistoryDto(1), utc))
        assertNull(PlaceCopy.checkInHistory(PlaceOwnHistoryDto(0), utc))
        assertEquals("1 Click made here", PlaceCopy.clicksMadeHere(1))
        assertEquals("2 Clicks made here", PlaceCopy.clicksMadeHere(2))
        val place = PlaceSummaryDto(id = "p", latitude = 0.0, longitude = 0.0, city = "Seattle", category = "cafe")
        assertEquals("Café · Seattle", PlaceCopy.categoryCity(place))
        assertEquals("4 visits · Last Sep 28", PlaceCopy.visitsLine(MyPlaceVisit(place, 4, "2026-09-28T20:00:00Z", 0), utc))
        assertEquals("1 visit", PlaceCopy.visitsLine(MyPlaceVisit(place, 1, null, 0), utc))
        assertEquals("Met 1 person here", PlaceCopy.metPeopleHere(1))
        assertEquals("Met 3 people here", PlaceCopy.metPeopleHere(3))
        assertNull(PlaceCopy.metPeopleHere(0))
        assertEquals("Checked in", PlaceCopy.checkedIn(null, utc))
        assertTrue(PlaceCopy.checkedIn(now, utc).startsWith("Checked in · until "))
    }

    @Test
    fun outOfBoundsRounding() {
        assertEquals("You're about 300 m away. Check in when you're here.", PlaceCopy.outOfBounds(312.0, metric = true))
        assertEquals("You're about 50 m away. Check in when you're here.", PlaceCopy.outOfBounds(10.0, metric = true))
        assertEquals("You're about 1.2 km away. Check in when you're here.", PlaceCopy.outOfBounds(1210.0, metric = true))
        assertEquals("You're about 0.2 mi away. Check in when you're here.", PlaceCopy.outOfBounds(312.0, metric = false))
        assertEquals("You're about 0.1 mi away. Check in when you're here.", PlaceCopy.outOfBounds(20.0, metric = false))
        assertEquals("You're not here yet. Check in when you're here.", PlaceCopy.outOfBounds(null, metric = false))
    }

    @Test
    fun requestBuilders() {
        assertEquals("/api/places/cafe-allegro", PlacesRequests.placePath("cafe-allegro"))
        assertEquals("/api/places/a%20b", PlacesRequests.placePath("a b"))
        assertEquals(
            listOf("lat" to "47.5", "lon" to "-122.25", "radius_meters" to "1500"),
            PlacesRequests.nearbyParams(47.5, -122.25, 1500),
        )
        assertEquals(listOf("source" to "history"), PlacesRequests.detailParams("history"))
        assertTrue(PlacesRequests.detailParams(null).isEmpty())

        val gps = PlacesRequests.checkInBody(47.5, -122.25, 12.0, null, shareWithConnections = true, appVersion = "1.4.2")
        assertEquals("android", gps["platform"]!!.jsonPrimitive.content)
        assertTrue(gps["share_with_connections"]!!.jsonPrimitive.boolean)
        assertEquals(47.5, gps["latitude"]!!.jsonPrimitive.double)
        assertEquals(12.0, gps["accuracy_meters"]!!.jsonPrimitive.double)
        assertEquals("1.4.2", gps["app_version"]!!.jsonPrimitive.content)
        assertFalse("anchor_token" in gps)

        val qr = PlacesRequests.checkInBody(null, null, null, "tok", shareWithConnections = false)
        assertEquals("tok", qr["anchor_token"]!!.jsonPrimitive.content)
        assertFalse("latitude" in qr)
        assertFalse("app_version" in qr)

        val pulse = PlacesRequests.pulseBody(energy = 3)
        assertEquals(1, pulse["question_version"]!!.jsonPrimitive.int)
        assertEquals(3, pulse["energy"]!!.jsonPrimitive.int)
        assertFalse("would_return" in pulse)
        assertEquals(1, PlacesRequests.pulseBody(wouldReturn = 1)["would_return"]!!.jsonPrimitive.int)
        val patch = PlacesRequests.pulsePatchBody(talkable = 0)
        assertEquals(setOf("talkable"), patch.keys)
    }
}
