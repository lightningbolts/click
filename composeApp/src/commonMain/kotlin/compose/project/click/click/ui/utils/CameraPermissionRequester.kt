package compose.project.click.click.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
expect fun rememberPlatformCameraPermissionRequester(): ((onComplete: () -> Unit) -> Unit)

@Composable
fun rememberCameraPermissionRequester(): ((onComplete: () -> Unit) -> Unit) =
    remember {
        { onComplete ->
            PermissionRequestQueue.enqueue(PermissionKind.Camera, onComplete = onComplete)
        }
    }
