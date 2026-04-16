package compose.project.click.click.ui.chat

import kotlin.math.sqrt

/**
 * Pure swipe-to-reply gesture math.
 *
 * Extracted from ConnectionsScreen.kt so the rubber-band curve and its
 * inverse are unit-testable in isolation. These functions must stay
 * mathematical inverses of each other — see `ChatSwipeMathTest`.
 */

/** 0..1 — how close the swipe is to triggering reply (for hint UI). */
internal fun replyDragHintProgress(rawTravelPx: Float, isSent: Boolean, thresholdPx: Float): Float {
    if (thresholdPx <= 0f) return 0f
    val directed = if (isSent) (-rawTravelPx).coerceAtLeast(0f) else rawTravelPx.coerceAtLeast(0f)
    return (directed / thresholdPx).coerceIn(0f, 1f)
}

/**
 * Maps finger travel → bubble translation: **quadratic** for the first pixels (zero slope at 0 so
 * nothing "snaps"), then **linear** to the cap, then rubber. Paired with [swipeRawTravelFromVisual]
 * for picking up mid-settle.
 */
internal fun swipeVisualFromRawTravel(
    rawTravelPx: Float,
    isSent: Boolean,
    maxVisualPx: Float,
    softKneePx: Float,
    trackGain: Float,
    overflowRubberGain: Float,
): Float {
    val cap = maxVisualPx.coerceAtLeast(1f)
    val directed = if (isSent) (-rawTravelPx).coerceAtLeast(0f) else rawTravelPx.coerceAtLeast(0f)
    if (directed <= 0f) return 0f

    val d1 = softKneePx.coerceAtLeast(1f)
    val gain = trackGain.coerceIn(0.01f, 1f)
    val k = gain / (2f * d1)
    val v1 = k * d1 * d1
    val rubber = overflowRubberGain.coerceAtLeast(0.001f)
    val dReach = d1 + (cap - v1) / gain

    val magnitude = when {
        directed <= d1 -> k * directed * directed
        directed <= dReach -> v1 + gain * (directed - d1)
        else -> cap + (directed - dReach) * rubber
    }
    return if (isSent) -magnitude else magnitude
}

/** Inverse of [swipeVisualFromRawTravel]. */
internal fun swipeRawTravelFromVisual(
    visualPx: Float,
    isSent: Boolean,
    maxVisualPx: Float,
    softKneePx: Float,
    trackGain: Float,
    overflowRubberGain: Float,
): Float {
    val cap = maxVisualPx.coerceAtLeast(1f)
    val v = if (isSent) (-visualPx).coerceAtLeast(0f) else visualPx.coerceAtLeast(0f)
    if (v <= 0f) return 0f

    val d1 = softKneePx.coerceAtLeast(1f)
    val gain = trackGain.coerceIn(0.01f, 1f)
    val k = gain / (2f * d1)
    val v1 = k * d1 * d1
    val rubber = overflowRubberGain.coerceAtLeast(0.001f)
    val dReach = d1 + (cap - v1) / gain

    val directedRaw = when {
        v <= v1 -> sqrt((v / k).coerceAtLeast(0f))
        v <= cap -> d1 + (v - v1) / gain
        else -> dReach + (v - cap) / rubber
    }
    return if (isSent) -directedRaw else directedRaw
}
