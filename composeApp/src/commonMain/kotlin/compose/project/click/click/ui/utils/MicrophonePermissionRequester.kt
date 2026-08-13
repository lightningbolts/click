package compose.project.click.click.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
expect fun rememberPlatformMicrophonePermissionRequester(): ((onComplete: () -> Unit) -> Unit)

@Composable
fun rememberMicrophonePermissionRequester(): ((onComplete: () -> Unit) -> Unit) =
    remember {
        { onComplete ->
            PermissionRequestQueue.enqueue(PermissionKind.Microphone, onComplete = onComplete)
        }
    }
