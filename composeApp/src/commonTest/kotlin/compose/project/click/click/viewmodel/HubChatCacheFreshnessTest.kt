package compose.project.click.click.viewmodel

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HubChatCacheFreshnessTest {
    @Test
    fun warmCacheExpiresAfterFiveMinutes() {
        val now = 1_000_000L

        assertTrue(isHubThreadWarmCacheFresh(now - 5 * 60 * 1000L, now))
        assertFalse(isHubThreadWarmCacheFresh(now - 5 * 60 * 1000L - 1L, now))
        assertFalse(isHubThreadWarmCacheFresh(0L, now))
        assertFalse(isHubThreadWarmCacheFresh(now + 1L, now))
    }
}
