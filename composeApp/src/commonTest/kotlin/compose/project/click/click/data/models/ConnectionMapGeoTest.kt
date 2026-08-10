package compose.project.click.click.data.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ConnectionMapGeoTest {
    @Test
    fun connectionMapGeo_prefersOriginEncounterOverLaterBeaconCrossing() {
        val origin = ConnectionEncounter(
            id = "enc-origin",
            connectionId = "conn-1",
            encounteredAt = "2026-01-01T12:00:00Z",
            gpsLat = 47.60,
            gpsLon = -122.33,
        )
        val laterAtBeacon = ConnectionEncounter(
            id = "enc-beacon",
            connectionId = "conn-1",
            encounteredAt = "2026-08-01T18:00:00Z",
            gpsLat = 47.66,
            gpsLon = -122.30,
            eventBeaconId = "beacon-1",
            eventBeaconTitle = "Community hang",
        )
        val connection = Connection(
            id = "conn-1",
            created = 1_700_000_000_000L,
            expiry = 1_800_000_000_000L,
            geo_location = null,
            connectionEncounters = listOf(laterAtBeacon, origin),
            user_ids = listOf("u1", "u2"),
        )

        val geo = connection.connectionMapGeo()
        assertNotNull(geo)
        assertEquals(47.60, geo.lat)
        assertEquals(-122.33, geo.lon)
    }

    @Test
    fun connectionMapGeo_prefersStoredGeoLocationOverEncounters() {
        val later = ConnectionEncounter(
            id = "enc-later",
            connectionId = "conn-2",
            encounteredAt = "2026-08-01T18:00:00Z",
            gpsLat = 40.0,
            gpsLon = -70.0,
        )
        val connection = Connection(
            id = "conn-2",
            created = 1_700_000_000_000L,
            expiry = 1_800_000_000_000L,
            geo_location = GeoLocation(lat = 37.77, lon = -122.42),
            connectionEncounters = listOf(later),
            user_ids = listOf("u1", "u2"),
        )

        val geo = connection.connectionMapGeo()
        assertNotNull(geo)
        assertEquals(37.77, geo.lat)
        assertEquals(-122.42, geo.lon)
    }
}
