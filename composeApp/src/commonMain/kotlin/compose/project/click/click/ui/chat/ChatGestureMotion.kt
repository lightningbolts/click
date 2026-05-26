package compose.project.click.click.ui.chat

/**
 * Shared horizontal-gesture limits. [trackGain] / friction values control sensitivity;
 * these caps and settle durations control peak speed only.
 */
internal object ChatGestureMotion {
    /** Finger travel accumulator cap (px) — higher allows faster peak motion without changing gain. */
    const val RawTravelCapPx = 720f

    /** Release settle for swipe-to-reply and timestamp peek (ms). */
    const val HorizontalSwipeSettleMillis = 260
}
