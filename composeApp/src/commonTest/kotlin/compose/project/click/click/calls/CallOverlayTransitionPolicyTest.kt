package compose.project.click.click.calls

import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun presentationFor_handsPreviewToActiveWithoutEmptyOwner() {
        val invite = sampleInvite()
        assertEquals(
            CallOverlayTransitionPolicy.Presentation.Preview,
            CallOverlayTransitionPolicy.presentationFor(
                overlayState = CallOverlayState.Connecting(invite),
                callState = CallState.Connecting(videoRequested = false),
                suppressEndedPreviewAfterActiveCall = false,
            ),
        )
        assertEquals(
            CallOverlayTransitionPolicy.Presentation.Active,
            CallOverlayTransitionPolicy.presentationFor(
                overlayState = CallOverlayState.Idle,
                callState = CallState.Connected(
                    videoRequested = false,
                    microphoneEnabled = true,
                    speakerEnabled = false,
                    cameraEnabled = false,
                    remoteVideoAvailable = false,
                    localVideoAvailable = false,
                ),
                suppressEndedPreviewAfterActiveCall = false,
            ),
        )
    }

    @Test
    fun presentationFor_keepsEndedTailOnActiveLayer() {
        val invite = sampleInvite()
        assertEquals(
            CallOverlayTransitionPolicy.Presentation.Active,
            CallOverlayTransitionPolicy.presentationFor(
                overlayState = CallOverlayState.Ended(invite, "Call ended"),
                callState = CallState.Ended("Call ended"),
                suppressEndedPreviewAfterActiveCall = true,
            ),
        )
        assertEquals(
            CallOverlayTransitionPolicy.Presentation.None,
            CallOverlayTransitionPolicy.presentationFor(
                overlayState = CallOverlayState.Ended(invite, "Call ended"),
                callState = CallState.Idle,
                suppressEndedPreviewAfterActiveCall = true,
            ),
        )
    }

    @Test
    fun presentationFor_allowsEndedPreviewWhenCallNeverBecameActive() {
        assertEquals(
            CallOverlayTransitionPolicy.Presentation.Preview,
            CallOverlayTransitionPolicy.presentationFor(
                overlayState = CallOverlayState.Ended(sampleInvite(), "No answer"),
                callState = CallState.Idle,
                suppressEndedPreviewAfterActiveCall = false,
            ),
        )
    }

    @Test
    fun presentationFor_doesNotShowColdBootEndedTail() {
        assertEquals(
            CallOverlayTransitionPolicy.Presentation.None,
            CallOverlayTransitionPolicy.presentationFor(
                overlayState = CallOverlayState.Idle,
                callState = CallState.Ended("stale"),
                suppressEndedPreviewAfterActiveCall = false,
            ),
        )
    }
}
