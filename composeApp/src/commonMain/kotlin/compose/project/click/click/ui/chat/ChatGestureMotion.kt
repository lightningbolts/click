package compose.project.click.click.ui.chat

/**
 * Shared horizontal-gesture limits.
 */
internal object ChatGestureMotion {
    /** Finger travel accumulator cap (px) — higher allows faster peak motion without changing gain. */
    const val RawTravelCapPx = 720f
}
