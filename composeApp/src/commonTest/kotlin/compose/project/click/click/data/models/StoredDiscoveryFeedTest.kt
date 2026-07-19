package compose.project.click.click.data.models

import compose.project.click.click.events.eventSchedule
import compose.project.click.click.ui.utils.mergeMapBeaconLists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class StoredDiscoveryFeedTest {

    @Test
    fun mapBeacon_roundTripsThroughStoredProjection() {
        val original = MapBeacon(
            id = "beacon-1",
            kind = MapBeaconKind.EVENT,
            latitude = 37.77,
            longitude = -122.42,
            metadata = MapBeaconMetadata(
                title = "Block party",
                raw = buildJsonObject {
                    put("title", JsonPrimitive("Block party"))
                    put("event_start_at", JsonPrimitive("2026-07-22T16:00:00Z"))
                    put("event_end_at", JsonPrimitive("2026-07-22T23:00:00Z"))
                },
            ),
            createdByUserId = "user-1",
            createdAtEpochMs = 1_700_000_000_000L,
            expiresAtEpochMs = 1_800_000_000_000L,
            sourceBeaconType = "event",
            showCreatorName = true,
            creatorDisplayName = "Sam",
        )
        val restored = original.toStoredMapBeacon().toMapBeacon()
        assertEquals(original.id, restored.id)
        assertEquals(original.kind, restored.kind)
        assertEquals(original.latitude, restored.latitude)
        assertEquals(original.longitude, restored.longitude)
        assertEquals(original.metadata.title, restored.metadata.title)
        assertEquals(original.creatorDisplayName, restored.creatorDisplayName)
        val schedule = restored.eventSchedule()
        assertNotNull(schedule)
        assertEquals(original.eventSchedule()?.startEpochMs, schedule.startEpochMs)
        assertEquals(original.eventSchedule()?.endEpochMs, schedule.endEpochMs)
    }

    @Test
    fun mergeMapBeaconLists_preservesScheduleWhenIncomingLacksIt() {
        val withSchedule = MapBeacon(
            id = "a",
            kind = MapBeaconKind.EVENT,
            latitude = 1.0,
            longitude = 2.0,
            metadata = MapBeaconMetadata(
                title = "birthday",
                raw = buildJsonObject {
                    put("event_start_at", JsonPrimitive("2026-07-22T16:00:00Z"))
                    put("event_end_at", JsonPrimitive("2026-07-22T23:00:00Z"))
                },
            ),
            createdAtEpochMs = 1_700_000_000_000L,
            expiresAtEpochMs = 1_900_000_000_000L,
            sourceBeaconType = "event",
        )
        val stripped = MapBeacon(
            id = "a",
            kind = MapBeaconKind.EVENT,
            latitude = 1.1,
            longitude = 2.1,
            metadata = MapBeaconMetadata(title = "birthday"),
            createdAtEpochMs = 1_700_000_000_000L,
            expiresAtEpochMs = 1_900_000_000_000L,
            sourceBeaconType = "event",
        )
        val merged = mergeMapBeaconLists(listOf(withSchedule), listOf(stripped)).single()
        assertEquals(1.1, merged.latitude)
        assertNotNull(merged.eventSchedule())
        assertEquals(withSchedule.eventSchedule()?.startEpochMs, merged.eventSchedule()?.startEpochMs)
    }

    @Test
    fun mergeMapBeaconLists_keepsExistingWhenIncomingEmpty() {
        val existing = listOf(
            MapBeacon(
                id = "a",
                kind = MapBeaconKind.OTHER,
                latitude = 1.0,
                longitude = 2.0,
                metadata = MapBeaconMetadata(),
            ),
        )
        assertEquals(existing, mergeMapBeaconLists(existing, emptyList()))
    }
}
