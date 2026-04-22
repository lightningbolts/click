package compose.project.click.click.proximity

import kotlinx.coroutines.delay

/**
 * Deterministic tri-factor stand-in for iOS Simulator / Android emulator when
 * [compose.project.click.click.ui.utils.AppSystemSettings.isDebugMode] is enabled.
 *
 * [startHandshakeListening] waits three seconds then returns two synthetic BLE token strings
 * (UUID form) so [bind-proximity-connection] can be exercised without radios.
 */
class MockProximityManager : ProximityManager {

    override suspend fun startHandshakeBroadcast(ephemeralToken: String) {
        // No hardware: broadcast is a no-op for the mock path.
    }

    override suspend fun startHandshakeListening(): List<String> {
        delay(3_000L)
        return listOf(
            "00000000-0000-4000-8000-000000001234",
            "00000000-0000-4000-8000-000000005678",
        )
    }

    override fun stopAll() {}

    override fun supportsTapExchange(): Boolean = true

    override fun capabilityNote(): String =
        "Simulator / emulator mock: returns fixed heard tokens after 3s (no BLE or ultrasound)."

    override fun openRadiosSettings() {}
}
