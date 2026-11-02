package compose.project.click.click.data.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReactionsApiTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun thePaletteMatchesWebAndIos() {
        assertEquals(listOf("❤️", "🔥", "😂", "😍", "👏", "😮"), ReactionsState.palette)
    }

    @Test
    fun pathsNameTheKindAndEncodeTheId() {
        assertEquals("https://x/api/reactions/shared_drop/d1", reactionsPath("https://x", ReactionTarget.SHARED_DROP, " d1 "))
        assertEquals("https://x/api/reactions/soundtrack/a%20b", reactionsPath("https://x", ReactionTarget.SOUNDTRACK, "a b"))
    }

    @Test
    fun takingAReactionBackSendsAnExplicitNull() {
        // The route's zod schema is `emoji: string | null`: a missing key is a 400.
        assertEquals("""{"emoji":null}""", json.encodeToString(ReactionPutBody.serializer(), ReactionPutBody(null)))
        assertEquals("""{"emoji":"🔥"}""", json.encodeToString(ReactionPutBody.serializer(), ReactionPutBody("🔥")))
    }

    @Test
    fun decodesTheServerPayload() {
        val state =
            json.decodeFromString(
                ReactionsState.serializer(),
                """{"mine":"🔥","is_owner":false,"reactions":[{"user_id":"u1","name":"Sam Lee","avatar_url":null,"emoji":"😂"}]}""",
            )
        assertEquals("🔥", state.mine)
        assertFalse(state.isOwner)
        assertEquals("Sam", state.reactions.single().firstName)
        assertEquals("u1", state.reactions.single().userId)
        val empty = json.decodeFromString(ReactionsState.serializer(), """{"mine":null,"reactions":[],"is_owner":true}""")
        assertNull(empty.mine)
        assertTrue(empty.isOwner)
    }

    @Test
    fun optimisticPicksOnlyChangeMine() {
        val before = ReactionsState(mine = "😂", reactions = listOf(Reactor("u", "U", null, "❤️")))
        assertEquals(before.copy(mine = "🔥"), before.withMine("🔥"))
        assertEquals(ReactionsState(mine = "🔥"), null.withMine("🔥"))
        assertNull(before.withMine(null).mine)
    }

    @Test
    fun thePickerTakesExactlyOneEmoji() {
        listOf("🔥", "❤️", "👍🏽", "👩‍👩‍👧", "🇫🇷", "✨").forEach { assertTrue(isSingleReactionEmoji(it), it) }
        listOf("", " ", "a", "ok", "🔥🔥", "1", "🔥a", "❤️ ", "́").forEach {
            assertEquals(it == "❤️ ", isSingleReactionEmoji(it), it)
        }
    }
}
