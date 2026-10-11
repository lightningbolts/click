package compose.project.click.click.collaboration

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fixtures from iOS `SharedDropsTests`. */
class SharedDropsTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun strip(body: String): List<SharedDrop> =
        json
            .decodeFromString(
                compose.project.click.click.data.api.SharedDropsStripResponse
                    .serializer(),
                body,
            ).drops

    @Test
    fun theStripParsesStatesFromRevealTimeAndMyDevelopedAt() {
        val drops =
            strip(
                """
                {"teaser":"pixelated","drops":[
                  {"id":"a","user":{"id":"u1","name":"Maya"},"is_mine":false,"connection_id":"c1",
                   "reveal_at":"2000-01-01T00:00:00Z","developed_at":null},
                  {"id":"b","user":{"id":"u1","name":"Maya"},"is_mine":false,"connection_id":"c1",
                   "reveal_at":"2000-01-01T00:00:00Z","developed_at":"2000-01-02T00:00:00Z"},
                  {"id":"c","user":{"id":"me","name":"Me"},"is_mine":true,"audience":"core",
                   "reveal_at":"2999-01-01T00:00:00Z"}
                ]}
                """.trimIndent(),
            )
        assertEquals(
            listOf(
                ClickDropDevelopState.Ready,
                ClickDropDevelopState.Developed,
                ClickDropDevelopState.Pending(Instant.parse("2999-01-01T00:00:00Z")),
            ),
            drops.map { it.state() },
        )
        assertEquals("c1", drops[0].connectionId)
        assertEquals("core", drops[2].audience)
        assertNull(drops[0].audience)
    }

    @Test
    fun theStripGroupsByPersonYoursFirstThenUnwatchedThenTheRest() {
        val past = "2000-01-01T00:00:00Z"
        val seen = "\"2000-01-02T00:00:00Z\""

        fun row(
            id: String,
            user: String,
            mine: Boolean = false,
            developed: String = "null",
            reveal: String = past,
        ) = """{"id":"$id","user":{"id":"$user","name":"$user"},"is_mine":$mine,"reveal_at":"$reveal","developed_at":$developed}"""
        // Newest first, as the server sends them.
        val rows =
            listOf(
                row("m3", "maya", developed = seen),
                row("j2", "jo", developed = seen),
                row("me1", "me", mine = true, reveal = "2999-01-01T00:00:00Z"),
                row("m2", "maya"),
                row("m1", "maya", developed = seen),
                row("j1", "jo", developed = seen),
                row("k1", "kai"),
            )
        val groups = SharedDropGroup.group(strip("""{"drops":[${rows.joinToString(",")}]}"""))
        // Unwatched people (maya, kai) come before jo, even though jo posted after kai.
        assertEquals(listOf("me", "maya", "kai", "jo"), groups.map { it.userId })
        assertEquals(listOf("m1", "m2", "m3"), groups[1].drops.map { it.id })
        assertEquals("m2", groups[1].start()?.id)
        assertEquals("m2", groups[1].cover().id)
        assertEquals("j1", groups[3].start()?.id)
        assertNull(groups[0].start())
        assertEquals("me1", groups[0].cover().id)
        assertTrue(groups[2].hasUnwatched())
        assertFalse(groups[3].hasUnwatched())
    }

    @Test
    fun archivePagesCarryTheirCursor() {
        val page =
            json.decodeFromString(
                SharedDropArchivePage.serializer(),
                """{"drops":[{"id":"a","user":{"id":"u1","name":"Maya"},"reveal_at":"2000-01-01T00:00:00Z"}],"next_before":"2026-10-01T00:00:00Z"}""",
            )
        assertEquals(listOf("a"), page.drops.map { it.id })
        assertEquals("2026-10-01T00:00:00Z", page.nextBefore)
    }

    @Test
    fun inlineReactionsAndCaptionDecode() {
        val drop =
            strip(
                """{"drops":[{"id":"n","user":{"id":"u","name":"Maya Lee"},"caption":"sunset",
                "reactions":{"mine":"🔥","is_owner":false,"reactions":[{"user_id":"x","name":"Jo","avatar_url":null,"emoji":"❤️"}]}}]}""",
            ).single()
        assertEquals("sunset", drop.caption)
        assertEquals("🔥", drop.reactions?.mine)
        assertEquals(
            "Jo",
            drop.reactions
                ?.reactions
                ?.single()
                ?.name,
        )
        assertEquals("Maya", drop.user.firstName)
    }

    @Test
    fun captionsCountGraphemesNotCodeUnits() {
        assertTrue(sharedDropCaptionFits("👍🏽".repeat(100)))
        assertFalse(sharedDropCaptionFits("a".repeat(101)))
        assertTrue(sharedDropCaptionFits("a".repeat(100)))
    }

    @Test
    fun graphemeCountMatchesWhatPeopleSee() {
        // Escapes keep the invisible joiners and selectors readable.
        val thumbsUpMedium = "\uD83D\uDC4D\uD83C\uDFFD"
        assertEquals(0, graphemeCount(""))
        assertEquals(5, graphemeCount("hello"))
        assertEquals(1, graphemeCount(thumbsUpMedium))
        // Family: man ZWJ woman ZWJ girl ZWJ boy.
        assertEquals(1, graphemeCount("\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66"))
        // Red heart with emoji presentation selector.
        assertEquals(1, graphemeCount("\u2764\uFE0F"))
        // Keycap one.
        assertEquals(1, graphemeCount("1\uFE0F\u20E3"))
        // Two flags (US, JP) are two regional-indicator pairs.
        assertEquals(2, graphemeCount("\uD83C\uDDFA\uD83C\uDDF8\uD83C\uDDEF\uD83C\uDDF5"))
        // Scotland: black flag plus tag sequence.
        assertEquals(1, graphemeCount("\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC73\uDB40\uDC63\uDB40\uDC74\uDB40\uDC7F"))
        // e plus combining acute accent.
        assertEquals(1, graphemeCount("e\u0301"))
        assertEquals(1, graphemeCount("\r\n"))
        assertEquals(3, graphemeCount("a${thumbsUpMedium}b"))
    }
}
