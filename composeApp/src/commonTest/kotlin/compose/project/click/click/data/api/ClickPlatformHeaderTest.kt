package compose.project.click.click.data.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClickPlatformHeaderTest {
    @Test
    fun headerNameAndValue() {
        assertEquals("X-Click-Platform", CLICK_PLATFORM_HEADER)
        assertEquals("android", CLICK_PLATFORM_ANDROID)
    }

    @Test
    fun addedOnlyToClickWebRequests() {
        assertTrue(shouldAddClickPlatformHeader("click.example", "click.example", alreadyPresent = false))
        assertTrue(shouldAddClickPlatformHeader("CLICK.example", "click.example", alreadyPresent = false))
        assertFalse(shouldAddClickPlatformHeader("abc.supabase.co", "click.example", alreadyPresent = false))
        assertFalse(shouldAddClickPlatformHeader("api.click.example", "click.example", alreadyPresent = false))
        assertFalse(shouldAddClickPlatformHeader("", "", alreadyPresent = false))
    }

    @Test
    fun neverDuplicated() {
        assertFalse(shouldAddClickPlatformHeader("click.example", "click.example", alreadyPresent = true))
    }
}
