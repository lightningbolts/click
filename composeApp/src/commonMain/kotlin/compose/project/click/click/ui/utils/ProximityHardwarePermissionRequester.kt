package compose.project.click.click.ui.utils

import androidx.compose.runtime.Composable

/**
 * Requests the hardware permissions needed by the tri-factor proximity handshake.
 *
 * The callback receives false when Bluetooth or microphone permission is denied; callers must
 * stop locally instead of sending an empty hardware result to the Edge Function.
 */
@Composable
expect fun rememberProximityHardwarePermissionRequester(): ((onResult: (Boolean) -> Unit) -> Unit)
