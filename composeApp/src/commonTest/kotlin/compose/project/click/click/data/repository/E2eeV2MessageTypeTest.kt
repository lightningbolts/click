package compose.project.click.click.data.repository

import compose.project.click.click.data.models.Message
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class E2eeV2MessageTypeTest {
    @Test
    fun legacyCallLogsAndBeaconsKeepTheirCompatibilityPath() {
        assertTrue(
            shouldBypassLegacyMessageDecrypt(
                Message(
                    id = "legacy-call",
                    user_id = "user-a",
                    content = "call metadata",
                    timeCreated = 1L,
                    messageType = "call_log",
                ),
            ),
        )
    }

    @Test
    fun v2CallLogsAndBeaconsUseAuthenticatedDecryption() {
        assertFalse(
            shouldBypassLegacyMessageDecrypt(
                Message(
                    id = "v2-call",
                    user_id = "user-a",
                    content = "e2e2:AA==",
                    timeCreated = 1L,
                    messageType = "call_log",
                ),
            ),
        )
    }
}
