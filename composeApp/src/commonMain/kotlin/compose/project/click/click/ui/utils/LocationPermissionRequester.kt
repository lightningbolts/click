package compose.project.click.click.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Platform-specific way to request location permission and run a block when done.
 * Used so the OS permission dialog is only shown after the in-app explainer;
 * the caller shows the explainer, then calls the returned function with the "continue" block.
 */
@Composable
expect fun rememberPlatformLocationPermissionRequester(): ((onComplete: () -> Unit) -> Unit)

@Composable
fun rememberLocationPermissionRequester(): ((onComplete: () -> Unit) -> Unit) =
    remember {
        { onComplete ->
            PermissionRequestQueue.enqueue(PermissionKind.Location, onComplete = onComplete)
        }
    }
