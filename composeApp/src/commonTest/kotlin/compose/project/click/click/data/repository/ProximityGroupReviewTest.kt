package compose.project.click.click.data.repository // pragma: allowlist secret

import compose.project.click.click.data.api.ClickWebRequestException
import compose.project.click.click.data.api.ProximityBindOkResponseDto
import compose.project.click.click.data.api.ProximitySelectionExclusionsPostBody
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProximityGroupReviewTest {
    private val json = Json { ignoreUnknownKeys = true }

    // Shape from click-web lib/server/proximity/bindProximityHandshake.ts (multi-peer branch).
    private fun awaitingBody(existing: String?) =
        """
        {
          "success": true,
          "encounter_logged": false,
          "matches": [
            {"id":"u2","name":"Lena","email":null,"image":null,"created_at":0,
             "connection_id":${existing?.let { "\"$it\"" } ?: "null"},"encounter_logged":false,
             "is_new_connection":${existing == null},"encounter_persisted_on_bind":false},
            {"id":"u3","name":"Sam","email":null,"image":null,"created_at":0,
             "connection_id":${existing?.let { "\"$it\"" } ?: "null"},"encounter_logged":false,
             "is_new_connection":${existing == null},"encounter_persisted_on_bind":false}
          ],
          "awaiting_selection": true,
          "pending_handshake_id": "ph-1",
          "expires_at": "2026-11-01T12:00:00Z",
          "is_group": true,
          "is_new_connection": ${existing == null}
          ${existing?.let { ""","existing_connection_id": "$it"""" } ?: ""},
          "group_clique_candidate": {"member_user_ids": ["u1","u2","u3"]}
        }
        """.trimIndent()

    @Test
    fun existingGroupTapCarriesExistingConnectionId() {
        val dto = json.decodeFromString<ProximityBindOkResponseDto>(awaitingBody("conn-group"))
        assertEquals("conn-group", dto.existingConnectionId)
        val awaiting = assertNotNull(dto.toAwaitingHostSelectionOrNull())
        assertEquals("conn-group", awaiting.existingConnectionId)
        assertEquals("ph-1", awaiting.pendingHandshakeId)
        assertFalse(awaiting.isAggregateNewConnection)
    }

    @Test
    fun newGroupTapHasNoExistingConnectionId() {
        val dto = json.decodeFromString<ProximityBindOkResponseDto>(awaitingBody(null))
        val awaiting = assertNotNull(dto.toAwaitingHostSelectionOrNull())
        assertNull(awaiting.existingConnectionId)
        assertTrue(awaiting.isAggregateNewConnection)
    }

    @Test
    fun selectionBodyUsesServerKeys() {
        val encoded = Json.encodeToString(ProximitySelectionExclusionsPostBody("ph-1", listOf("u3")))
        assertEquals("""{"pending_handshake_id":"ph-1","excluded_member_ids":["u3"]}""", encoded)
    }

    @Test
    fun exclusionsAreUnselectedCandidatesWithoutSelf() {
        assertEquals(
            listOf("u3", "u4"),
            hostSelectionExclusions(
                candidateIds = listOf("u4", "u2", "u1", "u3", "u3", " "),
                selectedIds = setOf("u2"),
                currentUserId = "u1",
            ),
        )
        assertEquals(emptyList(), hostSelectionExclusions(listOf("u2", "u3"), setOf("u2", "u3"), "u1"))
    }

    @Test
    fun classifiesConfirmConflicts() {
        assertTrue(ClickWebRequestException(409, "Handshake already finalized").isProximityHandshakeAlreadyFinalized())
        assertFalse(ClickWebRequestException(404, "Handshake already finalized").isProximityHandshakeAlreadyFinalized())
        assertFalse(ClickWebRequestException(409, "no_members_after_exclusions").isProximityHandshakeAlreadyFinalized())
        assertFalse(IllegalStateException("Handshake already finalized").isProximityHandshakeAlreadyFinalized())
        assertTrue(ClickWebRequestException(409, "no_members_after_exclusions").isProximityNoMembersAfterExclusions())
    }
}
