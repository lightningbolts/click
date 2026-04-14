package compose.project.click.click

import androidx.compose.runtime.Composable

/**
 * App-level haptics outside Compose [androidx.compose.ui.platform.LocalHapticFeedback].
 * Used for gesture thresholds, proximity success, and consistent long-press feedback.
 */
expect object PlatformHapticsPolicy {
    fun lightImpact()
    fun heavyImpact()
    fun successNotification()
}

/**
 * Binds the Android haptic host [android.view.View] from composition; no-op on iOS.
 * Call once near the root of [App].
 */
@Composable
expect fun BindPlatformHapticsToViewHierarchy()

/**
 * When true, Compose should use a no-op [androidx.compose.ui.platform.LocalHapticFeedback].
 *
 * On iOS, `MainViewController` always installs UIKit `UIFeedbackGenerator`-based haptics instead of
 * the default Compose bridge (which relied on Core Haptics patterns). This flag remains for
 * parity; it is currently always false on all targets.
 */
expect fun shouldUseNoOpComposeHaptics(): Boolean
