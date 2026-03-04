package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable

/**
 * iOS actual: no-op.
 * iOS handles back navigation via the native swipe-back gesture.
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op on iOS — the system provides swipe-back natively
}
