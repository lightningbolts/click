package compose.project.click.click.viewmodel

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HubChatSupportTest {
    @Test
    fun reactionDeletePayloadExtractsStableIdentifiers() {
        val record =
            buildJsonObject {
                put("id", "reaction-1")
                put("hub_message_id", "message-1")
                put("hub_id", "hub-1")
            }

        assertEquals("reaction-1", record.hubReactionRowId())
        assertEquals("message-1", record.hubReactionMessageId())
        assertEquals("hub-1", record.hubReactionHubId())
    }

    @Test
    fun deletePayloadHelpersRejectBlankIdentifiers() {
        val record =
            buildJsonObject {
                put("id", " ")
                put("hub_message_id", "")
                put("hub_id", "   ")
            }

        assertNull(record.hubReactionRowId())
        assertNull(record.hubReactionMessageId())
        assertNull(record.hubReactionHubId())
    }

    @Test
    fun hubTimestampParsingUsesIsoInstant() {
        assertEquals(1_789_603_200_000L, hubCreatedAtToEpoch("2026-09-17T00:00:00Z"))
    }
}
