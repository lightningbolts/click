package compose.project.click.click.data.activity

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActivityTest {
    private val utc = TimeZone.UTC
    private val now = Instant.parse("2026-11-07T18:00:00Z").toEpochMilliseconds()

    private fun item(
        id: String,
        at: String,
        type: String = "reaction",
        data: Map<String, String> = emptyMap(),
    ) = ActivityItemDto(id = id, type = type, createdAt = at, data = data)

    @Test
    fun decodesServerPage() {
        // click-web GET /api/activity
        val page =
            Json { ignoreUnknownKeys = true }.decodeFromString<ActivityPageDto>(
                """{"items":[{"id":"a1","type":"prior_connection_request","title":"Maya wants to connect","body":"From the meetup",
                    "data":{"type":"prior_connection_request","connection_id":"c1"},"created_at":"2026-11-07T17:00:00Z",
                    "actor":{"id":"u1","name":"Maya","avatar_url":null}}],
                   "seen_at":"2026-11-07T10:00:00Z","next_before":null,"pending_requests":["c1"]}""",
            )
        assertEquals("c1", page.items.single().requestConnectionId)
        assertEquals(listOf("c1"), page.pendingRequests)
        assertNull(item("x", "2026-11-07T17:00:00Z", data = mapOf("connection_id" to "c")).requestConnectionId, "only requests")
    }

    @Test
    fun groupsLikeWeb() {
        val items =
            listOf(
                item("new", "2026-11-07T17:30:00Z"),
                item("today", "2026-11-07T08:00:00Z"),
                item("yesterday", "2026-11-06T12:00:00Z"),
                item("week", "2026-11-03T12:00:00Z"),
                item("earlier", "2026-10-20T12:00:00Z"),
            )
        val groups = groupActivity(items, seenAt = "2026-11-07T09:00:00Z", nowEpochMs = now, timeZone = utc)
        assertEquals(
            listOf(ActivityGroup.NEW, ActivityGroup.TODAY, ActivityGroup.YESTERDAY, ActivityGroup.WEEK, ActivityGroup.EARLIER),
            groups.map { it.first },
        )
        assertEquals(listOf("new"), groups.first().second.map { it.id })
    }

    @Test
    fun neverSeenMakesEverythingNew() {
        val groups = groupActivity(listOf(item("a", "2026-10-01T00:00:00Z")), seenAt = null, nowEpochMs = now, timeZone = utc)
        assertEquals(ActivityGroup.NEW, groups.single().first)
    }

    @Test
    fun unseenCountAndMerge() {
        val a = item("a", "2026-11-07T17:00:00Z")
        val b = item("b", "2026-11-07T08:00:00Z")
        assertEquals(1, unseenActivityCount(listOf(a, b), "2026-11-07T09:00:00Z"))
        val regrouped = a.copy(createdAt = "2026-11-07T17:50:00Z", title = "Maya and 2 others reacted")
        val merged = mergeActivityPages(listOf(a, b), listOf(regrouped))
        assertEquals(listOf("a", "b"), merged.map { it.id })
        assertEquals("Maya and 2 others reacted", merged.first().title, "the newest copy wins")
    }
}
