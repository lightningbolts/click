package compose.project.click.click.data.repository

import compose.project.click.click.data.api.ProximityBindOkResponseDto
import compose.project.click.click.data.api.ProximitySelectionPostBody
import compose.project.click.click.viewmodel.proximityExcludedMemberIds
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** G0-4: `awaiting_selection` with and without `existing_connection_id` (fixtures from iOS ProximityCodecTests). */
class ProximityAwaitingSelectionTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(raw: String) = json.decodeFromString(ProximityBindOkResponseDto.serializer(), raw).toAwaitingHostSelectionOrNull()

    @Test
    fun firstTimeGroupTapHasNoExistingConnection() {
        val result =
            assertNotNull(
                parse(
                    """{"awaiting_selection":true,"pending_handshake_id":"p1","matches":[{"id":"a","name":"A"},{"id":"b","name":"B"}]}""",
                ),
            )
        assertEquals("p1", result.pendingHandshakeId)
        assertEquals(listOf("a", "b"), result.candidates.map { it.id })
        assertNull(result.existingConnectionId)
    }

    @Test
    fun existingGroupTapCarriesTheGroup() {
        val result =
            assertNotNull(
                parse(
                    """{"awaiting_selection":true,"pending_handshake_id":"p2","existing_connection_id":"g9",""" +
                        """"is_new_connection":false,"matches":[{"id":"a","name":"A"},{"id":"b","name":"B"}]}""",
                ),
            )
        assertEquals("p2", result.pendingHandshakeId)
        assertEquals(2, result.candidates.size)
        assertEquals("g9", result.existingConnectionId)
        assertEquals(false, result.isAggregateNewConnection)
    }

    @Test
    fun blankExistingConnectionIsIgnored() {
        val result =
            assertNotNull(
                parse("""{"awaiting_selection":true,"pending_handshake_id":"p3","existing_connection_id":" ","matches":[{"id":"a"}]}"""),
            )
        assertNull(result.existingConnectionId)
    }

    @Test
    fun selectionBodyIsSnakeCase() {
        val encoded =
            Json.encodeToString(
                ProximitySelectionPostBody.serializer(),
                ProximitySelectionPostBody(pendingHandshakeId = "p1", excludedMemberIds = listOf("b")),
            )
        assertEquals("""{"pending_handshake_id":"p1","excluded_member_ids":["b"]}""", encoded)
    }

    @Test
    fun excludedIdsAreTheUnselectedCandidates() {
        assertEquals(setOf("b"), proximityExcludedMemberIds(setOf("a", "b", "c"), setOf("a", "c")))
        assertEquals(emptySet(), proximityExcludedMemberIds(setOf("a", "b"), setOf("a", "b")))
        assertEquals(setOf("a", "b"), proximityExcludedMemberIds(setOf("a", "b"), emptySet()))
    }
}
