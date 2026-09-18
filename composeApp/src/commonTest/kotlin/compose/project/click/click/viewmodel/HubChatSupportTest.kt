package compose.project.click.click.viewmodel

import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.MessageReaction
import compose.project.click.click.data.models.MessageWithUser
import compose.project.click.click.data.models.User
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
    fun pkOnlyReactionDeleteResolvesFromCurrentState() {
        val reaction =
            MessageReaction(
                id = "reaction-1",
                messageId = "message-1",
                userId = "user-1",
                reactionType = "❤️",
                createdAt = 1L,
            )
        val event = hubReactionDeleteEvent(buildJsonObject { put("id", "reaction-1") })
        val state =
            event?.let {
                applyHubReactionRealtimeEvent(
                    current = mapOf("message-1" to listOf(reaction)),
                    event = it,
                )
            }

        assertEquals(HubReactionRealtimeEvent.Delete("reaction-1"), event)
        assertEquals(null, state?.get("message-1"))
    }

    @Test
    fun realtimeReplayMakesEventsAfterSnapshotWin() {
        val stale =
            MessageReaction(
                id = "reaction-old",
                messageId = "message-1",
                userId = "user-1",
                reactionType = "👍",
                createdAt = 1L,
            )
        val fresh =
            MessageReaction(
                id = "reaction-new",
                messageId = "message-1",
                userId = "user-2",
                reactionType = "❤️",
                createdAt = 2L,
            )

        val state =
            replayHubReactionEvents(
                snapshot = listOf(stale),
                events =
                    listOf(
                        HubReactionRealtimeEvent.Delete("reaction-old", "message-1"),
                        HubReactionRealtimeEvent.Upsert(fresh),
                    ),
            )

        assertEquals(listOf(fresh), state["message-1"])
    }

    @Test
    fun reactionRollbackPreservesConcurrentRealtimeRows() {
        val removed =
            MessageReaction(
                id = "mine",
                messageId = "message-1",
                userId = "me",
                reactionType = "👍",
                createdAt = 1L,
            )
        val concurrent =
            MessageReaction(
                id = "peer",
                messageId = "message-1",
                userId = "peer",
                reactionType = "❤️",
                createdAt = 2L,
            )
        val restored =
            rollbackHubReactionMutation(
                current = listOf(concurrent),
                optimisticId = null,
                removedExisting = removed,
            )

        assertEquals(setOf("mine", "peer"), restored.map { it.id }.toSet())

        val canonical =
            MessageReaction(
                id = "canonical",
                messageId = "message-1",
                userId = "me",
                reactionType = "😂",
                createdAt = 3L,
            )
        val afterFailedAdd =
            rollbackHubReactionMutation(
                current =
                    listOf(
                        concurrent,
                        MessageReaction(
                            id = "temp-message-1-😂",
                            messageId = "message-1",
                            userId = "me",
                            reactionType = "😂",
                            createdAt = 2L,
                        ),
                        canonical,
                    ),
                optimisticId = "temp-message-1-😂",
                removedExisting = null,
            )

        assertEquals(setOf("peer", "canonical"), afterFailedAdd.map { it.id }.toSet())
    }

    @Test
    fun reactionRollbackDoesNotResurrectRealtimeDeletedRow() {
        val removed =
            MessageReaction(
                id = "mine",
                messageId = "message-1",
                userId = "me",
                reactionType = "👍",
                createdAt = 1L,
            )

        val rolledBack =
            rollbackHubReactionMutation(
                current = emptyList(),
                optimisticId = null,
                removedExisting = removed,
                restoreRemovedExisting = false,
            )

        assertEquals(emptyList(), rolledBack)
    }

    @Test
    fun messageDeleteRollbackUsesNewestRealtimeVersion() {
        val original =
            MessageWithUser(
                message =
                    Message(
                        id = "message-1",
                        user_id = "me",
                        content = "old",
                        timeCreated = 1L,
                    ),
                user = User(id = "me", name = "You"),
                isSent = true,
            )
        val updated =
            original.copy(
                message =
                    original.message.copy(
                        content = "new",
                        timeEdited = 2L,
                    ),
            )

        val pending =
            PendingHubMessageDelete(
                removed = original,
                index = 0,
                latestRealtime = updated,
            )

        assertEquals("new", pending.rollbackMessage().message.content)
        assertEquals(2L, pending.rollbackMessage().message.timeEdited)
    }

    @Test
    fun hubTimestampParsingUsesIsoInstant() {
        assertEquals(1_789_603_200_000L, hubCreatedAtToEpoch("2026-09-17T00:00:00Z"))
    }
}
