package compose.project.click.click.calls

/**
 * Pure rules for when the ringing / in-call preview overlay may enter [CallOverlayState.Ended].
 * Keeps boot-time [CallState.Ended] tails from flashing "Call ended" when no session was active.
 */
object CallOverlayTransitionPolicy {
    enum class Presentation {
        None,
        Preview,
        Active,
    }

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

    /**
     * Chooses exactly one visual owner during preview → active → ended hand-off.
     * An ended preview is suppressed after the active layer has owned the session, preventing
     * the preview card from flashing while the active card performs its exit.
     */
    fun presentationFor(
        overlayState: CallOverlayState,
        callState: CallState,
        suppressEndedPreviewAfterActiveCall: Boolean,
    ): Presentation {
        val previewOnly = overlayState is CallOverlayState.Outgoing ||
            overlayState is CallOverlayState.Incoming ||
            overlayState is CallOverlayState.Connecting
        if (previewOnly) return Presentation.Preview

        if (
            callState is CallState.Connected ||
            (callState is CallState.Ended && suppressEndedPreviewAfterActiveCall)
        ) {
            return Presentation.Active
        }

        if (
            overlayState is CallOverlayState.Ended &&
            !suppressEndedPreviewAfterActiveCall
        ) {
            return Presentation.Preview
        }

        return Presentation.None
    }
}
