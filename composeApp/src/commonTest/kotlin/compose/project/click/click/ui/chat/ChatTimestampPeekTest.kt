package compose.project.click.click.ui.chat

import androidx.compose.runtime.mutableFloatStateOf
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatTimestampPeekTest {

    @Test
    fun isTimestampPeekRevealed_usesEpsilon() {
        assertFalse(isTimestampPeekRevealed(0f))
        assertFalse(isTimestampPeekRevealed(0.4f))
        assertTrue(isTimestampPeekRevealed(0.6f))
    }

    @Test
    fun applyTimestampPeekDragStep_rightwardDragCollapsesRevealedPeek() {
        val raw = mutableFloatStateOf(80f)
        val display = mutableFloatStateOf(40f)
        val maxReveal = 56f
        val softKnee = 5f
        applyTimestampPeekDragStep(
            rawLeftPx = raw,
            displayVisualPx = display,
            maxRevealPx = maxReveal,
            softKneePx = softKnee,
            dLeftPx = -20f,
        )
        assertTrue(raw.floatValue < 80f)
        assertTrue(display.floatValue < 40f)
    }
}
