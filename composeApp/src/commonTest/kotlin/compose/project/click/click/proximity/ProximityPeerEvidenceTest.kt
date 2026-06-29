package compose.project.click.click.proximity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProximityPeerEvidenceTest {

    @Test
    fun hasNearbyPeerEvidence_trueWhenAudioOnly() {
        val evidence = ProximityHandshakeListenResult(heardTokens = listOf("1234"), detectedDevices = emptyList())
        assertTrue(evidence.hasNearbyPeerEvidence())
        assertNull(proximityHandshakeAbortMessage(evidence))
    }

    @Test
    fun hasNearbyPeerEvidence_trueWhenBleOnly() {
        val evidence = ProximityHandshakeListenResult(heardTokens = emptyList(), detectedDevices = listOf("5678"))
        assertTrue(evidence.hasNearbyPeerEvidence())
        assertNull(proximityHandshakeAbortMessage(evidence))
    }

    @Test
    fun hasNearbyPeerEvidence_falseWhenBothEmpty() {
        val evidence = ProximityHandshakeListenResult()
        assertFalse(evidence.hasNearbyPeerEvidence())
        assertNull(proximityHandshakeAbortMessage(evidence))
    }

    @Test
    fun allPeerTokens_mergesDistinctSorted() {
        val evidence = ProximityHandshakeListenResult(
            heardTokens = listOf("9012", "1234"),
            detectedDevices = listOf("1234", "5678"),
        )
        assertEquals(listOf("1234", "5678", "9012"), evidence.allPeerTokens)
    }

    @Test
    fun proximityBindLocationWaitMs_shortForStrongPeerEvidence() {
        val evidence = ProximityHandshakeListenResult(heardTokens = listOf("1234"))

        assertEquals(PROXIMITY_STRONG_EVIDENCE_LOCATION_WAIT_MS, proximityBindLocationWaitMs(evidence))
    }

    @Test
    fun proximityBindLocationWaitMs_longerForServerFallbackEvidence() {
        val evidence = ProximityHandshakeListenResult()

        assertEquals(PROXIMITY_EMPTY_EVIDENCE_LOCATION_WAIT_MS, proximityBindLocationWaitMs(evidence))
    }
}
