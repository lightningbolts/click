package compose.project.click.click.ui.utils // pragma: allowlist secret

import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.GeoLocation // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconMetadata // pragma: allowlist secret
import compose.project.click.click.data.models.parseMapBeaconMetadata
import compose.project.click.click.events.EventSchedule
import compose.project.click.click.events.eventScheduleMetadata
import compose.project.click.click.events.isVisibleEventBeacon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MapRenderDataTest {

    @Test
    fun determineMapRenderData_zoomedOut_keepsEventStandaloneNotInClusters() {
        val lat = 37.7749
        val lon = -122.4194
        val connections = listOf(
            connection("c1", lat, lon),
            connection("c2", lat + 0.0001, lon + 0.0001),
        )
        val event = beacon("event-1", MapBeaconKind.EVENT, lat, lon)
        val soundtrack = beacon("ost-1", MapBeaconKind.SOUNDTRACK, lat, lon)
        val hazard = beacon("haz-1", MapBeaconKind.HAZARD, lat, lon)
        val sos = beacon("sos-1", MapBeaconKind.SOS, lat, lon)
        val utility = beacon("util-1", MapBeaconKind.UTILITY, lat, lon)
        val social = beacon("soc-1", MapBeaconKind.SOCIAL_VIBE, lat, lon)

        val rendered = determineMapRenderData(
            connections = connections,
            beacons = listOf(event, soundtrack, hazard, sos, utility, social),
            zoomLevel = 10.0,
        )

        val clusters = assertIs<MapRenderData.Clusters>(rendered)
        val standaloneIds = clusters.standaloneBeacons.map { it.id }.toSet()
        assertEquals(setOf("event-1", "ost-1", "haz-1", "sos-1", "util-1"), standaloneIds)

        val clusteredBeaconIds = clusters.clusters.flatMap { it.beaconPoints }.map { it.id }.toSet()
        assertFalse("event-1" in clusteredBeaconIds)
        assertTrue("soc-1" in clusteredBeaconIds)
        assertTrue(clusters.clusters.any { it.points.isNotEmpty() })
    }

    @Test
    fun determineMapRenderData_zoomedIn_returnsAllBeaconsAsIndividualPins() {
        val lat = 37.7749
        val lon = -122.4194
        val event = beacon("event-1", MapBeaconKind.EVENT, lat, lon)
        val rendered = determineMapRenderData(
            connections = listOf(connection("c1", lat, lon)),
            beacons = listOf(event),
            zoomLevel = 14.0,
        )
        val pins = assertIs<MapRenderData.IndividualPins>(rendered)
        assertEquals(listOf("event-1"), pins.beacons.map { it.id })
        assertEquals(1, pins.points.size)
    }

    @Test
    fun isVisibleEventBeacon_hidesEndedScheduledEvent() {
        val now = 1_000_000L
        val ended = MapBeacon(
            id = "ended",
            kind = MapBeaconKind.EVENT,
            latitude = 1.0,
            longitude = 2.0,
            metadata = parseMapBeaconMetadata(
                eventScheduleMetadata(
                    EventSchedule(startEpochMs = now - 10_000L, endEpochMs = now - 1L),
                ),
            ),
            sourceBeaconType = "event",
        )
        val active = MapBeacon(
            id = "active",
            kind = MapBeaconKind.EVENT,
            latitude = 1.0,
            longitude = 2.0,
            metadata = parseMapBeaconMetadata(
                eventScheduleMetadata(
                    EventSchedule(startEpochMs = now - 1_000L, endEpochMs = now + 10_000L),
                ),
            ),
            sourceBeaconType = "event",
        )
        assertFalse(ended.isVisibleEventBeacon(now))
        assertTrue(active.isVisibleEventBeacon(now))
        assertTrue(beacon("ost", MapBeaconKind.SOUNDTRACK, 1.0, 2.0).isVisibleEventBeacon(now))
    }

    private fun connection(id: String, lat: Double, lon: Double) = Connection(
        id = id,
        created = 1L,
        expiry = 9_999_999_999L,
        geo_location = GeoLocation(lat = lat, lon = lon),
        user_ids = listOf("u1", "u2"),
    )

    private fun beacon(
        id: String,
        kind: MapBeaconKind,
        lat: Double,
        lon: Double,
    ) = MapBeacon(
        id = id,
        kind = kind,
        latitude = lat,
        longitude = lon,
        metadata = MapBeaconMetadata(),
        sourceBeaconType = kind.name.lowercase(),
    )
}
