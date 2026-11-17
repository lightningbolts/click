package compose.project.click.click.ui.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** click-ios 6a8310d: "Don't allow" ends there; only a later request, already off, explains. */
class PermissionDenialFollowUpTest {
    @Test
    fun firstDenialFromPromptEndsThere() {
        // Never asked; the user saw the prompt and chose Don't allow.
        assertFalse(shouldExplainDeniedLocation(askedBefore = false, rationaleBefore = false, rationaleAfter = true))
    }

    @Test
    fun secondDenialFromPromptEndsThere() {
        // Denied once, prompt shown again, Don't allow: Android now says "don't ask again".
        assertFalse(shouldExplainDeniedLocation(askedBefore = true, rationaleBefore = true, rationaleAfter = false))
    }

    @Test
    fun laterRequestWhileOffForGoodExplains() {
        // The OS shows no prompt and denies at once.
        assertTrue(shouldExplainDeniedLocation(askedBefore = true, rationaleBefore = false, rationaleAfter = false))
    }

    @Test
    fun dismissedPromptThenDenialEndsThere() {
        assertFalse(shouldExplainDeniedLocation(askedBefore = true, rationaleBefore = false, rationaleAfter = true))
    }
}
