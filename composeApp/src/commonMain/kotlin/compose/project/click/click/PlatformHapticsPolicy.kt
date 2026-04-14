package compose.project.click.click

/**
 * When true, Compose should use a no-op [androidx.compose.ui.platform.LocalHapticFeedback].
 *
 * On iOS, `MainViewController` always installs UIKit `UIFeedbackGenerator`-based haptics instead of
 * the default Compose bridge (which relied on Core Haptics patterns). This flag remains for
 * parity; it is currently always false on all targets.
 */
expect fun shouldUseNoOpComposeHaptics(): Boolean
