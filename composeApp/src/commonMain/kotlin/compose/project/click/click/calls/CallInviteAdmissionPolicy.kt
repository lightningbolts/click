package compose.project.click.click.calls

/**
 * Pure rules for admitting an incoming [CallInvite] while another overlay/call may be active.
 * Prevents FCM + Realtime dual delivery of the same [CallInvite.callId] from sending a false
 * "busy" response, and allows a new ring to replace a leftover [CallOverlayState.Ended] card.
 */
object CallInviteAdmissionPolicy {
    enum class Decision {
        /** Admit this invite as a new Incoming ring. */
        Admit,

        /** Same call is already ringing / connecting — ignore duplicate delivery. */
        SameCall,

        /** A different call session is in progress — reply busy. */
        Busy,
    }

    fun decide(
        invite: CallInvite,
        activeInvite: CallInvite?,
        overlayState: CallOverlayState,
        callState: CallState,
    ): Decision {
        if (activeInvite?.callId == invite.callId) return Decision.SameCall
        when (overlayState) {
            is CallOverlayState.Incoming ->
                if (overlayState.invite.callId == invite.callId) return Decision.SameCall
            is CallOverlayState.Connecting ->
                if (overlayState.invite.callId == invite.callId) return Decision.SameCall
            is CallOverlayState.Outgoing ->
                if (overlayState.invite.callId == invite.callId) return Decision.SameCall
            is CallOverlayState.Ended,
            CallOverlayState.Idle,
            -> Unit
        }

        if (callState is CallState.Connecting || callState is CallState.Connected) {
            return Decision.Busy
        }

        return when (overlayState) {
            is CallOverlayState.Outgoing,
            is CallOverlayState.Incoming,
            is CallOverlayState.Connecting,
            -> Decision.Busy

            is CallOverlayState.Ended,
            CallOverlayState.Idle,
            -> Decision.Admit
        }
    }
}
