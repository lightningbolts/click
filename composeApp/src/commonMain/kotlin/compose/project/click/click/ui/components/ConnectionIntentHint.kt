package compose.project.click.click.ui.components // pragma: allowlist secret

/**
 * Server-driven framing for the connection context sheet (thin client: no TTL math).
 */
sealed class ConnectionIntentHint {
    data class SparkNew(val peerShortName: String) : ConnectionIntentHint()

    data class LogExistingEncounter(val peerShortName: String) : ConnectionIntentHint()
}

fun ConnectionIntentHint.peerShortName(): String = when (this) {
    is ConnectionIntentHint.SparkNew -> peerShortName
    is ConnectionIntentHint.LogExistingEncounter -> peerShortName
}

/** In-app QR scan flows skip the full-screen reveal between context and network work. */
fun shouldBypassConnectionRevealOverlay(
    useQrScanFlow: Boolean,
    intentHint: ConnectionIntentHint?,
): Boolean {
    if (useQrScanFlow) return true
    if (intentHint is ConnectionIntentHint.LogExistingEncounter) return true
    return false
}

fun shouldBypassConnectionRevealForProximity(isNewConnectionAggregate: Boolean): Boolean =
    !isNewConnectionAggregate
