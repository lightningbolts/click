package compose.project.click.click.data.places

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fixtures follow iOS `Tests/ClickTests/PlacesModelTests.swift` and click-web `serialize.ts`. */
class PlaceModelsTest {
    private fun json(s: String): JsonElement = Json.parseToJsonElement(s)

    private val fullSummary =
        """
        {
          "id": "6a1f0000-0000-4000-8000-000000000001",
          "slug": "cafe-allegro-seattle",
          "name": "Café Allegro",
          "category": "cafe",
          "photo_url": "https://example.supabase.co/place-photos/cover.jpg",
          "latitude": 47.6588, "longitude": -122.3131,
          "radius_meters": 75,
          "distance_meters": 412.3,
          "address_line": "4214 University Way NE",
          "city": "Seattle",
          "open_now": true,
          "pulse": {
            "state": "live", "label": "lively", "energy_score": 3.0, "report_count": 1,
            "newest_at": "2026-10-01T19:56:00.000Z", "confidence": "low",
            "distribution": [0, 0, 1, 0], "talkable": {"yes": 1, "no": 0},
            "category": {"question": "seats", "counts": [0, 1, 0]}, "window_minutes": 90
          },
          "here_now_count": 3,
          "events_today_count": 1,
          "next_event": {"beacon_id": "e1", "title": "Open mic", "starts_at": "2026-10-02T03:00:00Z", "ends_at": null, "is_live": false},
          "hub_id": "place-6a1f",
          "viewer": {"checked_in": false, "has_history": true, "connections_been_here_count": 2}
        }
        """.trimIndent()

    @Test
    fun decodesFullSummary() {
        val place = assertNotNull(decodePlaceSummary(json(fullSummary)))
        assertEquals("cafe-allegro-seattle", place.slugOrId)
        assertEquals(PlaceCategory.CAFE, place.placeCategory)
        assertEquals(true, place.openNow)
        assertTrue(place.pulse.isLive)
        assertEquals(EnergyLabel.LIVELY, place.pulse.energy)
        assertEquals(listOf(0, 0, 1, 0), place.pulse.bars)
        assertEquals(listOf(0, 1, 0), place.pulse.category?.counts)
        assertEquals(1, place.pulse.talkable?.yes)
        assertEquals("Open mic", place.nextEvent?.title)
        assertEquals(2, place.viewer?.connectionsBeenHereCount)
        assertEquals("place-6a1f", place.hubId)
        assertEquals(Instant.parse("2026-10-01T19:56:00Z").toEpochMilliseconds(), place.pulse.newestAtMs)
    }

    @Test
    fun minimalSummaryFallsBackAndRowsWithoutCoordinatesDrop() {
        val place = assertNotNull(decodePlaceSummary(json("""{"id":"p1","latitude":1.5,"longitude":2.5,"category":"church"}""")))
        assertEquals(PlaceCategory.OTHER, place.placeCategory)
        assertEquals("Place", place.placeCategory.label)
        assertEquals("none", place.pulse.state)
        assertEquals("p1", place.slugOrId)
        assertNull(place.viewer)
        assertNull(decodePlaceSummary(json("""{"id":"no-coordinates"}""")))

        val list = decodeNearbyPlaces(json("""{"places":[$fullSummary,{"id":"bad"},{"id":"p2","latitude":0,"longitude":0}]}"""))
        assertEquals(listOf("6a1f0000-0000-4000-8000-000000000001", "p2"), list.map { it.id })
    }

    @Test
    fun wrongLengthDistributionReadsAsZeros() {
        val pulse = Json { ignoreUnknownKeys = true }.decodeFromString<PulseSummaryDto>("""{"state":"live","distribution":[1,2]}""")
        assertEquals(listOf(0, 0, 0, 0), pulse.bars)
        assertNull(pulse.energy)
    }

    @Test
    fun decodesDetailSections() {
        val detailJson =
            fullSummary.trimEnd().removeSuffix("}") +
                """
                ,
                  "description": "Espresso since 1975",
                  "website_url": "https://example.com",
                  "today_hours_label": "7 AM – 10 PM",
                  "directions": {"apple_maps_url": "https://maps.apple.com/?daddr=1,2", "google_maps_url": "https://www.google.com/maps/dir/?api=1&destination=1,2"},
                  "pattern": {"label": "lively", "report_count": 6, "weeks": 8},
                  "upcoming_events": [{"beacon_id": "e1", "title": "Open mic", "is_live": true}, {"title": "no id"}],
                  "here_now_connections": [{"user_id": "u2", "name": "Jordan"}],
                  "clicks_been_here": {"count": 2, "names": ["Jordan", "Maya"]},
                  "you_met_here": {"total": 1, "people": [{"user_id": "u1", "name": "Maya", "last_met_at": "2026-09-28T20:00:00Z"}]},
                  "own_history": {"check_in_count": 4, "last_check_in_at": "2026-09-28T20:00:00Z", "encounter_count": 2},
                  "check_in": {"active": true, "check_in_id": "ci1", "expires_at": "2026-10-01T22:00:00Z", "proof": "qr", "share_with_connections": true},
                  "pulse_eligibility": {
                    "can_pulse": false, "reason": "cooldown", "cooldown_until": "2026-10-01T20:30:00Z",
                    "questions": [{"key": "energy", "prompt": "How's the energy?", "required": true, "options": [{"value": 1, "label": "Chill"}]},
                                  {"key": "talkable", "prompt": "Easy to talk?", "options": [{"value": 1, "label": "Yes"}, {"value": 0, "label": "No"}]}],
                    "leaving_questions": [{"key": "would_return", "prompt": "Come back at this time?", "phase": "leaving", "options": []}],
                    "my_last_pulse": {"id": "pl1", "energy": 3}
                  },
                  "hub": {"id": "h1", "name": "Allegro Hub", "joined": false},
                  "is_manager": true
                }
                """.trimIndent()
        val detail = assertNotNull(decodePlaceDetail(json("""{"place":$detailJson}""")))
        assertEquals("Espresso since 1975", detail.description)
        assertEquals("7 AM – 10 PM", detail.todayHoursLabel)
        assertTrue(detail.directionsUrl!!.startsWith("https://www.google.com/maps"))
        assertEquals(EnergyLabel.LIVELY, detail.pattern?.energy)
        assertEquals(listOf("e1"), detail.upcomingEvents.map { it.beaconId })
        assertEquals("Jordan", detail.hereNowConnections.single().name)
        assertEquals(2, detail.clicksBeenHere?.count)
        assertEquals(
            "Maya",
            detail.youMetHere
                ?.people
                ?.single()
                ?.name,
        )
        assertEquals(4, detail.ownHistory?.checkInCount)
        assertTrue(detail.checkIn!!.isActive)
        assertEquals("qr", detail.checkIn?.proof)
        assertEquals("cooldown", detail.pulseEligibility?.reason)
        assertNotNull(detail.pulseEligibility?.cooldownUntilMs)
        assertEquals(2, detail.pulseEligibility?.questions?.size)
        assertEquals(
            "would_return",
            detail.pulseEligibility
                ?.leavingQuestions
                ?.single()
                ?.key,
        )
        assertEquals("h1", detail.hub?.id)
        assertTrue(detail.isManager)
    }

    @Test
    fun minimalDetailAndBadRoots() {
        val detail =
            assertNotNull(decodePlaceDetail(json("""{"place":{"id":"p","latitude":1,"longitude":2,"check_in":{"active":false}}}""")))
        assertNull(detail.checkIn)
        assertNull(detail.pulseEligibility)
        assertFalse(detail.isManager)
        assertNull(detail.directionsUrl)
        assertNull(decodePlaceDetail(json("""{"error":"nope"}""")))
        assertNull(decodePlaceDetail(json("""{"place":{"id":"p"}}""")))
    }

    @Test
    fun decodesCheckInRoutes() {
        // POST/GET use checked_in; the detail uses active.
        val post =
            decodeCheckIn(
                json(
                    """{"checked_in":true,"refreshed":false,"check_in_id":"ci1","checked_in_at":"2026-10-01T19:00:00Z",
                    "expires_at":"2026-10-01T22:00:00Z","proof":"gps","share_with_connections":false,"hub_id":"h1","here_now_count":4}""",
                ),
            )
        assertNotNull(post)
        assertEquals("h1", post.hubId)
        assertEquals(4, post.hereNowCount)
        assertNull(decodeCheckIn(json("""{"checked_in":false}""")))
        assertNull(decodeCheckIn(json("""{"checked_in":true}""")), "an id is required")
        assertTrue(decodeAskWouldReturn(json("""{"checked_in":false,"ask_would_return":true}""")))
        assertFalse(decodeAskWouldReturn(json("""{"checked_in":false}""")))
    }

    @Test
    fun decodesPulseResponses() {
        val result =
            assertNotNull(
                decodePulseSubmit(
                    json(
                        """{"pulse":{"id":"pl1","editable_until":"2026-10-01T20:10:00Z"},"summary":{"state":"live","label":"packed","report_count":2}}""",
                    ),
                ),
            )
        assertEquals("pl1", result.pulseId)
        assertEquals(Instant.parse("2026-10-01T20:10:00Z").toEpochMilliseconds(), result.editableUntilMs)
        assertEquals(EnergyLabel.PACKED, result.summary.energy)
        assertNull(decodePulseSubmit(json("""{"summary":{}}""")))
        assertEquals(5, decodePulseSummary(json("""{"summary":{"report_count":5}}""")).reportCount)
    }

    @Test
    fun decodesMyPlaces() {
        val visits =
            decodeMyPlaces(
                json(
                    """{"places":[{"place":$fullSummary,"check_in_count":4,"last_check_in_at":"2026-09-28T20:00:00Z","encounter_count":1},
                    {"place":{"id":"x"},"check_in_count":1}]}""",
                ),
            )
        assertEquals(1, visits.size)
        assertEquals(4, visits.single().checkInCount)
        assertEquals(1, visits.single().encounterCount)
        assertNotNull(visits.single().lastCheckInAtMs)
    }

    @Test
    fun mapsErrorsByCodeThenStatus() {
        assertEquals(PlaceError.NoLocation, PlaceError.from(422, """{"error":"x","code":"no_location"}"""))
        assertEquals(PlaceError.LowAccuracy, PlaceError.from(422, """{"code":"low_accuracy"}"""))
        assertEquals(PlaceError.OutOfBounds(312.5), PlaceError.from(422, """{"code":"out_of_bounds","distance_meters":312.5}"""))
        assertEquals(PlaceError.InvalidAnchor, PlaceError.from(422, """{"code":"invalid_anchor"}"""))
        assertEquals(PlaceError.ManagerPulseNotAllowed, PlaceError.from(403, """{"code":"manager_pulse_not_allowed"}"""))
        assertEquals(PlaceError.NotPresent, PlaceError.from(403, """{"code":"not_present"}"""))
        assertEquals(
            PlaceError.Cooldown(Instant.parse("2026-10-01T20:30:00Z").toEpochMilliseconds()),
            PlaceError.from(429, """{"code":"pulse_cooldown","cooldown_until":"2026-10-01T20:30:00Z"}"""),
        )
        assertEquals(PlaceError.NotEditable, PlaceError.from(409, """{"code":"pulse_not_editable"}"""))
        assertEquals(PlaceError.NotFound, PlaceError.from(404, """{"code":"place_not_found"}"""))
        // Fallbacks by status.
        assertEquals(PlaceError.NotFound, PlaceError.from(404, "<html>"))
        assertEquals(PlaceError.NotPresent, PlaceError.from(403, null))
        assertEquals(PlaceError.NotFound, PlaceError.from(403, null, forbidden = PlaceError.NotFound))
        assertEquals(PlaceError.Cooldown(null), PlaceError.from(429, ""))
        assertEquals(PlaceError.Network, PlaceError.from(500, """{"error":"Internal Server Error"}"""))
    }

    @Test
    fun errorCopyMatchesIos() {
        assertEquals("Turn on location to check in.", PlaceError.NoLocation.message())
        assertEquals("This Place isn't available.", PlaceError.NotFound.message())
        assertEquals("You manage this Place, so you can't Pulse it.", PlaceError.ManagerPulseNotAllowed.message())
        assertEquals("Something went wrong. Try again.", PlaceError.EmptyPulse.message())
    }
}
