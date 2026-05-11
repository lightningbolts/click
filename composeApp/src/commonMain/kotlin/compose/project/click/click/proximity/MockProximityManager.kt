package compose.project.click.click.proximity

import kotlinx.coroutines.delay

/**
 * Deterministic tri-factor stand-in for iOS Simulator / Android emulator.
 *
 * [startHandshakeListening] waits briefly then returns a synthetic token so
 * [bind-proximity-connection] can be exercised without radios.
 */
class MockProximityManager : ProximityManager {

    override suspend fun startHandshakeBroadcast(ephemeralToken: String) {
        // No hardware: broadcast is a no-op for the mock path.
    }

    override suspend fun startHandshakeListening(): List<String> {
        delay(2_000L)
        return listOf(
            "5678",
        )
    }

    override fun stopAll() {}

    override fun supportsTapExchange(): Boolean = true

    override fun capabilityNote(): String =
        "Simulator / emulator mock: returns a fixed heard token after 2s (no BLE or ultrasound)."

    override fun openRadiosSettings() {}
}
