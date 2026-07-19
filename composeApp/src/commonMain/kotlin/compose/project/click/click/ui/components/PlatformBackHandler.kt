package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable

/**
 * Cross-platform back handler.
 * On Android: consumes predictive-back progress and commits only when the gesture completes.
 * On iOS: bridges the existing native edge-swipe completion notification.
 */
@Composable
expect fun PlatformBackHandler(
    enabled: Boolean = true,
    onBackProgress: (Float) -> Unit = {},
    onBackCancelled: () -> Unit = {},
    onBack: () -> Unit,
)
