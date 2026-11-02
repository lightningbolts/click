package compose.project.click.click.data.places

/**
 * The shared Place filter fixture (click-ios `Tests/ClickTests/Fixtures/place_filters.json`,
 * click-web `__tests__/fixtures/places/filters.json`): five Places and the cases every client
 * must agree on. Filters are in the stored JSON shape [PlaceFilters.decode] reads, with default
 * (false, 0, empty) fields left out.
 */
internal object PlaceFilterFixture {
    data class Case(
        val name: String,
        val filtersJson: String,
        val expectedIds: List<String>,
    )

    val placesJson: String =
        """
        [
         {
          "id": "cafe-live-lively",
          "slug": "cafe-live-lively",
          "name": "cafe live lively",
          "category": "cafe",
          "photo_url": null,
          "latitude": 47.6588,
          "longitude": -122.3131,
          "radius_meters": 75,
          "distance_meters": 100,
          "address_line": null,
          "city": "Seattle",
          "open_now": true,
          "pulse": {
           "state": "live",
           "label": "lively",
           "energy_score": 3,
           "report_count": 1,
           "newest_at": "2026-10-01T19:00:00.000Z",
           "confidence": "low",
           "distribution": [
            0,
            0,
            0,
            0
           ],
           "talkable": {
            "yes": 0,
            "no": 0
           },
           "category": null,
           "window_minutes": 90
          },
          "here_now_count": 1,
          "events_today_count": 0,
          "next_event": null,
          "hub_id": null,
          "viewer": {
           "checked_in": false,
           "has_history": true,
           "connections_been_here_count": 0
          }
         },
         {
          "id": "bar-live-packed",
          "slug": "bar-live-packed",
          "name": "bar live packed",
          "category": "bar",
          "photo_url": null,
          "latitude": 47.6588,
          "longitude": -122.3131,
          "radius_meters": 75,
          "distance_meters": 100,
          "address_line": null,
          "city": "Seattle",
          "open_now": false,
          "pulse": {
           "state": "live",
           "label": "packed",
           "energy_score": 3.8,
           "report_count": 6,
           "newest_at": "2026-10-01T19:00:00.000Z",
           "confidence": "medium",
           "distribution": [
            0,
            0,
            0,
            0
           ],
           "talkable": {
            "yes": 0,
            "no": 0
           },
           "category": null,
           "window_minutes": 90
          },
          "here_now_count": 0,
          "events_today_count": 1,
          "next_event": {
           "beacon_id": "e1",
           "title": "Trivia",
           "starts_at": "2026-10-02T03:00:00.000Z",
           "ends_at": "2026-10-02T05:00:00.000Z",
           "is_live": false
          },
          "hub_id": "place-bar",
          "viewer": {
           "checked_in": false,
           "has_history": false,
           "connections_been_here_count": 2
          }
         },
         {
          "id": "gym-stale",
          "slug": "gym-stale",
          "name": "gym stale",
          "category": "gym",
          "photo_url": null,
          "latitude": 47.6588,
          "longitude": -122.3131,
          "radius_meters": 75,
          "distance_meters": 100,
          "address_line": null,
          "city": "Seattle",
          "open_now": true,
          "pulse": {
           "state": "stale",
           "label": "chill",
           "energy_score": null,
           "report_count": 0,
           "newest_at": "2026-10-01T19:00:00.000Z",
           "confidence": null,
           "distribution": [
            0,
            0,
            0,
            0
           ],
           "talkable": {
            "yes": 0,
            "no": 0
           },
           "category": null,
           "window_minutes": 90
          },
          "here_now_count": 0,
          "events_today_count": 0,
          "next_event": null,
          "hub_id": null,
          "viewer": {
           "checked_in": false,
           "has_history": false,
           "connections_been_here_count": 0
          }
         },
         {
          "id": "study-none",
          "slug": "study-none",
          "name": "study none",
          "category": "study_space",
          "photo_url": null,
          "latitude": 47.6588,
          "longitude": -122.3131,
          "radius_meters": 75,
          "distance_meters": 100,
          "address_line": null,
          "city": "Seattle",
          "open_now": null,
          "pulse": {
           "state": "none",
           "label": null,
           "energy_score": null,
           "report_count": 0,
           "newest_at": null,
           "confidence": null,
           "distribution": [
            0,
            0,
            0,
            0
           ],
           "talkable": {
            "yes": 0,
            "no": 0
           },
           "category": null,
           "window_minutes": 90
          },
          "here_now_count": 3,
          "events_today_count": 0,
          "next_event": {
           "beacon_id": "e2",
           "title": "Study jam",
           "starts_at": "2026-10-01T18:00:00.000Z",
           "ends_at": "2026-10-01T21:00:00.000Z",
           "is_live": true
          },
          "hub_id": null,
          "viewer": null
         },
         {
          "id": "cafe-live-chill",
          "slug": "cafe-live-chill",
          "name": "cafe live chill",
          "category": "cafe",
          "photo_url": null,
          "latitude": 47.6588,
          "longitude": -122.3131,
          "radius_meters": 75,
          "distance_meters": 100,
          "address_line": null,
          "city": "Seattle",
          "open_now": true,
          "pulse": {
           "state": "live",
           "label": "chill",
           "energy_score": 1.2,
           "report_count": 10,
           "newest_at": "2026-10-01T19:00:00.000Z",
           "confidence": "high",
           "distribution": [
            0,
            0,
            0,
            0
           ],
           "talkable": {
            "yes": 0,
            "no": 0
           },
           "category": null,
           "window_minutes": 90
          },
          "here_now_count": 0,
          "events_today_count": 0,
          "next_event": null,
          "hub_id": "place-cafe2",
          "viewer": {
           "checked_in": true,
           "has_history": true,
           "connections_been_here_count": 1
          }
         }
        ]
        """.trimIndent()

    val cases: List<Case> =
        listOf(
            Case(
                name = "no filters shows every Place in input order",
                filtersJson = """{}""",
                expectedIds = listOf("cafe-live-lively", "bar-live-packed", "gym-stale", "study-none", "cafe-live-chill"),
            ),
            Case(
                name = "category OR",
                filtersJson = """{"categories":["bar","gym"]}""",
                expectedIds = listOf("bar-live-packed", "gym-stale"),
            ),
            Case(
                name = "pulse now excludes stale and none",
                filtersJson = """{"pulseNow":true}""",
                expectedIds = listOf("cafe-live-lively", "bar-live-packed", "cafe-live-chill"),
            ),
            Case(
                name = "min reports counts only live Pulses",
                filtersJson = """{"minReports":5}""",
                expectedIds = listOf("bar-live-packed", "cafe-live-chill"),
            ),
            Case(
                name = "energies OR, live only",
                filtersJson = """{"energies":["lively","packed"]}""",
                expectedIds = listOf("cafe-live-lively", "bar-live-packed"),
            ),
            Case(
                name = "quiet energies ignore a stale chill",
                filtersJson = """{"energies":["chill","steady"]}""",
                expectedIds = listOf("cafe-live-chill"),
            ),
            Case(
                name = "events today includes a live next event",
                filtersJson = """{"eventsToday":true}""",
                expectedIds = listOf("bar-live-packed", "study-none"),
            ),
            Case(
                name = "open now excludes unknown hours",
                filtersJson = """{"openNow":true}""",
                expectedIds = listOf("cafe-live-lively", "gym-stale", "cafe-live-chill"),
            ),
            Case(
                name = "been here needs viewer history",
                filtersJson = """{"beenHere":true}""",
                expectedIds = listOf("cafe-live-lively", "cafe-live-chill"),
            ),
            Case(
                name = "clicks been here",
                filtersJson = """{"clicksBeenHere":true}""",
                expectedIds = listOf("bar-live-packed", "cafe-live-chill"),
            ),
            Case(
                name = "has hub",
                filtersJson = """{"hasHub":true}""",
                expectedIds = listOf("bar-live-packed", "cafe-live-chill"),
            ),
            Case(
                name = "here now",
                filtersJson = """{"hereNow":true}""",
                expectedIds = listOf("cafe-live-lively", "study-none"),
            ),
            Case(
                name = "AND across fields",
                filtersJson = """{"categories":["cafe"],"minReports":2,"openNow":true}""",
                expectedIds = listOf("cafe-live-chill"),
            ),
            Case(
                name = "a single report still matches (no thresholds)",
                filtersJson = """{"categories":["cafe"],"energies":["lively"]}""",
                expectedIds = listOf("cafe-live-lively"),
            ),
        )
}
