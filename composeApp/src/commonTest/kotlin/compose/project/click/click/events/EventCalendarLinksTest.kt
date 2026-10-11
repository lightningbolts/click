package compose.project.click.click.events

import compose.project.click.click.data.models.MapBeacon
import compose.project.click.click.data.models.MapBeaconKind
import compose.project.click.click.data.models.MapBeaconMetadata
import io.ktor.http.Url
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventCalendarLinksTest {
    // iOS EventExtrasTests.EventCalendarLinkTests fixture.
    private fun beacon(description: String? = null) =
        MapBeacon(
            id = "e1",
            kind = MapBeaconKind.EVENT,
            latitude = 47.6,
            longitude = -122.3,
            metadata =
                MapBeaconMetadata(
                    title = "Open mic & tea",
                    description = description,
                    locationName = "Café Allegro",
                    raw =
                        buildJsonObject {
                            put("title", JsonPrimitive("Open mic & tea"))
                            put("event_start_at", JsonPrimitive("2026-10-08T02:00:00Z"))
                            put("event_end_at", JsonPrimitive("2026-10-08T04:30:00Z"))
                        },
                ),
            sourceBeaconType = "event",
        )

    private fun entry(description: String? = null) = assertNotNull(eventCalendarEntry(beacon(description), "Open mic & tea"))

    @Test
    fun google_getsUtcCompactDatesTitleAndPlace() {
        val url = Url(googleCalendarUrl(entry()))
        assertEquals("calendar.google.com", url.host)
        assertEquals("/calendar/render", url.encodedPath)
        assertEquals("TEMPLATE", url.parameters["action"])
        assertEquals("Open mic & tea", url.parameters["text"])
        assertEquals("20261008T020000Z/20261008T043000Z", url.parameters["dates"])
        assertEquals("Café Allegro", url.parameters["location"])
        assertEquals("https://joinclick.co/e/e1", url.parameters["details"])
    }

    @Test
    fun google_percentEncodesReservedCharacters() {
        val raw = googleCalendarUrl(entry())
        assertTrue(raw.contains("text=Open%20mic%20%26%20tea"), raw)
        assertTrue(raw.contains("dates=20261008T020000Z%2F20261008T043000Z"), raw)
        assertTrue(raw.contains("location=Caf%C3%A9%20Allegro"), raw)
        assertTrue(raw.contains("details=https%3A%2F%2Fjoinclick.co%2Fe%2Fe1"), raw)
    }

    @Test
    fun outlook_getsIsoDatesAndTheSameDetails() {
        val url = Url(outlookCalendarUrl(entry()))
        assertEquals("outlook.live.com", url.host)
        assertEquals("/calendar/0/action/compose", url.encodedPath)
        assertEquals("addevent", url.parameters["rru"])
        assertEquals("Open mic & tea", url.parameters["subject"])
        assertEquals("2026-10-08T02:00:00Z", url.parameters["startdt"])
        assertEquals("2026-10-08T04:30:00Z", url.parameters["enddt"])
        assertEquals("Café Allegro", url.parameters["location"])
        assertEquals("https://joinclick.co/e/e1", url.parameters["body"])
        assertTrue(outlookCalendarUrl(entry()).contains("startdt=2026-10-08T02%3A00%3A00Z"))
    }

    @Test
    fun deviceCalendarDescription_appendsTheEventLink() {
        assertEquals("Bring a song.\n\nhttps://joinclick.co/e/e1", entry("Bring a song.").details)
        assertEquals("https://joinclick.co/e/e1", entry("   ").details)
        assertEquals("https://joinclick.co/e/abc", eventCalendarDetails(null, "abc"))
    }

    @Test
    fun stamps_areUtc() {
        val ms = 1_791_424_800_000L // 2026-10-08T02:00:00Z
        assertEquals("20261008T020000Z", compactUtcStamp(ms))
        assertEquals("2026-10-08T02:00:00Z", isoUtcStamp(ms))
    }

    @Test
    fun entry_needsASchedule() {
        val noSchedule = beacon().copy(metadata = MapBeaconMetadata(title = "x"))
        assertNull(eventCalendarEntry(noSchedule, "x"))
    }
}
