package compose.project.click.click.calls // pragma: allowlist secret

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CallLayoutPolicyTest {

    private fun participant(
        id: String,
        name: String = id,
        isLocal: Boolean = false,
        isSpeaking: Boolean = false,
        hasVideo: Boolean = false,
        isMuted: Boolean = false,
    ) = CallParticipant(
        identity = id,
        displayName = name,
        isLocal = isLocal,
        isMuted = isMuted,
        isSpeaking = isSpeaking,
        cameraEnabled = hasVideo,
        hasVideo = hasVideo,
    )

    @Test
    fun defaultMode_speakerForThreeOrFewer() {
        assertEquals(CallLayoutMode.Speaker, CallLayoutPolicy.defaultMode(1))
        assertEquals(CallLayoutMode.Speaker, CallLayoutPolicy.defaultMode(2))
        assertEquals(CallLayoutMode.Speaker, CallLayoutPolicy.defaultMode(3))
    }

    @Test
    fun defaultMode_gridForFourOrMore() {
        assertEquals(CallLayoutMode.Grid, CallLayoutPolicy.defaultMode(4))
        assertEquals(CallLayoutMode.Grid, CallLayoutPolicy.defaultMode(8))
    }

    @Test
    fun resolveMode_honorsManualOverrideWithinSameSide() {
        assertEquals(
            CallLayoutMode.Grid,
            CallLayoutPolicy.resolveMode(
                participantCount = 2,
                manualOverride = CallLayoutMode.Grid,
                overrideAtCount = 2,
            ),
        )
        assertEquals(
            CallLayoutMode.Grid,
            CallLayoutPolicy.resolveMode(
                participantCount = 5,
                manualOverride = CallLayoutMode.Speaker,
                overrideAtCount = 5,
            ),
        )
        assertEquals(
            CallLayoutMode.Speaker,
            CallLayoutPolicy.resolveMode(
                participantCount = 4,
                manualOverride = CallLayoutMode.Speaker,
                overrideAtCount = 4,
            ),
        )
    }

    @Test
    fun resolveMode_forcesGridAboveSpeakerLayoutMax() {
        assertEquals(
            CallLayoutMode.Grid,
            CallLayoutPolicy.resolveMode(
                participantCount = 5,
                manualOverride = null,
            ),
        )
        assertEquals(
            CallLayoutMode.Grid,
            CallLayoutPolicy.resolveMode(
                participantCount = 8,
                manualOverride = CallLayoutMode.Speaker,
                overrideAtCount = 8,
            ),
        )
    }

    @Test
    fun resolveMode_clearsOverrideWhenThresholdCrossed() {
        assertEquals(
            CallLayoutMode.Grid,
            CallLayoutPolicy.resolveMode(
                participantCount = 4,
                manualOverride = CallLayoutMode.Speaker,
                overrideAtCount = 2,
            ),
        )
        assertEquals(
            CallLayoutMode.Speaker,
            CallLayoutPolicy.resolveMode(
                participantCount = 2,
                manualOverride = CallLayoutMode.Grid,
                overrideAtCount = 5,
            ),
        )
    }

    @Test
    fun pickActiveSpeaker_prefersSpeakingRemote() {
        val local = participant("me", isLocal = true, hasVideo = true)
        val quiet = participant("a", hasVideo = true)
        val talking = participant("b", isSpeaking = true)
        assertEquals(
            talking,
            CallLayoutPolicy.pickActiveSpeaker(listOf(local, quiet, talking)),
        )
    }

    @Test
    fun pickActiveSpeaker_fallsBackToRemoteWithVideoThenRemoteThenLocal() {
        val local = participant("me", isLocal = true, hasVideo = true)
        val remoteVideo = participant("a", hasVideo = true)
        assertEquals(remoteVideo, CallLayoutPolicy.pickActiveSpeaker(listOf(local, remoteVideo)))

        val remoteAudio = participant("b")
        assertEquals(remoteAudio, CallLayoutPolicy.pickActiveSpeaker(listOf(local, remoteAudio)))

        assertEquals(local, CallLayoutPolicy.pickActiveSpeaker(listOf(local)))
        assertNull(CallLayoutPolicy.pickActiveSpeaker(emptyList()))
    }

    @Test
    fun initialsFor_usesTwoLetters() {
        assertEquals("AS", CallLayoutPolicy.initialsFor("Alex Smith"))
        assertEquals("AL", CallLayoutPolicy.initialsFor("Alex"))
        assertEquals("?", CallLayoutPolicy.initialsFor("   "))
    }

    @Test
    fun selfLabel_and_formatDuration() {
        assertEquals("You (David)", CallLayoutPolicy.selfLabel("David"))
        assertEquals("00:00", CallLayoutPolicy.formatDuration(0))
        assertEquals("00:45", CallLayoutPolicy.formatDuration(45_000))
        assertEquals("12:46", CallLayoutPolicy.formatDuration(12 * 60_000L + 46_000L))
    }

    @Test
    fun gridRowSizes_faceTimeStyleRows() {
        assertEquals(emptyList<Int>(), CallLayoutPolicy.gridRowSizes(0))
        assertEquals(listOf(1), CallLayoutPolicy.gridRowSizes(1))
        assertEquals(listOf(2), CallLayoutPolicy.gridRowSizes(2))
        assertEquals(listOf(1, 2), CallLayoutPolicy.gridRowSizes(3))
        assertEquals(listOf(2, 2), CallLayoutPolicy.gridRowSizes(4))
        assertEquals(listOf(2, 3), CallLayoutPolicy.gridRowSizes(5))
        assertEquals(listOf(3, 3), CallLayoutPolicy.gridRowSizes(6))
        assertEquals(listOf(3, 2, 2), CallLayoutPolicy.gridRowSizes(7))
        assertEquals(listOf(3, 3, 2), CallLayoutPolicy.gridRowSizes(8))
    }
}
