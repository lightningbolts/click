package compose.project.click.click.collaboration

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Fixtures from iOS `ClickDropDevelopTests` and click-web `lib/drops/developState.ts`. */
class ClickDropDevelopStateTest {
    private val reveal = Instant.fromEpochSeconds(1_000)

    @Test
    fun pendingBeforeRevealReadyAfterDevelopedOnceDeveloped() {
        assertEquals(ClickDropDevelopState.Pending(reveal), ClickDropDevelopState.resolve(reveal, null, reveal - 1.seconds))
        // Developed state never shows before reveal.
        assertEquals(ClickDropDevelopState.Pending(reveal), ClickDropDevelopState.resolve(reveal, reveal, reveal - 1.seconds))
        assertEquals(ClickDropDevelopState.Ready, ClickDropDevelopState.resolve(reveal, null, reveal))
        assertEquals(ClickDropDevelopState.Developed, ClickDropDevelopState.resolve(reveal, reveal, reveal + 5.seconds))
        assertTrue(ClickDropDevelopState.resolve(null, null).isPending)
    }

    @Test
    fun chatDropsRevealTwentyFourHoursAfterSend() {
        val now = Instant.parse("2026-10-01T10:00:00Z")
        assertEquals("2026-10-02T10:00:00Z", chatClickDropRevealAtIso(now))
    }

    @Test
    fun developResponsesMapToTypedResults() {
        val body =
            """
            {"drops":[
              {"kind":"chat","id":"a","status":"developed","developed_at":"2026-10-01T10:05:00Z","url":"https://signed.example/o"},
              {"kind":"chat","id":"b","status":"pending","reveal_at":"2026-10-02T10:00:00Z"},
              {"kind":"chat","id":"c","status":"not_found"}
            ]}
            """.trimIndent()
        val results = parseClickDropDevelopResults(Json.parseToJsonElement(body))
        assertEquals(
            listOf(ClickDropDevelopResult.Status.DEVELOPED, ClickDropDevelopResult.Status.PENDING, ClickDropDevelopResult.Status.NOT_FOUND),
            results.map { it.status },
        )
        assertEquals("https://signed.example/o", results[0].originalUrl)
        assertEquals(Instant.parse("2026-10-01T10:05:00Z"), results[0].developedAt)
        assertNull(results[1].originalUrl)
        assertEquals(ClickDropRef(ClickDropKind.CHAT, "c"), results[2].ref)
    }

    @Test
    fun viewsMapIdsToDevelopedAt() {
        val views = parseClickDropViews(Json.parseToJsonElement("""{"developed":{"a":"2026-10-01T10:05:00Z","b":null}}"""))
        assertEquals(mapOf("a" to Instant.parse("2026-10-01T10:05:00Z")), views)
    }
}
