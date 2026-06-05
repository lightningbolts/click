package compose.project.click.click.ui.utils

import androidx.compose.runtime.Composable

@Composable
expect fun rememberCalendarPermissionRequester(): ((onComplete: () -> Unit) -> Unit)
