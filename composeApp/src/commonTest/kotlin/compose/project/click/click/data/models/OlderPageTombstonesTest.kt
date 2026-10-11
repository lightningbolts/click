package compose.project.click.click.data.models

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OlderPageTombstonesTest {
    @Test
    fun keepsOnlyTombstonesInsidePageSpan() {
        val page = listOf(1_000L, 1_500L, 2_000L)
        val tombs =
            listOf(
                MessageTombstone("below", "u", 999),
                MessageTombstone("at-min", "u", 1_000),
                MessageTombstone("inside", "u", 1_700),
                MessageTombstone("at-max", "u", 2_000),
                // cursor + include_tombstones has no upper bound: newer ones leak in.
                MessageTombstone("newer", "u", 9_000),
            )
        assertEquals(
            listOf("at-min", "inside", "at-max"),
            clampTombstonesToPage(tombs, page).map { it.messageId },
        )
    }

    @Test
    fun emptyPageKeepsNothing() {
        assertTrue(clampTombstonesToPage(listOf(MessageTombstone("x", "u", 5)), emptyList()).isEmpty())
    }

    @Test
    fun missingTombstonesKeyDecodesAsEmpty() {
        // The route omits `tombstones` when the cursor page is empty.
        val json = Json { ignoreUnknownKeys = true }
        val envelope =
            json.decodeFromString(
                compose.project.click.click.data.api.TombstonesEnvelope
                    .serializer(),
                """{"messages":[]}""",
            )
        assertTrue(envelope.tombstones.isEmpty())
    }
}
