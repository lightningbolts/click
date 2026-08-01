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
     *
     * Video surfaces live only under the Active layer — mount it as soon as media joins
     * (Connecting/Connected) so TextureView/VideoView init is not delayed until overlay → Idle.
     */
    fun presentationFor(
        overlayState: CallOverlayState,
        callState: CallState,
        suppressEndedPreviewAfterActiveCall: Boolean,
    ): Presentation {
        // Incoming ring always owns the Accept/Decline card.
        if (overlayState is CallOverlayState.Incoming) return Presentation.Preview

        // Outgoing ring keeps the ring card until the peer is actually in-room.
        if (overlayState is CallOverlayState.Outgoing) {
            val connected = callState as? CallState.Connected
            return if (connected?.hasRemoteParticipant == true) {
                Presentation.Active
            } else {
                Presentation.Preview
            }
        }

        // Accept/join path: mount Active as soon as LiveKit is Connecting/Connected so remote
        // video can attach without waiting for the overlay to clear to Idle.
        if (overlayState is CallOverlayState.Connecting) {
            return if (
                callState is CallState.Connecting ||
                callState is CallState.Connected
            ) {
                Presentation.Active
            } else {
                Presentation.Preview
            }
        }

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
