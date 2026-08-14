package compose.project.click.click.ui.screens // pragma: allowlist secret

import compose.project.click.click.data.api.EventBookmarkItemDto // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconMetadata // pragma: allowlist secret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SavedEventDetailTest {
    @Test
    fun syntheticBeaconUsesBookmarkIdentityWhenCachesAreEmpty() {
        val bookmark =
            EventBookmarkItemDto(
                beaconId = "evt-9",
                title = "Picnic",
                locationName = "Gas Works",
            )
        val resolved = resolveSavedEventBeacon(bookmark, emptyList(), emptyList())
        assertEquals("evt-9", resolved.id)
        assertEquals(MapBeaconKind.EVENT, resolved.kind)
        assertEquals("Picnic", resolved.metadata.title)
        assertEquals("Gas Works", resolved.metadata.locationName)
    }

    @Test
    fun bookmarkScheduleOverlaysACachedMapBeacon() {
        val cached =
            MapBeacon(
                id = "evt-9",
                kind = MapBeaconKind.EVENT,
                latitude = 47.6,
                longitude = -122.3,
                metadata = MapBeaconMetadata(title = "Stale title"),
            )
        val bookmark =
            EventBookmarkItemDto(
                beaconId = "evt-9",
                title = "Picnic",
                eventStartAt = "2026-08-14T18:00:00Z",
            )
        val resolved = resolveSavedEventBeacon(bookmark, listOf(cached), emptyList())
        assertEquals("evt-9", resolved.id)
        assertEquals(47.6, resolved.latitude)
        assertEquals("Stale title", resolved.metadata.title)
        val raw = resolved.metadata.raw
        assertTrue(raw != null && raw.containsKey("event_start_at"))
    }
}
