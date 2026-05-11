package compose.project.click.click.data.api // pragma: allowlist secret

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThinBffApiSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun connectionEncounterPostBody_roundTrips() {
        val original = ConnectionEncounterPostBody(
            peerUserId = "peer-1",
            contextTag = "coffee",
        )
        val wire = json.encodeToString(ConnectionEncounterPostBody.serializer(), original)
        assertTrue(wire.contains("peer_user_id"))
        val back = json.decodeFromString(ConnectionEncounterPostBody.serializer(), wire)
        assertEquals(original.peerUserId, back.peerUserId)
        assertEquals(original.contextTag, back.contextTag)
    }

    @Test
    fun hubCreateResponse_roundTrips() {
        val dto = HubCreateResponse(
            hubId = "hub_123",
            name = "Night Market",
            channel = "hub:hub_123",
            category = "social",
        )
        val text = json.encodeToString(HubCreateResponse.serializer(), dto)
        val parsed = json.decodeFromString(HubCreateResponse.serializer(), text)
        assertEquals(dto.hubId, parsed.hubId)
        assertEquals(dto.channel, parsed.channel)
        assertEquals(dto.name, parsed.name)
    }
}
