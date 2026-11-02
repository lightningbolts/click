package compose.project.click.click.ui.utils

import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.MapBeaconKind
import compose.project.click.click.ui.components.MapPin
import compose.project.click.click.ui.components.MapPinKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapPinStackingTest {
    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    // ~100 m of latitude.
    private val hundredMeters = 0.0009

    private fun connection(
        id: String,
        lat: Double = 47.6,
        lon: Double = -122.3,
        isCore: Boolean = false,
    ) = MapPin(id = id, title = id, latitude = lat, longitude = lon, kind = MapPinKind.CONNECTION, isCore = isCore)

    private fun beacon(
        id: String,
        kind: MapBeaconKind,
        lat: Double = 47.6,
        lon: Double = -122.3,
        start: Long? = null,
        end: Long? = null,
        title: String = id,
    ) = MapPin(
        id = "beacon:$id",
        title = title,
        latitude = lat,
        longitude = lon,
        kind = if (kind == MapBeaconKind.EVENT) MapPinKind.BEACON_SOCIAL else MapPinKind.BEACON_OTHER,
        beaconKind = kind,
        eventStartEpochMs = start,
        eventEndEpochMs = end,
    )

    private fun hub(id: String) = MapPin(id = "hub:$id", title = id, latitude = 47.6, longitude = -122.3, kind = MapPinKind.COMMUNITY_HUB)

    @Test
    fun empty_givesNoStacks() {
        assertTrue(stackMapPins(emptyList(), 15.0, now).isEmpty())
    }

    @Test
    fun samePlacePins_stackLedByAlert() {
        val pins = listOf(connection("c1"), beacon("e1", MapBeaconKind.EVENT), beacon("h1", MapBeaconKind.HAZARD), hub("hub"))
        val stacks = stackMapPins(pins, 16.0, now)
        assertEquals(1, stacks.size)
        val stack = stacks.single()
        assertEquals(listOf("beacon:h1", "beacon:e1", "hub:hub", "c1"), stack.members.map { it.id })
        assertEquals("stack.beacon:h1", stack.id)
        assertEquals(4, stack.toRenderedPin().stackCount)
        assertEquals("beacon:h1", stack.toRenderedPin().id)
    }

    @Test
    fun coreClicksRankAboveOtherConnectionsButBelowBeaconsAndHubs() {
        // "Ann" would lead A–Z; the Core Click "Zed" leads instead (iOS `stackRank` 6 vs 7).
        val ann = connection("Ann")
        val zed = connection("Zed", isCore = true)
        assertEquals(6, mapPinStackRank(zed, now))
        assertEquals(7, mapPinStackRank(ann, now))
        val stack = stackMapPins(listOf(ann, hub("hub"), zed, beacon("b1", MapBeaconKind.STUDY)), 16.0, now).single()
        assertEquals(listOf("hub:hub", "beacon:b1", "Zed", "Ann"), stack.members.map { it.id })
        assertEquals("Zed", stackMapPins(listOf(ann, zed), 16.0, now).single().lead.id)
        // The flag only matters for connection pins.
        assertEquals(3, mapPinStackRank(hub("h2").copy(isCore = true), now))
    }

    @Test
    fun connectionPinsCarryTheCoreFlag() {
        val point =
            ConnectionMapPoint(
                connection = Connection(id = "c1", created = 1L, expiry = 9_999_999_999L, user_ids = listOf("me", "peer")),
                latitude = 47.6,
                longitude = -122.3,
                timeState = TimeState.RECENT,
                opacity = 1f,
                shouldPulse = false,
                displayName = "Peer",
                locationLabel = "Campus",
                formattedDate = "Today",
            )
        assertTrue(MapPin.fromConnectionPoint(point, isCore = true).isCore)
        assertFalse(MapPin.fromConnectionPoint(point).isCore)
    }

    @Test
    fun lonePin_isAStackOfOneWithItsOwnId() {
        val stacks = stackMapPins(listOf(connection("c1")), 15.0, now)
        assertEquals("c1", stacks.single().id)
        assertEquals(1, stacks.single().toRenderedPin().stackCount)
    }

    @Test
    fun liveEventLeadsUpcomingEvent_thenSoonestStart() {
        val live = beacon("live", MapBeaconKind.EVENT, start = now - hour, end = now + hour, title = "Z live")
        val soon = beacon("soon", MapBeaconKind.EVENT, start = now + hour, end = now + 2 * hour)
        val later = beacon("later", MapBeaconKind.EVENT, start = now + 5 * hour, end = now + 6 * hour)
        val stack = stackMapPins(listOf(later, soon, live), 16.0, now).single()
        assertEquals(listOf("beacon:live", "beacon:soon", "beacon:later"), stack.members.map { it.id })
    }

    @Test
    fun rankOrder_matchesIos() {
        assertEquals(-1, mapPinStackRank(beacon("s", MapBeaconKind.SOS), now))
        assertEquals(0, mapPinStackRank(beacon("e", MapBeaconKind.EVENT, start = now - 1, end = now + 1), now))
        assertEquals(1, mapPinStackRank(beacon("e", MapBeaconKind.EVENT), now))
        assertEquals(3, mapPinStackRank(hub("h"), now))
        assertEquals(4, mapPinStackRank(beacon("t", MapBeaconKind.SOUNDTRACK), now))
        assertEquals(7, mapPinStackRank(connection("c"), now))
    }

    @Test
    fun overlapDependsOnZoom() {
        val a = connection("a")
        val b = connection("b", lat = 47.6 + hundredMeters)
        // At zoom 15 one dp is ~3 m here, so 100 m is ~30 dp: one stack.
        assertEquals(1, stackMapPins(listOf(a, b), 15.0, now).size)
        // At zoom 19 100 m is ~500 dp: apart.
        assertEquals(2, stackMapPins(listOf(a, b), 19.0, now).size)
    }

    @Test
    fun farPins_neverStack() {
        val pins = listOf(connection("a"), connection("b", lat = 48.6))
        assertEquals(2, stackMapPins(pins, 10.0, now).size)
    }

    @Test
    fun stacksAcrossGridCellEdges() {
        // Two pins 20 dp apart horizontally straddle a 60 dp cell boundary at zoom 0's scale.
        val zoom = 10.0
        val dpPerDegree = 256.0 * 1024.0 / 360.0
        val boundaryLon = 60.0 * 1000 / dpPerDegree - 180.0
        val a = connection("a", lat = 0.0, lon = boundaryLon - 10.0 / dpPerDegree)
        val b = connection("b", lat = 0.0, lon = boundaryLon + 10.0 / dpPerDegree)
        assertEquals(1, stackMapPins(listOf(a, b), zoom, now).size)
    }

    @Test
    fun duplicateIds_countOnce() {
        val stack = stackMapPins(listOf(connection("a"), connection("a")), 15.0, now)
        assertEquals(1, stack.single().count)
    }

    @Test
    fun canSeparate_onlyWhenMembersAreApart() {
        val samespot = MapPinStack("s", listOf(connection("a"), connection("b")))
        assertFalse(samespot.canSeparate())
        val apart = MapPinStack("s", listOf(connection("a"), connection("b", lat = 47.6 + hundredMeters)))
        assertTrue(apart.canSeparate())
    }

    @Test
    fun zoomInTarget_framesMembersSoTheySeparate() {
        val a = connection("a")
        val b = connection("b", lat = 47.6 + hundredMeters)
        val stack = MapPinStack("s", listOf(a, b))
        val (lat, lon, zoom) = stack.zoomInTarget()
        assertEquals(47.6 + hundredMeters / 2, lat, 1e-9)
        assertEquals(-122.3, lon, 1e-9)
        assertEquals(2, stackMapPins(listOf(a, b), MapPinStacking.zoomStep(zoom), now).size)
    }

    @Test
    fun zoomInTarget_samespotUsesMinimumSpan() {
        val (_, _, zoom) = MapPinStack("s", listOf(connection("a"), connection("b"))).zoomInTarget()
        assertTrue(zoom in 15.0..18.0, "zoom was $zoom")
    }

    @Test
    fun title_namesOneKindOrSaysHere() {
        assertEquals("2 Events", mapPinStackTitle(listOf(beacon("a", MapBeaconKind.EVENT), beacon("b", MapBeaconKind.EVENT))))
        assertEquals("2 Clicks", mapPinStackTitle(listOf(connection("a"), connection("b"))))
        assertEquals("2 Here", mapPinStackTitle(listOf(connection("a"), hub("h"))))
    }

    @Test
    fun findStack_byLeadId() {
        val pins = listOf(connection("c1"), beacon("h1", MapBeaconKind.HAZARD))
        assertNotNull(findMapPinStack("beacon:h1", pins, 16.0, now))
        assertNull(findMapPinStack("c1", pins, 16.0, now))
    }

    @Test
    fun zoomStep_roundsDownToQuarters() {
        assertEquals(14.75, MapPinStacking.zoomStep(14.99))
        assertEquals(14.0, MapPinStacking.zoomStep(14.0))
        assertEquals(12.25, MapPinStacking.zoomStep(12.3))
    }
}
