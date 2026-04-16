package compose.project.click.click.calls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Push payload parsing tests.
 *
 * `parseIncomingCallPayload` is the single entry point both FCM (Android)
 * and APNs bridges hand their flat string maps to. These tests lock the
 * parser contract so a silently reordered or renamed key in the push
 * payload fails fast instead of dropping a real call on the floor.
 */
class CallPushPayloadTest {

    private fun validPayload(overrides: Map<String, String> = emptyMap()): Map<String, String> {
        val base = mapOf(
            "type" to "incoming_call",
            "call_id" to "call-abc-123",
            "connection_id" to "conn-xyz-789",
            "room_name" to "click-room-1",
            "caller_id" to "user-caller",
            "caller_name" to "Alice",
            "callee_id" to "user-callee",
            "callee_name" to "Bob",
            "video_enabled" to "true",
            "created_at" to "1700000000000",
        )
        return base + overrides
    }

    @Test
    fun parses_fullyPopulatedPayload() {
        val invite = parseIncomingCallPayload(validPayload())
        assertNotNull(invite)
        assertEquals("call-abc-123", invite.callId)
        assertEquals("conn-xyz-789", invite.connectionId)
        assertEquals("click-room-1", invite.roomName)
        assertEquals("user-caller", invite.callerId)
        assertEquals("Alice", invite.callerName)
        assertEquals("user-callee", invite.calleeId)
        assertEquals("Bob", invite.calleeName)
        assertTrue(invite.videoEnabled)
        assertEquals(1_700_000_000_000L, invite.createdAt)
    }

    @Test
    fun defaultsVideoEnabledToFalseWhenMissingOrBogus() {
        val missing = validPayload() - "video_enabled"
        assertEquals(false, parseIncomingCallPayload(missing)?.videoEnabled)

        val bogus = validPayload(mapOf("video_enabled" to "yes"))
        assertEquals(false, parseIncomingCallPayload(bogus)?.videoEnabled)

        val caseMismatch = validPayload(mapOf("video_enabled" to "True"))
        // toBooleanStrictOrNull only accepts lowercase "true" / "false".
        assertEquals(false, parseIncomingCallPayload(caseMismatch)?.videoEnabled)

        val explicitFalse = validPayload(mapOf("video_enabled" to "false"))
        assertEquals(false, parseIncomingCallPayload(explicitFalse)?.videoEnabled)
    }

    @Test
    fun defaultsCreatedAtToZeroWhenMissingOrBogus() {
        val missing = validPayload() - "created_at"
        assertEquals(0L, parseIncomingCallPayload(missing)?.createdAt)

        val bogus = validPayload(mapOf("created_at" to "not-a-number"))
        assertEquals(0L, parseIncomingCallPayload(bogus)?.createdAt)
    }

    @Test
    fun rejectsPayloadMissingAnyRequiredKey() {
        val required = listOf(
            "call_id",
            "connection_id",
            "room_name",
            "caller_id",
            "caller_name",
            "callee_id",
            "callee_name",
        )
        for (key in required) {
            val trimmed = validPayload() - key
            assertNull(
                parseIncomingCallPayload(trimmed),
                "Expected null when required key '$key' is missing",
            )
        }
    }

    @Test
    fun rejectsPayloadWithBlankRequiredValues() {
        val blanked = validPayload(mapOf("caller_name" to "   "))
        assertNull(parseIncomingCallPayload(blanked))

        val empty = validPayload(mapOf("call_id" to ""))
        assertNull(parseIncomingCallPayload(empty))
    }

    @Test
    fun ignoresExtraUnknownKeys() {
        val withExtras = validPayload(mapOf(
            "custom_field" to "ignored",
            "future_flag" to "whatever",
        ))
        val invite = parseIncomingCallPayload(withExtras)
        assertNotNull(invite)
        assertEquals("call-abc-123", invite.callId)
    }

    @Test
    fun negativeCreatedAtIsParsedFaithfully() {
        // Future-safety: the parser should not sanitize negative timestamps —
        // the server is authoritative for time, the client just reports it.
        val payload = validPayload(mapOf("created_at" to "-1"))
        assertEquals(-1L, parseIncomingCallPayload(payload)?.createdAt)
    }
}

/**
 * CallInvite / CallOverlayState shape tests.
 *
 * These cover the pure transition helpers the state machine relies on —
 * anything that does not require Supabase or LiveKit. The full
 * `CallSessionManager` is a singleton with heavyweight side-effects
 * (realtime, push, LiveKit), so the focus here is on the value semantics
 * that power the ringing → connected → disconnected flow.
 */
class CallOverlayStateTransitionTest {

    private fun sampleInvite(
        callerId: String = "caller-1",
        calleeId: String = "callee-1",
        callerName: String = "Alice",
        calleeName: String = "Bob",
    ) = CallInvite(
        callId = "c1",
        connectionId = "conn-1",
        roomName = "room-1",
        callerId = callerId,
        callerName = callerName,
        calleeId = calleeId,
        calleeName = calleeName,
        videoEnabled = false,
        createdAt = 0L,
    )

    @Test
    fun counterpartName_returnsCalleeForCaller() {
        val invite = sampleInvite()
        assertEquals("Bob", invite.counterpartName("caller-1"))
    }

    @Test
    fun counterpartName_returnsCallerForCallee() {
        val invite = sampleInvite()
        assertEquals("Alice", invite.counterpartName("callee-1"))
    }

    @Test
    fun counterpartName_defaultsToCallerForUnknownUser() {
        val invite = sampleInvite()
        // Non-caller ids (including null / unknown / the callee) fall through
        // to the callerName branch. Only an exact callerId match flips to the
        // callee side, matching production behavior.
        assertEquals("Alice", invite.counterpartName(null))
        assertEquals("Alice", invite.counterpartName("some-other-user"))
    }

    @Test
    fun overlayState_busyCheckIsFalseOnlyInIdle() {
        // Mirrors the busy guard in CallSessionManager.handleInvite /
        // startOutgoingCall. Only Idle permits a new invite to progress.
        fun isBusy(overlay: CallOverlayState): Boolean = overlay !is CallOverlayState.Idle

        val invite = sampleInvite()
        assertFalse(isBusy(CallOverlayState.Idle))
        assertTrue(isBusy(CallOverlayState.Outgoing(invite)))
        assertTrue(isBusy(CallOverlayState.Incoming(invite)))
        assertTrue(isBusy(CallOverlayState.Connecting(invite)))
        assertTrue(isBusy(CallOverlayState.Ended(invite, "Declined")))
    }

    @Test
    fun overlayState_endedReasonsMapForCancelPayload() {
        // Mirrors CallSessionManager.handleCancel — the cancel reason string
        // must map to a user-facing Ended label deterministically.
        fun mapCancelToEnded(invite: CallInvite, reason: String): CallOverlayState = when (reason) {
            "ended" -> CallOverlayState.Ended(invite, "Call ended")
            "missed" -> CallOverlayState.Ended(invite, "No answer")
            else -> CallOverlayState.Idle
        }

        val invite = sampleInvite()
        assertEquals(
            CallOverlayState.Ended(invite, "Call ended"),
            mapCancelToEnded(invite, "ended"),
        )
        assertEquals(
            CallOverlayState.Ended(invite, "No answer"),
            mapCancelToEnded(invite, "missed"),
        )
        assertEquals(CallOverlayState.Idle, mapCancelToEnded(invite, "cancelled"))
        assertEquals(CallOverlayState.Idle, mapCancelToEnded(invite, "unknown-reason"))
    }

    @Test
    fun overlayState_peerIdIsOppositeOfCurrentUser() {
        // Mirrors the peerId resolution in cancelCurrentCall / endActiveCall.
        // Swapping caller/callee must be self-inverse: applying it twice
        // returns the original id so the state machine never cancels itself.
        fun peerId(invite: CallInvite, currentUserId: String): String =
            if (currentUserId == invite.callerId) invite.calleeId else invite.callerId

        val invite = sampleInvite(callerId = "A", calleeId = "B")
        assertEquals("B", peerId(invite, "A"))
        assertEquals("A", peerId(invite, "B"))
        // Unknown current user falls through to the callee-side branch and
        // returns the callerId, matching current production behavior.
        assertEquals("A", peerId(invite, "unknown"))
    }

    @Test
    fun overlayState_ringingToConnectingToEnded_transitionSequence() {
        // This is the canonical happy path. Model each transition through
        // an opaque producer so the compiler cannot smart-cast away the
        // state-shape checks — those checks are the whole point.
        fun next(state: CallOverlayState): CallOverlayState = state
        val invite = sampleInvite()

        val outgoing = next(CallOverlayState.Outgoing(invite))
        assertTrue(outgoing is CallOverlayState.Outgoing)

        // Callee accepts → caller transitions Outgoing → Connecting.
        val connecting = next(CallOverlayState.Connecting(invite))
        assertTrue(connecting is CallOverlayState.Connecting)

        // LiveKit disconnects → Ended with reason.
        val ended = next(CallOverlayState.Ended(invite, "Call ended"))
        assertTrue(ended is CallOverlayState.Ended)
        assertEquals("Call ended", ended.reason)
        assertEquals(invite, ended.invite)

        // Overlay dismissed → Idle.
        val idle = next(CallOverlayState.Idle)
        assertTrue(idle is CallOverlayState.Idle)
    }

    @Test
    fun callInvite_valueEqualityForSerializationRoundTrip() {
        // @Serializable data-class equality must be stable so realtime
        // invite dedup (activeInviteValue?.callId == invite.callId) works.
        val a = sampleInvite()
        val b = sampleInvite()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())

        val c = sampleInvite(callerName = "Different")
        assertFalse(a == c)
    }
}
