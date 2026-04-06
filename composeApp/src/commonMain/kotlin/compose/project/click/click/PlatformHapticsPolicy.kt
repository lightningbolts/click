package compose.project.click.click

/**
 * When true, Compose should use a no-op [androidx.compose.ui.platform.LocalHapticFeedback]
 * (e.g. iOS Simulator lacks Core Haptics pattern data and logs CHHapticPattern noise).
 */
expect fun shouldUseNoOpComposeHaptics(): Boolean
