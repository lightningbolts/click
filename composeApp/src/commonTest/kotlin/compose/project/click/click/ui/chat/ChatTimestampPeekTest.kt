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
    fun applyTimestampPeekDragStep_matchesReplySwipeRubberBand() {
        val maxReveal = 56f
        val softKnee = 5f
        val raw = mutableFloatStateOf(80f)
        val display = mutableFloatStateOf(
            swipeVisualFromRawTravel(
                rawTravelPx = raw.floatValue,
                isSent = false,
                maxVisualPx = maxReveal,
                softKneePx = softKnee,
                trackGain = 1f,
                overflowRubberGain = 0.12f,
            ),
        )
        val initialDisplay = display.floatValue
        applyTimestampPeekDragStep(
            rawLeftPx = raw,
            displayVisualPx = display,
            maxRevealPx = maxReveal,
            softKneePx = softKnee,
            dLeftPx = -20f,
        )
        assertTrue(raw.floatValue < 80f)
        assertTrue(display.floatValue < initialDisplay)
        // Past-cap resistance matches reply: overflow * 0.12
        val pastCap = swipeVisualFromRawTravel(
            rawTravelPx = 120f,
            isSent = false,
            maxVisualPx = maxReveal,
            softKneePx = softKnee,
            trackGain = 1f,
            overflowRubberGain = 0.12f,
        )
        assertTrue(pastCap > maxReveal)
        assertTrue(pastCap - maxReveal < 120f - maxReveal)
    }
}
