package compose.project.click.click.ui.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** click-ios 6a8310d: "Don't allow" ends there; only a denial with no prompt explains. */
class PermissionDenialFollowUpTest {
    private val shownPrompt = 2_500L
    private val noPrompt = 40L

    @Test
    fun firstDenialFromPromptEndsThere() {
        assertFalse(shouldExplainDeniedLocation(rationaleBefore = false, rationaleAfter = true, elapsedMs = shownPrompt))
    }

    @Test
    fun secondDenialFromPromptEndsThere() {
        // Denied once, prompt shown again, Don't allow: Android now says "don't ask again".
        assertFalse(shouldExplainDeniedLocation(rationaleBefore = true, rationaleAfter = false, elapsedMs = shownPrompt))
    }

    @Test
    fun dismissedPromptEndsThere() {
        // A dismissed prompt can leave both rationale flags false; the prompt still showed.
        assertFalse(shouldExplainDeniedLocation(rationaleBefore = false, rationaleAfter = false, elapsedMs = shownPrompt))
    }

    @Test
    fun denialWithoutPromptExplains() {
        // Off for good, including an install updated from a version that kept no record.
        assertTrue(shouldExplainDeniedLocation(rationaleBefore = false, rationaleAfter = false, elapsedMs = noPrompt))
    }

    @Test
    fun fastDenialWithRationaleEndsThere() {
        assertFalse(shouldExplainDeniedLocation(rationaleBefore = true, rationaleAfter = true, elapsedMs = noPrompt))
    }

    @Test
    fun boundaryCountsAsShownPrompt() {
        assertFalse(
            shouldExplainDeniedLocation(rationaleBefore = false, rationaleAfter = false, elapsedMs = NO_PROMPT_DENIAL_MAX_MS),
        )
    }
}
