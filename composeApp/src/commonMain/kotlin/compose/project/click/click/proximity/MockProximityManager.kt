package compose.project.click.click.proximity

import kotlinx.coroutines.delay

/**
 * Deterministic tri-factor stand-in for iOS Simulator / Android emulator when
 * [compose.project.click.click.ui.utils.AppSystemSettings.isDebugMode] is enabled.
 *
 * [startHandshakeListening] waits through the debounce window then returns two synthetic
 * token strings so [bind-proximity-connection] can be exercised without radios.
 */
class MockProximityManager : ProximityManager {

    override suspend fun startHandshakeBroadcast(ephemeralToken: String) {
        // No hardware: broadcast is a no-op for the mock path.
    }

    override suspend fun startHandshakeListening(): List<String> {
        delay(4_000L)
        return listOf(
            "1234",
            "5678",
        )
    }

    override fun stopAll() {}

    override fun supportsTapExchange(): Boolean = true

    override fun capabilityNote(): String =
        "Simulator / emulator mock: returns fixed heard tokens after 3s (no BLE or ultrasound)."

    override fun openRadiosSettings() {}
}
