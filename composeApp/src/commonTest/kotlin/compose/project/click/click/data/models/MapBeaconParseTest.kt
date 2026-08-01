package compose.project.click.click.data.models

import compose.project.click.click.events.eventSchedule
import compose.project.click.click.events.isVisibleEventBeacon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class MapBeaconParseTest {
    @Test
    fun parseEpochMs_acceptsPostgresTimestamptz() {
        val ms = parseEpochMs("2026-05-30 01:40:47.869682+00")
        assertNotNull(ms)
        assertTrue(ms > 0L)
    }

    @Test
    fun parseMapBeaconRows_preservesScheduleAndCreator() {
        val json = Json.parseToJsonElement(
            """
            [{
              "id": "124c10f4-f72a-4dba-97e7-9c6f10daf700",
              "creator_id": "aa3c293c-4066-4fae-8639-30b7fcd1a5c9",
              "beacon_type": "event",
              "lat": 47.6062,
              "lon": -122.3321,
              "metadata": {"description": "Anyone welcome to study"},
              "created_at": "2026-05-30 01:40:47.869682+00",
              "expires_at": "2026-05-30 04:40:47.852+00",
              "show_creator_name": true,
              "creator_name": "Kairui Cheng"
            }]
            """.trimIndent(),
        )
        val beacon = parseMapBeaconRows(json).single()
        assertEquals(MapBeaconKind.EVENT, beacon.kind)
        assertTrue(beacon.showCreatorName)
        assertEquals("Kairui Cheng", beacon.creatorDisplayName)
        assertNotNull(beacon.createdAtEpochMs)
        assertNotNull(beacon.expiresAtEpochMs)
        // Legacy TTL rows without event_start_at/event_end_at must not invent Start = created_at.
        assertEquals(null, beacon.eventSchedule())
        assertTrue(beacon.isVisibleEventBeacon(nowEpochMs = beacon.createdAtEpochMs!! + 1L))
        assertTrue(!beacon.isVisibleEventBeacon(nowEpochMs = beacon.expiresAtEpochMs!! + 1L))
    }

    @Test
    fun parseEventSchedule_acceptsPostgresTimestamptzInMetadata() {
        val json = Json.parseToJsonElement(
            """
            [{
              "id": "124c10f4-f72a-4dba-97e7-9c6f10daf700",
              "beacon_type": "event",
              "lat": 47.6062,
              "lon": -122.3321,
              "metadata": {
                "title": "birthday",
                "event_start_at": "2026-07-22 16:00:00+00",
                "event_end_at": "2026-07-22 23:00:00+00"
              },
              "created_at": "2026-06-12 02:33:00+00",
              "expires_at": "2026-07-22 23:00:00+00"
            }]
            """.trimIndent(),
        )
        val beacon = parseMapBeaconRows(json).single()
        val schedule = beacon.eventSchedule()
        assertNotNull(schedule)
        assertEquals(parseEpochMs("2026-07-22 16:00:00+00"), schedule.startEpochMs)
        assertEquals(parseEpochMs("2026-07-22 23:00:00+00"), schedule.endEpochMs)
        assertTrue(schedule.startEpochMs != beacon.createdAtEpochMs)
    }

    @Test
    fun parseMapBeaconMetadata_acceptsStringifiedJson() {
        val json = Json.parseToJsonElement(
            """
            [{
              "id": "124c10f4-f72a-4dba-97e7-9c6f10daf700",
              "beacon_type": "event",
              "lat": 47.6062,
              "lon": -122.3321,
              "metadata": "{\"title\":\"birthday\",\"event_start_at\":\"2026-07-22T16:00:00Z\",\"event_end_at\":\"2026-07-22T23:00:00Z\"}",
              "created_at": "2026-06-12T02:33:00Z",
              "expires_at": "2026-07-22T23:00:00Z"
            }]
            """.trimIndent(),
        )
        val beacon = parseMapBeaconRows(json).single()
        val schedule = beacon.eventSchedule()
        assertNotNull(schedule)
        assertEquals(parseEpochMs("2026-07-22T16:00:00Z"), schedule.startEpochMs)
    }

    @Test
    fun parseMapBeaconMetadata_preservesLocationAddressFields() {
        val json = Json.parseToJsonElement(
            """
            [{
              "id": "124c10f4-f72a-4dba-97e7-9c6f10daf700",
              "beacon_type": "event",
              "lat": 47.6062,
              "lon": -122.3321,
              "metadata": {
                "title": "campus meetup",
                "location_name": "Red Square",
                "formatted_address": "Red Square, Seattle, WA, USA"
              },
              "created_at": "2026-06-12T02:33:00Z",
              "expires_at": "2026-07-22T23:00:00Z"
            }]
            """.trimIndent(),
        )
        val beacon = parseMapBeaconRows(json).single()
        assertEquals("Red Square", beacon.metadata.locationName)
        assertEquals("Red Square, Seattle, WA, USA", beacon.metadata.formattedAddress)
    }
}
