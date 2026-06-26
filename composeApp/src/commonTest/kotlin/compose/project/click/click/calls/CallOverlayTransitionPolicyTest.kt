package compose.project.click.click.calls

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallOverlayTransitionPolicyTest {

    private fun sampleInvite() = CallInvite(
        callId = "c1",
        connectionId = "conn-1",
        roomName = "room-1",
        callerId = "caller-1",
        callerName = "Alice",
        calleeId = "callee-1",
        calleeName = "Bob",
        videoEnabled = false,
        createdAt = 0L,
    )

    @Test
    fun shouldPresentCallEndedOverlay_falseOnColdBootIdleTail() {
        assertFalse(
            CallOverlayTransitionPolicy.shouldPresentCallEndedOverlay(
                previousCallState = CallState.Idle,
                overlayState = CallOverlayState.Idle,
            ),
        )
    }

    @Test
    fun shouldPresentCallEndedOverlay_trueAfterConnectedSession() {
        assertTrue(
            CallOverlayTransitionPolicy.shouldPresentCallEndedOverlay(
                previousCallState = CallState.Connected(
                    videoRequested = false,
                    microphoneEnabled = true,
                    speakerEnabled = false,
                    cameraEnabled = false,
                    remoteVideoAvailable = false,
                    localVideoAvailable = false,
                ),
                overlayState = CallOverlayState.Idle,
            ),
        )
    }

    @Test
    fun shouldPresentCallEndedOverlay_trueDuringOutgoingRing() {
        val invite = sampleInvite()
        assertTrue(
            CallOverlayTransitionPolicy.shouldPresentCallEndedOverlay(
                previousCallState = CallState.Idle,
                overlayState = CallOverlayState.Outgoing(invite),
            ),
        )
    }

    @Test
    fun shouldPresentCallEndedOverlay_trueDuringConnectingPreview() {
        val invite = sampleInvite()
        assertTrue(
            CallOverlayTransitionPolicy.shouldPresentCallEndedOverlay(
                previousCallState = CallState.Connecting(videoRequested = false),
                overlayState = CallOverlayState.Connecting(invite),
            ),
        )
    }
}
