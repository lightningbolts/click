package compose.project.click.click.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Sheet 10 §A4: one notification per request, replaced by a re-send, never merged with another request. */
class DeviceApprovalNotificationsTest {
    @Test
    fun tagIsPerRequest() {
        assertEquals(
            "device_approval:4f1c2a3b-5d6e-4f70-8a9b-0c1d2e3f4a5b",
            deviceApprovalNotificationTag("4f1c2a3b-5d6e-4f70-8a9b-0c1d2e3f4a5b"),
        )
        assertNotEquals(deviceApprovalNotificationTag("a"), deviceApprovalNotificationTag("b"))
    }
}
