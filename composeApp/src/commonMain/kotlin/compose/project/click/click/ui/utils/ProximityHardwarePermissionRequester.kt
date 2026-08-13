package compose.project.click.click.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Requests the hardware permissions needed by the tri-factor proximity handshake.
 *
 * The callback receives false when microphone permission is denied. Bluetooth permission is requested
 * when relevant, but denial does not block the ultrasonic + GPS fallback path.
 */
@Composable
expect fun rememberPlatformProximityHardwarePermissionRequester(): ((onResult: (Boolean) -> Unit) -> Unit)

@Composable
fun rememberProximityHardwarePermissionRequester(): ((onResult: (Boolean) -> Unit) -> Unit) =
    remember {
        { onResult ->
            PermissionRequestQueue.enqueue(PermissionKind.ProximityHardware, onResult = onResult)
        }
    }
