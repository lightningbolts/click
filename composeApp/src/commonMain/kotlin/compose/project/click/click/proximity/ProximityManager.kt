package compose.project.click.click.proximity

import androidx.compose.runtime.Composable

/**
 * Tri-factor proximity handshake: BLE service/GATT token reads + ultrasonic audio token encoding.
 *
 * [startHandshakeBroadcast] begins BLE advertising and plays the audio envelope (18.5 kHz chirp + digit tones).
 * [startHandshakeListening] listens through the proximity debounce window and returns audio
 * ([ProximityHandshakeListenResult.heardTokens]) and BLE ([ProximityHandshakeListenResult.detectedDevices])
 * evidence separately.
 */
interface ProximityManager {
    suspend fun startHandshakeBroadcast(ephemeralToken: String)

    suspend fun startHandshakeListening(): ProximityHandshakeListenResult

    fun stopAll()

    /** Whether phone-to-phone style broadcast + listen is expected to work on this OS build. */
    fun supportsTapExchange(): Boolean

    /** Short human-readable capability line for the UI. */
    fun capabilityNote(): String

    /** Open OS settings relevant to Bluetooth / audio (platform-specific). */
    fun openRadiosSettings()
}

class ProximityHardwarePermissionException(
    message: String = "Missing proximity hardware permission",
) : Exception(message)

@Composable
expect fun rememberProximityManager(): ProximityManager
