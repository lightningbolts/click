package compose.project.click.click.chat

import compose.project.click.click.data.api.DeviceApproverDto
import compose.project.click.click.data.api.DeviceHistoryRequestDto
import compose.project.click.click.platform.DEVICE_LABEL_MAX_LENGTH
import compose.project.click.click.platform.friendlyDeviceLabel
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Label rules from iOS `DeviceApprovalViews.swift` (#66) and click-web `lib/chat/deviceLabel.ts`. */
class DeviceApprovalLabelsTest {
    @Test
    fun browserTestIsExactWebBrowserOrContainsOn() {
        assertTrue(DeviceLabels.isBrowser("Web browser"))
        assertTrue(DeviceLabels.isBrowser("Chrome on Mac"))
        assertTrue(DeviceLabels.isBrowser("Safari on iPhone"))
        assertFalse(DeviceLabels.isBrowser("iPhone"))
        assertFalse(DeviceLabels.isBrowser("Pixel 9"))
        assertFalse(DeviceLabels.isBrowser(null))
        // iOS compares "Web browser" exactly.
        assertFalse(DeviceLabels.isBrowser("web browser"))
    }

    @Test
    fun iconIsLaptopForBrowsersExceptPhoneBrowsers() {
        assertEquals(DeviceLabels.Icon.Laptop, DeviceLabels.icon("Chrome on Mac"))
        assertEquals(DeviceLabels.Icon.Laptop, DeviceLabels.icon("Web browser"))
        assertEquals(DeviceLabels.Icon.Phone, DeviceLabels.icon("Safari on iPhone"))
        assertEquals(DeviceLabels.Icon.Phone, DeviceLabels.icon("Chrome on Android"))
        assertEquals(DeviceLabels.Icon.Phone, DeviceLabels.icon("iPhone"))
        assertEquals(DeviceLabels.Icon.Phone, DeviceLabels.icon("Pixel 9"))
        assertEquals(DeviceLabels.Icon.Phone, DeviceLabels.icon(null))
        assertEquals(DeviceLabels.Icon.Tablet, DeviceLabels.icon("iPad"))
    }

    @Test
    fun articleRules() {
        assertEquals("An iPhone", DeviceLabels.withArticle("iPhone"))
        assertEquals("An iPad", DeviceLabels.withArticle("iPad"))
        assertEquals("A Pixel 9", DeviceLabels.withArticle("Pixel 9"))
        assertEquals("A web browser", DeviceLabels.withArticle("Web browser"))
        assertEquals("Chrome on Mac", DeviceLabels.withArticle("Chrome on Mac"))
        assertEquals("A new device", DeviceLabels.withArticle(null))
        assertEquals("A new device", DeviceLabels.withArticle(""))
    }

    @Test
    fun approvedAndDeniedCopy() {
        assertEquals(
            "Your past messages will appear in Chrome on your Mac in a moment.",
            DeviceLabels.approvedDetail("Chrome on Mac"),
        )
        assertEquals("Your past messages will appear on your browser in a moment.", DeviceLabels.approvedDetail("Web browser"))
        assertEquals("Your past messages will appear on your iPhone in a moment.", DeviceLabels.approvedDetail("iPhone"))
        assertEquals("Your past messages will appear on your device in a moment.", DeviceLabels.approvedDetail(null))
        assertEquals(
            "That browser can't read your past messages. If you didn't sign in, change your password in Settings.",
            DeviceLabels.deniedDetail("Chrome on Mac"),
        )
        assertEquals(
            "That Pixel 9 can't read your past messages. If you didn't sign in, change your password in Settings.",
            DeviceLabels.deniedDetail("Pixel 9"),
        )
        assertEquals(
            "An iPhone signed in to your Click account 2 min ago. Approve it to let it read your past messages.",
            DeviceLabels.askingDetail("iPhone", "2 min ago"),
        )
    }

    @Test
    fun friendlyAndroidLabels() {
        assertEquals("Pixel 9", friendlyDeviceLabel("Google", "Pixel 9"))
        assertEquals("Galaxy S24", friendlyDeviceLabel("samsung", "SM-S921U"))
        assertEquals("Galaxy S24 Ultra", friendlyDeviceLabel("samsung", "SM-S928B"))
        assertEquals("Galaxy phone", friendlyDeviceLabel("samsung", "SM-X000"))
        assertEquals("OnePlus CPH2449", friendlyDeviceLabel("OnePlus", "CPH2449"))
        assertEquals("motorola edge 2024", friendlyDeviceLabel("motorola", "motorola edge 2024"))
        assertEquals("Android phone", friendlyDeviceLabel(null, null))
        assertEquals("Samsung phone", friendlyDeviceLabel("samsung", " "))
        assertTrue(friendlyDeviceLabel("Acme", "X".repeat(100)).length <= DEVICE_LABEL_MAX_LENGTH)
    }

    private val now = Instant.parse("2026-10-06T12:00:00Z").toEpochMilliseconds()

    @Test
    fun approverLines() {
        assertEquals(
            "Approve on your Pixel 9 (active 2 min ago)",
            approverLine(DeviceApproverDto("Pixel 9", "2026-10-06T11:58:00+00:00"), now),
        )
        assertEquals(
            "Approve in Chrome on your Mac (active just now)",
            approverLine(DeviceApproverDto("Chrome on Mac", "2026-10-06T11:59:40Z"), now),
        )
        assertEquals("Approve on your other device", approverLine(DeviceApproverDto(null, null), now))
    }

    private fun own(
        status: String = "pending",
        emailSent: Boolean = false,
        expired: Boolean = false,
    ) = DeviceHistoryRequestDto(id = "r1", status = status, emailSent = emailSent, expired = expired)

    @Test
    fun bannerStates() {
        val approvers = listOf(DeviceApproverDto("iPhone", "2026-10-06T11:58:00Z"))
        assertNull(deviceHistoryBannerState(null, approvers, false, "Pixel 9", now))
        assertNull(deviceHistoryBannerState(own("approved"), approvers, false, "Pixel 9", now))

        val waiting = deviceHistoryBannerState(own(), approvers, false, "Pixel 9", now)!!
        assertEquals("Unlock older messages", waiting.title)
        assertEquals("Approve this Pixel 9 in Click on a device you already use.", waiting.subtitle)
        assertEquals(listOf("Approve on your iPhone (active 2 min ago)"), waiting.approverLines)
        assertEquals(DeviceHistoryBannerState.Action.EmailLink, waiting.action)

        val noApprovers = deviceHistoryBannerState(own(), emptyList(), false, "Pixel 9", now)!!
        assertEquals("Approve this Pixel 9 with a link we email you.", noApprovers.subtitle)
        assertEquals(DeviceHistoryBannerState.Action.EmailLink, noApprovers.action)

        val emailed = deviceHistoryBannerState(own(), approvers, true, "Pixel 9", now)!!
        assertNull(emailed.action)
        assertEquals(emailed, deviceHistoryBannerState(own(emailSent = true), approvers, false, "Pixel 9", now))

        val denied = deviceHistoryBannerState(own("denied"), approvers, false, "Pixel 9", now)!!
        assertEquals("Older messages stay locked", denied.title)
        assertEquals(DeviceHistoryBannerState.Action.AskAgain, denied.action)
        assertTrue(denied.approverLines.isEmpty())

        val expired = deviceHistoryBannerState(own(expired = true), approvers, false, "Pixel 9", now)!!
        assertEquals("The approval request expired.", expired.subtitle)
        assertEquals(DeviceHistoryBannerState.Action.AskAgain, expired.action)
    }
}
