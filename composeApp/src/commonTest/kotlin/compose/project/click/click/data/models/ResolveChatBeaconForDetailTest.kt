package compose.project.click.click.data.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ResolveChatBeaconForDetailTest {

    private fun stub(
        id: String = "beacon-1",
        kind: MapBeaconKind = MapBeaconKind.OTHER,
        sourceBeaconType: String? = null,
    ) = MapBeacon(
        id = id,
        kind = kind,
        latitude = 37.0,
        longitude = -122.0,
        metadata = MapBeaconMetadata(title = "Meetup"),
        sourceBeaconType = sourceBeaconType,
    )

    @Test
    fun coalesce_cacheOther_withEventMetadata_toEvent() {
        val cached = stub(kind = MapBeaconKind.OTHER)
        val meta = buildJsonObject {
            put("beacon_id", "beacon-1")
            put("beacon_type", "event")
            put("title", "Meetup")
        }
        val fallback = mapBeaconFromChatMetadata("beacon-1", meta, "Beacon: Meetup")
        assertNotNull(fallback)
        assertEquals(MapBeaconKind.EVENT, fallback.kind)

        val resolved = resolveChatBeaconForDetail(cached, fallback, meta)
        assertNotNull(resolved)
        assertEquals(MapBeaconKind.EVENT, resolved.kind)
        assertTrue(chatBeaconLooksLikeEvent(fallback, meta))
    }

    @Test
    fun coalesce_keepsEventCacheUnchanged() {
        val cached = stub(kind = MapBeaconKind.EVENT, sourceBeaconType = "event")
        val meta = buildJsonObject { put("beacon_type", "event") }
        val resolved = resolveChatBeaconForDetail(cached, cached, meta)
        assertNotNull(resolved)
        assertEquals(MapBeaconKind.EVENT, resolved.kind)
        assertEquals("event", resolved.sourceBeaconType)
    }

    @Test
    fun coalesce_nonEventMetadata_leavesOther() {
        val cached = stub(kind = MapBeaconKind.OTHER)
        val meta = buildJsonObject { put("beacon_type", "hazard") }
        val resolved = resolveChatBeaconForDetail(cached, null, meta)
        assertNotNull(resolved)
        assertEquals(MapBeaconKind.OTHER, resolved.kind)
    }
}
