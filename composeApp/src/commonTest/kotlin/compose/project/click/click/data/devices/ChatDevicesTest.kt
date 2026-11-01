package compose.project.click.click.data.devices

import compose.project.click.click.data.api.MyChatDeviceDto
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatDevicesTest {
    private val now = Instant.parse("2026-11-01T12:00:00Z").toEpochMilliseconds()

    @Test
    fun decodesServerShape() {
        // click-web app/api/me/devices/route.ts
        val list =
            Json { ignoreUnknownKeys = true }.decodeFromString<List<MyChatDeviceDto>>(
                """[{"device_id":"d1","label":"Chrome on Mac","created_at":"2026-10-01T00:00:00Z","last_seen_at":null}]""",
            )
        assertEquals(MyChatDeviceDto("d1", "Chrome on Mac", "2026-10-01T00:00:00Z", null), list.single())
    }

    @Test
    fun thisDeviceComesFirstOthersKeepServerOrder() {
        val list = listOf(MyChatDeviceDto("a"), MyChatDeviceDto("me"), MyChatDeviceDto("b"))
        assertEquals(listOf("me", "a", "b"), orderDevicesThisFirst(list, "me").map { it.deviceId })
        assertEquals(listOf("a", "me", "b"), orderDevicesThisFirst(list, null).map { it.deviceId })
    }

    @Test
    fun namesFallBack() {
        assertEquals("Pixel 9", deviceDisplayName(MyChatDeviceDto("x", " Pixel 9 "), false))
        assertEquals("This phone", deviceDisplayName(MyChatDeviceDto("x", null), true))
        assertEquals("Unnamed device", deviceDisplayName(MyChatDeviceDto("x", ""), false))
    }

    @Test
    fun activityPrefersLastSeenThenAdded() {
        assertEquals("Active 5 minutes ago", deviceActivityLabel(MyChatDeviceDto("x", lastSeenAt = "2026-11-01T11:55:00Z"), now))
        assertEquals("Added yesterday", deviceActivityLabel(MyChatDeviceDto("x", createdAt = "2026-10-31T06:00:00Z"), now))
        assertEquals("", deviceActivityLabel(MyChatDeviceDto("x", lastSeenAt = "bogus"), now))
    }

    @Test
    fun relativeLabels() {
        val m = 60_000L
        assertEquals("just now", relativePastLabel(now - 20_000, now))
        assertEquals("1 minute ago", relativePastLabel(now - m, now))
        assertEquals("3 hours ago", relativePastLabel(now - 3 * 60 * m, now))
        assertEquals("yesterday", relativePastLabel(now - 30 * 60 * m, now))
        assertEquals("4 days ago", relativePastLabel(now - 4 * 24 * 60 * m, now))
        assertEquals("3 weeks ago", relativePastLabel(now - 21 * 24 * 60 * m, now))
        assertEquals("2 years ago", relativePastLabel(now - 800L * 24 * 60 * m, now))
        assertEquals("just now", relativePastLabel(now + 60 * m, now), "future clocks never go negative")
    }
}
