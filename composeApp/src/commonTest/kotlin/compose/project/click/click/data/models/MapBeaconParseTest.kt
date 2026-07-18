package compose.project.click.click.data.models

import compose.project.click.click.events.eventSchedule
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
        val schedule = beacon.eventSchedule()
        assertNotNull(schedule)
        assertEquals(beacon.createdAtEpochMs, schedule.startEpochMs)
        assertEquals(beacon.expiresAtEpochMs, schedule.endEpochMs)
    }
}
