package compose.project.click.click.ui.utils // pragma: allowlist secret

import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconKind // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconMetadata // pragma: allowlist secret
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
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
