package compose.project.click.click.chat

import compose.project.click.click.data.api.MyDeviceDto
import compose.project.click.click.data.api.MyDevicesDto
import compose.project.click.click.ui.screens.deviceActivityLine
import compose.project.click.click.ui.screens.deviceRowName
import compose.project.click.click.ui.screens.removeDeviceTitle
import compose.project.click.click.ui.screens.signedInLine
import compose.project.click.click.viewmodel.sortDevicesForSettings
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Port of iOS `ChatDevicesTests.swift` plus the Settings › Devices copy rules (sheet 10 §A5). */
class ChatDevicesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun readsMeDevicesRowsSkippingAnyWithoutAnId() {
        val dto =
            json.decodeFromString(
                MyDevicesDto.serializer(),
                """
                {"devices":[
                  {"device_id":"ios-1","label":"iPhone","created_at":"2026-10-01T10:00:00Z","last_seen_at":"2026-10-09T08:30:00Z"},
                  {"device_id":"web-1","label":null,"created_at":"2026-09-20T10:00:00Z","last_seen_at":null},
                  {"label":"Ghost"}
                ]}
                """.trimIndent(),
            )
        val devices = dto.rows()
        assertEquals(listOf("ios-1", "web-1"), devices.map { it.deviceId })
        assertEquals("iPhone", devices[0].label)
        assertNotNull(devices[0].lastSeenAt)
        assertNull(devices[1].label)
        assertNull(devices[1].lastSeenAt)
        assertNotNull(devices[1].createdAt)
    }

    @Test
    fun aDevicesLabelPicksItsIcon() {
        val cases =
            listOf(
                "iPhone" to DeviceLabels.Icon.Phone,
                "iPad" to DeviceLabels.Icon.Tablet,
                "Chrome on Mac" to DeviceLabels.Icon.Laptop,
                "Safari on iPhone" to DeviceLabels.Icon.Phone,
                "Web browser" to DeviceLabels.Icon.Laptop,
                "Chrome on Android" to DeviceLabels.Icon.Phone,
                "Safari on iPad" to DeviceLabels.Icon.Tablet,
                "Pixel 9" to DeviceLabels.Icon.Phone,
            )
        cases.forEach { (label, icon) -> assertEquals(icon, DeviceLabels.icon(label), label) }
        assertEquals(DeviceLabels.Icon.Phone, DeviceLabels.icon(null))
    }

    @Test
    fun thisDeviceSortsFirstThenTheServersOrder() {
        val devices = listOf(MyDeviceDto("ios-1"), MyDeviceDto("web-1"), MyDeviceDto("android-1"), MyDeviceDto("ios-2"))
        assertEquals(listOf("android-1", "ios-1", "web-1", "ios-2"), sortDevicesForSettings(devices, "android-1").map { it.deviceId })
        assertEquals(listOf("ios-1", "web-1", "android-1", "ios-2"), sortDevicesForSettings(devices, null).map { it.deviceId })
    }

    private val now = Instant.parse("2026-10-09T10:30:00Z").toEpochMilliseconds()

    @Test
    fun rowNameAndActivityLine() {
        assertEquals("iPhone", deviceRowName("iPhone", isThisDevice = false, thisDeviceModel = "Pixel 9"))
        assertEquals("Pixel 9", deviceRowName(null, isThisDevice = true, thisDeviceModel = "Pixel 9"))
        assertEquals("Unnamed device", deviceRowName(null, isThisDevice = false, thisDeviceModel = "Pixel 9"))
        assertEquals("Unnamed device", deviceRowName(null, isThisDevice = true, thisDeviceModel = null))

        assertEquals("Active 2 hr ago", deviceActivityLine(MyDeviceDto("a", null, "2026-09-01T00:00:00Z", "2026-10-09T08:30:00Z"), now))
        assertEquals("Added yesterday", deviceActivityLine(MyDeviceDto("a", null, "2026-10-08T09:00:00Z", null), now))
        assertEquals("", deviceActivityLine(MyDeviceDto("a"), now))
    }

    @Test
    fun waitingForApprovalAndRemoveCopy() {
        assertEquals("Signed in 5 min ago", signedInLine("2026-10-09T10:25:00Z", now))
        assertEquals("Signed in recently", signedInLine(null, now))
        assertEquals("Chrome on Mac", DeviceLabels.withArticle("Chrome on Mac"))
        assertEquals("An iPhone", DeviceLabels.withArticle("iPhone"))
        assertEquals("Remove iPhone?", removeDeviceTitle("iPhone"))
        assertEquals("Remove this device?", removeDeviceTitle(null))
    }
}
