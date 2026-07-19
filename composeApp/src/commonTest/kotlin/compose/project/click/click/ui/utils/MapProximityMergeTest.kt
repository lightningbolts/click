package compose.project.click.click.ui.utils // pragma: allowlist secret

import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconMetadata // pragma: allowlist secret
import compose.project.click.click.data.models.parseMapBeaconMetadata
import compose.project.click.click.events.EventSchedule
import compose.project.click.click.events.eventSchedule
import compose.project.click.click.events.eventScheduleMetadata
import compose.project.click.click.events.isActiveForDiscoveryFeed
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MapProximityMergeTest {

    @Test
    fun mergeCommunityHubLists_keepsExistingWhenIncomingEmpty() {
        val existing = listOf(hub("a"), hub("b"))
        assertEquals(existing, mergeCommunityHubLists(existing, emptyList()))
    }

    @Test
    fun mergeCommunityHubLists_unionsByIdAndPrefersIncoming() {
        val existing = listOf(hub("a", active = 1))
        val incoming = listOf(hub("a", active = 4), hub("b"))
        val merged = mergeCommunityHubLists(existing, incoming)
        assertEquals(2, merged.size)
        assertEquals(4, merged.first { it.hubId == "a" }.activeUserCount)
        assertTrue(merged.any { it.hubId == "b" })
    }

    @Test
    fun mergeMapBeaconLists_keepsExistingWhenIncomingEmpty() {
        val existing = listOf(beacon("x"))
        assertEquals(existing, mergeMapBeaconLists(existing, emptyList()))
    }

    @Test
    fun mergeMapBeaconLists_dropsExpiredIncoming() {
        val now = Clock.System.now().toEpochMilliseconds()
        val existing = listOf(beacon("fresh"))
        val incoming = listOf(
            beacon("fresh"),
            beacon("stale", expiresAtEpochMs = now - 1L),
        )
        val merged = mergeMapBeaconLists(existing, incoming)
        assertEquals(1, merged.size)
        assertEquals("fresh", merged.single().id)
    }

    @Test
    fun mergeMapBeaconLists_keepsScheduledEventWhenExpiresAtColumnIsStale() {
        val now = Clock.System.now().toEpochMilliseconds()
        val schedule = EventSchedule(startEpochMs = now + 3_600_000L, endEpochMs = now + 7_200_000L)
        val event = MapBeacon(
            id = "event-1",
            kind = MapBeaconKind.EVENT,
            latitude = 1.0,
            longitude = 2.0,
            metadata = parseMapBeaconMetadata(eventScheduleMetadata(schedule)),
            expiresAtEpochMs = now - 60_000L,
            sourceBeaconType = "event",
        )
        val merged = mergeMapBeaconLists(emptyList(), listOf(event))
        assertEquals(1, merged.size)
        assertTrue(merged.single().isActiveForDiscoveryFeed(now))
    }

    @Test
    fun mergeMapBeaconLists_preservesCoordsWhenIncomingHasScheduleAtNullIsland() {
        val good = MapBeacon(
            id = "event-1",
            kind = MapBeaconKind.EVENT,
            latitude = 47.6062,
            longitude = -122.3321,
            metadata = MapBeaconMetadata(title = "birthday"),
            expiresAtEpochMs = Clock.System.now().toEpochMilliseconds() + 86_400_000L,
            sourceBeaconType = "event",
        )
        val schedule = EventSchedule(
            startEpochMs = Clock.System.now().toEpochMilliseconds() + 3_600_000L,
            endEpochMs = Clock.System.now().toEpochMilliseconds() + 7_200_000L,
        )
        val poisoned = MapBeacon(
            id = "event-1",
            kind = MapBeaconKind.EVENT,
            latitude = 0.0,
            longitude = 0.0,
            metadata = parseMapBeaconMetadata(eventScheduleMetadata(schedule)),
            expiresAtEpochMs = Clock.System.now().toEpochMilliseconds() + 86_400_000L,
            sourceBeaconType = "event",
        )
        val merged = mergeMapBeaconLists(listOf(good), listOf(poisoned)).single()
        assertEquals(47.6062, merged.latitude)
        assertEquals(-122.3321, merged.longitude)
        assertNotNull(merged.eventSchedule())
    }

    @Test
    fun mergeMapBeaconLists_dropsOrphanNullIslandIncoming() {
        val poisoned = MapBeacon(
            id = "event-1",
            kind = MapBeaconKind.EVENT,
            latitude = 0.0,
            longitude = 0.0,
            metadata = MapBeaconMetadata(title = "ghost"),
            expiresAtEpochMs = Clock.System.now().toEpochMilliseconds() + 86_400_000L,
            sourceBeaconType = "event",
        )
        val merged = mergeMapBeaconLists(emptyList(), listOf(poisoned))
        assertTrue(merged.isEmpty())
    }

    @Test
    fun mergeMapBeaconLists_healsExistingNullIslandWhenIncomingEmpty() {
        val poisoned = MapBeacon(
            id = "event-1",
            kind = MapBeaconKind.EVENT,
            latitude = 0.0,
            longitude = 0.0,
            metadata = MapBeaconMetadata(title = "ghost"),
            expiresAtEpochMs = Clock.System.now().toEpochMilliseconds() + 86_400_000L,
            sourceBeaconType = "event",
        )
        val merged = mergeMapBeaconLists(listOf(poisoned), emptyList())
        assertTrue(merged.isEmpty())
    }

    @Test
    fun mergeMapBeaconLists_donatesScheduleFromPoisonedExistingOntoGoodIncoming() {
        val schedule = EventSchedule(
            startEpochMs = Clock.System.now().toEpochMilliseconds() + 3_600_000L,
            endEpochMs = Clock.System.now().toEpochMilliseconds() + 7_200_000L,
        )
        val poisonedWithSchedule = MapBeacon(
            id = "event-1",
            kind = MapBeaconKind.EVENT,
            latitude = 0.0,
            longitude = 0.0,
            metadata = parseMapBeaconMetadata(eventScheduleMetadata(schedule)),
            expiresAtEpochMs = Clock.System.now().toEpochMilliseconds() + 86_400_000L,
            sourceBeaconType = "event",
        )
        val proximity = MapBeacon(
            id = "event-1",
            kind = MapBeaconKind.EVENT,
            latitude = 47.6062,
            longitude = -122.3321,
            metadata = MapBeaconMetadata(title = "birthday"),
            expiresAtEpochMs = Clock.System.now().toEpochMilliseconds() + 86_400_000L,
            sourceBeaconType = "event",
        )
        val merged = mergeMapBeaconLists(listOf(poisonedWithSchedule), listOf(proximity)).single()
        assertEquals(47.6062, merged.latitude)
        assertEquals(-122.3321, merged.longitude)
        assertNotNull(merged.eventSchedule())
        assertEquals(schedule.startEpochMs, merged.eventSchedule()?.startEpochMs)
    }

    private fun hub(id: String, active: Int = 0) = CommunityHubPin(
        hubId = id,
        name = "Hub $id",
        latitude = 1.0,
        longitude = 2.0,
        radiusMeters = 100,
        activeUserCount = active,
    )

    private fun beacon(id: String, expiresAtEpochMs: Long? = null) = MapBeacon(
        id = id,
        kind = MapBeaconKind.SOUNDTRACK,
        latitude = 1.0,
        longitude = 2.0,
        metadata = MapBeaconMetadata(),
        createdByUserId = null,
        createdAtEpochMs = 0L,
        expiresAtEpochMs = expiresAtEpochMs,
        sourceBeaconType = "soundtrack",
    )
}
