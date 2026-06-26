package compose.project.click.click.calls

/**
 * Pure rules for when the ringing / in-call preview overlay may enter [CallOverlayState.Ended].
 * Keeps boot-time [CallState.Ended] tails from flashing "Call ended" when no session was active.
 */
object CallOverlayTransitionPolicy {
    fun wasInActiveCallSession(previousCallState: CallState): Boolean =
        previousCallState is CallState.Connecting || previousCallState is CallState.Connected

    fun wasInActiveCallOverlay(overlayState: CallOverlayState): Boolean =
        overlayState is CallOverlayState.Outgoing ||
            overlayState is CallOverlayState.Incoming ||
            overlayState is CallOverlayState.Connecting

    /**
     * @return true when [CallState.Ended] should surface the post-call preview card.
     */
    fun shouldPresentCallEndedOverlay(
        previousCallState: CallState,
        overlayState: CallOverlayState,
    ): Boolean = wasInActiveCallSession(previousCallState) || wasInActiveCallOverlay(overlayState)
}
