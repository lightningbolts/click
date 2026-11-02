package compose.project.click.click.collaboration

import compose.project.click.click.data.api.SharedDropArchivePage
import compose.project.click.click.data.api.sharedDropArchiveUrl
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedDropArchiveTest {
    private val now = Instant.parse("2026-10-20T15:00:00Z")
    private val utc = TimeZone.UTC

    private fun title(iso: String) = sharedDropArchiveSectionTitle(Instant.parse(iso), now, utc)

    private fun drop(
        id: String,
        created: String? = null,
        user: String = "u",
        reveal: String = "2026-10-01T00:00:00Z",
    ) = SharedDrop(id = id, user = SharedDropUser(user, user), createdAt = created, revealAtIso = reveal)

    @Test
    fun headersFollowIos() {
        assertEquals("Today", title("2026-10-20T00:10:00Z"))
        assertEquals("Yesterday", title("2026-10-19T23:00:00Z"))
        assertEquals("This week", title("2026-10-15T09:00:00Z"))
        assertEquals("This month", title("2026-10-02T09:00:00Z"))
        assertEquals("September 2026", title("2026-09-30T09:00:00Z"))
        assertEquals("December 2025", title("2025-12-31T09:00:00Z"))
    }

    @Test
    fun headersUseTheLocalDay() {
        val la = TimeZone.of("America/Los_Angeles")
        // 03:00 UTC on the 20th is still the 19th in Los Angeles.
        assertEquals("Yesterday", sharedDropArchiveSectionTitle(Instant.parse("2026-10-20T03:00:00Z"), now, la))
    }

    @Test
    fun consecutiveDropsShareAHeader() {
        val sections =
            sharedDropArchiveSections(
                listOf(
                    drop("a", "2026-10-20T10:00:00Z"),
                    drop("b", "2026-10-20T09:00:00Z"),
                    drop("c", "2026-10-19T09:00:00Z"),
                    drop("d", "2026-08-01T09:00:00Z"),
                    drop("e", null),
                ),
                now,
                utc,
            )
        assertEquals(listOf("Today", "Yesterday", "August 2026", "Today"), sections.map { it.title })
        assertEquals(listOf("a", "b"), sections.first().drops.map { it.id })
    }

    @Test
    fun archiveUrlsPageByCreatedAt() {
        assertEquals("https://x/api/me/shared-drops/archive?limit=30", sharedDropArchiveUrl("https://x/api/me/shared-drops", null))
        assertEquals(
            "https://x/api/me/shared-drops/archive?limit=30&before=2026-10-01T10%3A00%3A00%2B00%3A00",
            sharedDropArchiveUrl("https://x/api/me/shared-drops", "2026-10-01T10:00:00+00:00"),
        )
    }

    @Test
    fun decodesAnArchivePageWithInlineReactions() {
        val page =
            Json { ignoreUnknownKeys = true }.decodeFromString(
                SharedDropArchivePage.serializer(),
                """{"drops":[{"id":"a","user":{"id":"u","name":"Maya Lin"},"reveal_at":"2026-10-01T00:00:00Z",
                  "reactions":{"mine":"🔥","reactions":[],"is_owner":false}}],"next_before":null}""",
            )
        assertEquals(
            "🔥",
            page.drops
                .single()
                .reactions
                ?.mine,
        )
        assertNull(page.nextBefore)
    }

    @Test
    fun aPushedDropOpensOnceItsInTheStripAndDeveloped() {
        val groups =
            SharedDropGroup.group(
                listOf(drop("a", user = "maya"), drop("b", user = "sam"), drop("p", user = "sam", reveal = "2026-10-21T00:00:00Z")),
                now,
            )
        val samIndex = groups.indexOfFirst { it.userId == "sam" }
        assertEquals(samIndex, openableGroupIndex("b", groups, now))
        assertNull(openableGroupIndex("p", groups, now))
        assertNull(openableGroupIndex("missing", groups, now))
    }

    @Test
    fun theArchivePlaysAsOneStoryInGridOrder() {
        val group = archiveStoryGroup(listOf(drop("a"), drop("p", reveal = "2026-10-21T00:00:00Z"), drop("b")))
        assertEquals(listOf("a", "b"), group.viewable(now).map { it.id })
    }
}
