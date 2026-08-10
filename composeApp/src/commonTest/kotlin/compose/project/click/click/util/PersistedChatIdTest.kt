package compose.project.click.click.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersistedChatIdTest {
    @Test
    fun acceptsCanonicalUuid() {
        assertTrue(isPersistedApiChatId("11111111-1111-4111-8111-111111111111"))
        assertTrue(isPersistedApiUuid("11111111-1111-4111-8111-111111111111"))
    }

    @Test
    fun rejectsOptimisticTempIds() {
        assertFalse(isPersistedApiChatId("temp-1750000000000-123456789"))
        assertFalse(isPersistedApiChatId("temp_3756033350280 - 580928034532165216"))
        assertFalse(isPersistedApiUuid("temp-1786375216459--9040327941310846566"))
        assertFalse(isPersistedApiChatId("optimistic:abc"))
        assertFalse(isPersistedApiChatId(""))
        assertFalse(isPersistedApiChatId(null))
    }
}
