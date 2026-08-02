package compose.project.click.click.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HubMessageCooldownParseTest {
    @Test
    fun parseHubCooldownSeconds_readsColonSuffix() {
        val e = Exception("HUB_MESSAGE_COOLDOWN:12")
        assertEquals(12, HubChatViewModel.parseHubCooldownSecondsForTest(e))
    }

    @Test
    fun parseHubCooldownSeconds_defaultsWhenMissingNumber() {
        val e = Exception("HUB_MESSAGE_COOLDOWN")
        assertEquals(HUB_MESSAGE_COOLDOWN_SECONDS, HubChatViewModel.parseHubCooldownSecondsForTest(e))
    }

    @Test
    fun parseHubCooldownSeconds_nullWhenUnrelated() {
        assertNull(HubChatViewModel.parseHubCooldownSecondsForTest(Exception("OUT_OF_BOUNDS")))
    }
}
