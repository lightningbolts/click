package compose.project.click.click.ui.utils

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType

@Composable
actual fun rememberPlatformCameraPermissionRequester(): ((onComplete: () -> Unit) -> Unit) =
    { onComplete ->
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> onComplete()
            AVAuthorizationStatusDenied,
            AVAuthorizationStatusRestricted,
            -> {
                openApplicationSystemSettings()
                onComplete()
            }
            else -> {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ ->
                    CoroutineScope(Dispatchers.Main).launch {
                        onComplete()
                    }
                }
            }
        }
    }
