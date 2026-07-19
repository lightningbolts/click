package compose.project.click.click.calls

import kotlin.test.Test
import kotlin.test.assertEquals

class CallInviteAdmissionPolicyTest {

    private fun invite(callId: String = "c1") = CallInvite(
        callId = callId,
        connectionId = "conn-1",
        roomName = "room-1",
        callerId = "caller-1",
        callerName = "Alice",
        calleeId = "callee-1",
        calleeName = "Bob",
        videoEnabled = false,
        createdAt = 0L,
    )

    private val connected = CallState.Connected(
        videoRequested = false,
        microphoneEnabled = true,
        speakerEnabled = false,
        cameraEnabled = false,
        remoteVideoAvailable = false,
        localVideoAvailable = false,
    )

    @Test
    fun sameCallIdViaActiveInvite_isSameCallNotBusy() {
        val inv = invite("c1")
        assertEquals(
            CallInviteAdmissionPolicy.Decision.SameCall,
            CallInviteAdmissionPolicy.decide(
                invite = inv,
                activeInvite = inv,
                overlayState = CallOverlayState.Incoming(inv),
                callState = CallState.Idle,
            ),
        )
    }

    @Test
    fun sameCallIdViaIncomingOverlay_isSameCallEvenIfActiveCleared() {
        val inv = invite("c1")
        assertEquals(
            CallInviteAdmissionPolicy.Decision.SameCall,
            CallInviteAdmissionPolicy.decide(
                invite = inv,
                activeInvite = null,
                overlayState = CallOverlayState.Incoming(inv),
                callState = CallState.Idle,
            ),
        )
    }

    @Test
    fun endedOverlay_isReplaceableByNewInvite() {
        val old = invite("old")
        val next = invite("new")
        assertEquals(
            CallInviteAdmissionPolicy.Decision.Admit,
            CallInviteAdmissionPolicy.decide(
                invite = next,
                activeInvite = old,
                overlayState = CallOverlayState.Ended(old, "Call ended"),
                callState = CallState.Ended("Call ended"),
            ),
        )
    }

    @Test
    fun idleOverlay_admitsNewInvite() {
        assertEquals(
            CallInviteAdmissionPolicy.Decision.Admit,
            CallInviteAdmissionPolicy.decide(
                invite = invite(),
                activeInvite = null,
                overlayState = CallOverlayState.Idle,
                callState = CallState.Idle,
            ),
        )
    }

    @Test
    fun differentIncoming_isBusy() {
        val ringing = invite("a")
        val other = invite("b")
        assertEquals(
            CallInviteAdmissionPolicy.Decision.Busy,
            CallInviteAdmissionPolicy.decide(
                invite = other,
                activeInvite = ringing,
                overlayState = CallOverlayState.Incoming(ringing),
                callState = CallState.Idle,
            ),
        )
    }

    @Test
    fun outgoingDifferentCall_isBusy() {
        val out = invite("out")
        assertEquals(
            CallInviteAdmissionPolicy.Decision.Busy,
            CallInviteAdmissionPolicy.decide(
                invite = invite("in"),
                activeInvite = out,
                overlayState = CallOverlayState.Outgoing(out),
                callState = CallState.Idle,
            ),
        )
    }

    @Test
    fun connectingMedia_isBusyEvenIfOverlayIdle() {
        assertEquals(
            CallInviteAdmissionPolicy.Decision.Busy,
            CallInviteAdmissionPolicy.decide(
                invite = invite(),
                activeInvite = null,
                overlayState = CallOverlayState.Idle,
                callState = CallState.Connecting(videoRequested = false),
            ),
        )
    }

    @Test
    fun connectedMedia_isBusy() {
        assertEquals(
            CallInviteAdmissionPolicy.Decision.Busy,
            CallInviteAdmissionPolicy.decide(
                invite = invite(),
                activeInvite = invite("live"),
                overlayState = CallOverlayState.Idle,
                callState = connected,
            ),
        )
    }

    @Test
    fun sameCallWhileConnecting_isSameCallNotBusy() {
        val inv = invite("c1")
        assertEquals(
            CallInviteAdmissionPolicy.Decision.SameCall,
            CallInviteAdmissionPolicy.decide(
                invite = inv,
                activeInvite = inv,
                overlayState = CallOverlayState.Connecting(inv),
                callState = CallState.Connecting(videoRequested = false),
            ),
        )
    }
}
