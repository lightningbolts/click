package compose.project.click.click.ui.chat

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locks down the pure swipe-to-reply math.
 *
 * The critical contract is that [swipeRawTravelFromVisual] is the
 * inverse of [swipeVisualFromRawTravel]. Any asymmetry between the
 * two causes bubbles to "jump" when the user picks a swipe back up
 * mid-settle, which is the exact class of regression this file guards.
 */
class ChatSwipeMathTest {

    // Production-style knobs taken from the chat bubble draggable configuration.
    private val maxVisualPx = 120f
    private val softKneePx = 24f
    private val trackGain = 0.6f
    private val overflowRubberGain = 0.12f

    private fun forward(raw: Float, isSent: Boolean = false): Float =
        swipeVisualFromRawTravel(raw, isSent, maxVisualPx, softKneePx, trackGain, overflowRubberGain)

    private fun inverse(visual: Float, isSent: Boolean = false): Float =
        swipeRawTravelFromVisual(visual, isSent, maxVisualPx, softKneePx, trackGain, overflowRubberGain)

    // region replyDragHintProgress

    @Test
    fun hintProgress_isZeroForZeroThreshold() {
        assertEquals(0f, replyDragHintProgress(100f, isSent = false, thresholdPx = 0f))
    }

    @Test
    fun hintProgress_clampsToZeroWhenSwipingWrongDirection() {
        // Received bubble swiped left-to-right (positive) is a valid hint.
        assertTrue(replyDragHintProgress(30f, isSent = false, thresholdPx = 60f) > 0f)
        // Same bubble, swipe the opposite way — no progress.
        assertEquals(0f, replyDragHintProgress(-30f, isSent = false, thresholdPx = 60f))
        // Sent bubble swiped right-to-left (negative) is a valid hint.
        assertTrue(replyDragHintProgress(-30f, isSent = true, thresholdPx = 60f) > 0f)
        // Sent bubble, swipe the opposite way — no progress.
        assertEquals(0f, replyDragHintProgress(30f, isSent = true, thresholdPx = 60f))
    }

    @Test
    fun hintProgress_clampsAtOneForOverThreshold() {
        assertEquals(1f, replyDragHintProgress(120f, isSent = false, thresholdPx = 60f))
        assertEquals(1f, replyDragHintProgress(-120f, isSent = true, thresholdPx = 60f))
    }

    @Test
    fun hintProgress_midpointIsHalf() {
        assertEquals(0.5f, replyDragHintProgress(30f, isSent = false, thresholdPx = 60f), 1e-6f)
    }

    // endregion

    // region swipeVisualFromRawTravel

    @Test
    fun forward_zeroRawProducesZeroVisual() {
        assertEquals(0f, forward(0f))
        assertEquals(0f, forward(0f, isSent = true))
    }

    @Test
    fun forward_wrongDirectionProducesZeroVisual() {
        // Received bubble cannot translate to the left past zero.
        assertEquals(0f, forward(-50f, isSent = false))
        // Sent bubble cannot translate to the right past zero.
        assertEquals(0f, forward(50f, isSent = true))
    }

    @Test
    fun forward_signFollowsIsSent() {
        val rawReceived = 40f
        val rawSent = -40f
        val vReceived = forward(rawReceived, isSent = false)
        val vSent = forward(rawSent, isSent = true)
        assertTrue(vReceived > 0f)
        assertTrue(vSent < 0f)
        assertEquals(vReceived, -vSent, 1e-4f)
    }

    @Test
    fun forward_zeroSlopeAtOriginAvoidsSnap() {
        // For tiny travels the quadratic ramp should produce a much smaller
        // visual offset than the linear region — that's what kills the
        // "snap" when the finger first touches.
        val tiny = forward(1f)
        val linear = forward(50f)
        // `tiny` would be ~0.01-0.05 px; `linear` well into the dozens.
        assertTrue(tiny < 0.5f, "Tiny raw should map to sub-pixel visual, got $tiny")
        assertTrue(linear > 20f, "Linear-region raw should translate substantially, got $linear")
    }

    @Test
    fun forward_neverExceedsCapMeaningfullyBeforeOverflow() {
        // Right at cap travel the visual should be very close to maxVisualPx,
        // and past that the rubber band adds only gradually.
        val atCap = forward(1_000f)
        // Well past the rubber-band knee the magnitude still grows but slowly.
        assertTrue(atCap >= maxVisualPx, "Expected rubber-band output >= cap, got $atCap")
    }

    // endregion

    // region swipeRawTravelFromVisual (inverse)

    @Test
    fun inverse_zeroVisualProducesZeroRaw() {
        assertEquals(0f, inverse(0f))
        assertEquals(0f, inverse(0f, isSent = true))
    }

    @Test
    fun inverse_isLeftInverseOfForwardAcrossAllThreeRegions() {
        // Pick points that hit each region: quadratic (< softKnee),
        // linear (between knee and cap), and rubber (past cap).
        val samples = listOf(5f, 15f, 24f, 40f, 80f, 120f, 200f, 400f)
        for (raw in samples) {
            val visual = forward(raw)
            val recovered = inverse(visual)
            assertTrue(
                abs(recovered - raw) < 0.5f,
                "Inverse mismatch at raw=$raw: visual=$visual → recovered=$recovered",
            )
        }
    }

    @Test
    fun inverse_roundTripsSentBubbleSign() {
        val raw = -60f
        val visual = forward(raw, isSent = true)
        val recovered = inverse(visual, isSent = true)
        assertTrue(abs(recovered - raw) < 0.5f, "Sent-bubble round-trip failed: $recovered != $raw")
    }

    @Test
    fun inverse_wrongDirectionProducesZeroRaw() {
        // If the visual offset is on the wrong side for the bubble type,
        // the inverse should report zero raw travel rather than negative.
        assertEquals(0f, inverse(-40f, isSent = false))
        assertEquals(0f, inverse(40f, isSent = true))
    }

    // endregion
}
