package compose.project.click.click.ui.chat

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
 * Maps finger travel → bubble translation. Active drag is 1:1 until the visual cap, then uses a
 * small rubber-band overflow. Paired with [swipeRawTravelFromVisual] for picking up mid-settle.
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

    val rubber = overflowRubberGain.coerceAtLeast(0.001f)

    val magnitude = when {
        directed <= cap -> directed
        else -> cap + (directed - cap) * rubber
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

    val rubber = overflowRubberGain.coerceAtLeast(0.001f)

    val directedRaw = when {
        v <= cap -> v
        else -> cap + (v - cap) / rubber
    }
    return if (isSent) -directedRaw else directedRaw
}
