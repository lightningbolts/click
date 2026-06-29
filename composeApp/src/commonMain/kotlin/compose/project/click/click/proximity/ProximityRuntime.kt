package compose.project.click.click.proximity

/** True when running on the iOS Simulator or an Android emulator / generic AVD image. */
expect fun isSimulatorOrEmulatorRuntime(): Boolean

/**
 * Tri-factor listen window output — audio ([heardTokens]) and BLE ([detectedDevices]) are kept
 * separate so the server can distinguish strong radio evidence from same-time/same-place fallback.
 */
data class ProximityHandshakeListenResult(
    val heardTokens: List<String> = emptyList(),
    val detectedDevices: List<String> = emptyList(),
) {
    val allPeerTokens: List<String>
        get() = (heardTokens + detectedDevices).distinct().sorted()
}

fun ProximityHandshakeListenResult.hasNearbyPeerEvidence(): Boolean =
    heardTokens.isNotEmpty() || detectedDevices.isNotEmpty()

internal const val PROXIMITY_SENSOR_LOCATION_WAIT_MS: Long = 1_200L
internal const val PROXIMITY_STRONG_EVIDENCE_LOCATION_WAIT_MS: Long = 250L
internal const val PROXIMITY_EMPTY_EVIDENCE_LOCATION_WAIT_MS: Long = 1_800L
internal const val PROXIMITY_SENSOR_WAIT_MS: Long = 800L

internal fun proximityBindLocationWaitMs(evidence: ProximityHandshakeListenResult): Long =
    if (evidence.hasNearbyPeerEvidence()) {
        PROXIMITY_STRONG_EVIDENCE_LOCATION_WAIT_MS
    } else {
        PROXIMITY_EMPTY_EVIDENCE_LOCATION_WAIT_MS
    }

const val PROXIMITY_NO_NEARBY_DEVICES_MESSAGE: String = "No nearby devices detected."

/** @return null when the handshake should proceed; tap attempts are always sent to the API. */
fun proximityHandshakeAbortMessage(evidence: ProximityHandshakeListenResult): String? =
    null
