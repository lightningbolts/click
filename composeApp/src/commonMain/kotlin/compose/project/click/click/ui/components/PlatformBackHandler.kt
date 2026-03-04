package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable

/**
 * Cross-platform back handler.
 * On Android: delegates to androidx.activity.compose.BackHandler.
 * On iOS: no-op (system swipe-back gesture is handled natively).
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
