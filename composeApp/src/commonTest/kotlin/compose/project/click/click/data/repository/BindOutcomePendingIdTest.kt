package compose.project.click.click.data.repository // pragma: allowlist secret

import compose.project.click.click.data.api.ProximityBindOkResponseDto
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** G4-5: an instant match keeps this phone's tap id so a late joiner's group can be polled. */
class BindOutcomePendingIdTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun instantMatchCarriesThePendingHandshakeId() {
        val body =
            """
            {"success":true,"encounter_logged":true,"matches":[{"id":"u2","name":"Lena","created_at":0,
             "connection_id":"c1","encounter_logged":true,"is_new_connection":true,"encounter_persisted_on_bind":true}],
             "pending_handshake_id":" ph-9 ","connection_id":"c1","is_new_connection":true,"is_group":false}
            """.trimIndent()
        val outcome = json.decodeFromString(ProximityBindOkResponseDto.serializer(), body).toBindOutcome()
        assertEquals("ph-9", outcome.pendingHandshakeId)
        val without = json.decodeFromString(ProximityBindOkResponseDto.serializer(), body.replace("\" ph-9 \"", "null")).toBindOutcome()
        assertNull(without.pendingHandshakeId)
    }
}
