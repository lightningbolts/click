package compose.project.click.click.data.api // pragma: allowlist secret

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActivityRecapDtoTest {
    @Test
    fun allZero_whenEveryStatIsZero() {
        val recap =
            ActivityRecapDto(
                window = "week",
                since = "2026-01-01T00:00:00Z",
            )
        assertTrue(recap.isAllZero())
        assertEquals(0, recap.peakValue())
    }

    @Test
    fun peakValue_isHighestNonZeroStat() {
        val recap =
            ActivityRecapDto(
                window = "week",
                since = "2026-01-01T00:00:00Z",
                connectionsFormed = 1,
                messagesSent = 12,
                messagesReceived = 4,
            )
        assertFalse(recap.isAllZero())
        assertEquals(12, recap.peakValue())
    }
}
