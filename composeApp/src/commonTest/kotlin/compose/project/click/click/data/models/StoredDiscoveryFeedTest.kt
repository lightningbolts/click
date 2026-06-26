package compose.project.click.click.data.models

import compose.project.click.click.ui.utils.mergeMapBeaconLists
import kotlin.test.Test
import kotlin.test.assertEquals

class StoredDiscoveryFeedTest {

    @Test
    fun mapBeacon_roundTripsThroughStoredProjection() {
        val original = MapBeacon(
            id = "beacon-1",
            kind = MapBeaconKind.EVENT,
            latitude = 37.77,
            longitude = -122.42,
            metadata = MapBeaconMetadata(title = "Block party"),
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
