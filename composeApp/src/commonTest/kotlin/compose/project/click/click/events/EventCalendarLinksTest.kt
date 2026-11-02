package compose.project.click.click.events // pragma: allowlist secret

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventCalendarLinksTest {
    private val start = Instant.parse("2026-11-07T03:30:00Z").toEpochMilliseconds()
    private val end = Instant.parse("2026-11-07T06:00:05Z").toEpochMilliseconds()
    private val draft =
        EventCalendarDraft(
            title = "Jazz & Tacos",
            startEpochMs = start,
            endEpochMs = end,
            location = "Blue Note, 131 W 3rd St",
            details = "Bring friends\n\nhttps://joinclick.co/e/b1",
        )

    @Test
    fun utcFormats() {
        assertEquals("20261107T033000Z", compactUtc(start))
        assertEquals("2026-11-07T06:00:05Z", isoUtc(end))
    }

    @Test
    fun googleUrlMatchesIos() {
        val url = googleCalendarUrl(draft)
        assertTrue(url.startsWith("https://calendar.google.com/calendar/render?action=TEMPLATE&text=Jazz"), url)
        assertTrue("dates=20261107T033000Z%2F20261107T060005Z" in url, url)
        assertTrue("%26" in url, "ampersand in the title is escaped: $url")
        assertTrue("details=Bring" in url, url)
    }

    @Test
    fun outlookUrlMatchesIos() {
        val url = outlookCalendarUrl(draft)
        assertTrue(url.startsWith("https://outlook.live.com/calendar/0/action/compose?rru=addevent&subject="), url)
        assertTrue("startdt=2026-11-07T03%3A30%3A00Z" in url, url)
        assertTrue("enddt=2026-11-07T06%3A00%3A05Z" in url, url)
    }

    @Test
    fun missingLocationIsLeftOut() {
        val url = googleCalendarUrl(draft.copy(location = null))
        assertTrue("location=" !in url, url)
    }

    @Test
    fun directionsUrl() {
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=47.6,-122.3",
            googleMapsDirectionsUrl(47.6, -122.3),
        )
    }
}
