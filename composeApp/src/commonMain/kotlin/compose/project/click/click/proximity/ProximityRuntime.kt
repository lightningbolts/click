package compose.project.click.click.proximity

/** True when running on the iOS Simulator or an Android emulator / generic AVD image. */
expect fun isSimulatorOrEmulatorRuntime(): Boolean

/**
 * Tri-factor listen window output — audio ([heardTokens]) and BLE ([detectedDevices]) are kept
 * separate so the client can abort before queueing or calling the API when both are empty.
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

const val PROXIMITY_NO_NEARBY_DEVICES_MESSAGE: String = "No nearby devices detected."

/** @return null when the handshake should proceed; otherwise the user-facing abort message. */
fun proximityHandshakeAbortMessage(evidence: ProximityHandshakeListenResult): String? =
    if (evidence.hasNearbyPeerEvidence()) null else PROXIMITY_NO_NEARBY_DEVICES_MESSAGE
