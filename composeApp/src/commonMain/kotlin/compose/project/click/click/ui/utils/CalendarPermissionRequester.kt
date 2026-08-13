package compose.project.click.click.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
expect fun rememberPlatformCalendarPermissionRequester(): ((onComplete: () -> Unit) -> Unit)

@Composable
fun rememberCalendarPermissionRequester(): ((onComplete: () -> Unit) -> Unit) =
    remember {
        { onComplete ->
            PermissionRequestQueue.enqueue(PermissionKind.Calendar, onComplete = onComplete)
        }
    }
