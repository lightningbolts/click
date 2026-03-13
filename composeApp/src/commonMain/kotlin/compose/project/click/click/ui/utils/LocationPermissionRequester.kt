package compose.project.click.click.ui.utils

import androidx.compose.runtime.Composable

/**
 * Platform-specific way to request location permission and run a block when done.
 * Used so the OS permission dialog is only shown after the in-app explainer;
 * the caller shows the explainer, then calls the returned function with the "continue" block.
 */
@Composable
expect fun rememberLocationPermissionRequester(): ((onComplete: () -> Unit) -> Unit)
