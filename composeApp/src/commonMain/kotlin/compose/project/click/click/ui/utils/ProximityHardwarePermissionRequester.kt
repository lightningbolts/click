package compose.project.click.click.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Whether Tap to Connect can proceed without presenting another native permission request.
 *
 * Bluetooth denial is intentionally non-blocking because the handshake can fall back to
 * ultrasonic + location. A blocking state therefore represents a missing microphone grant.
 */
enum class ProximityHardwarePermissionStatus {
    Ready,
    NeedsRequest,
    Blocked,
}

@Composable
expect fun rememberPlatformProximityHardwarePermissionStatus(): () -> ProximityHardwarePermissionStatus

/**
 * Requests the hardware permissions needed by the tri-factor proximity handshake.
 *
 * The callback receives false when microphone permission is denied. Bluetooth permission is requested
 * when relevant, but denial does not block the ultrasonic + GPS fallback path.
 */
@Composable
expect fun rememberPlatformProximityHardwarePermissionRequester(): ((onResult: (Boolean) -> Unit) -> Unit)

@Composable
fun rememberProximityHardwarePermissionRequester(): ((onResult: (Boolean) -> Unit) -> Unit) {
    val status = rememberPlatformProximityHardwarePermissionStatus()
    return remember(status) {
        { onResult ->
            when (status()) {
                ProximityHardwarePermissionStatus.Ready -> onResult(true)
                ProximityHardwarePermissionStatus.Blocked -> onResult(false)
                ProximityHardwarePermissionStatus.NeedsRequest ->
                    PermissionRequestQueue.enqueue(PermissionKind.ProximityHardware, onResult = onResult)
            }
        }
    }
}
