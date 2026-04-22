package compose.project.click.click.ui.utils

/**
 * Opens the host OS screen for this app’s settings (deep link / intent).
 * Used when location or microphone access is blocked and the system will not show the permission dialog again.
 */
expect fun openApplicationSystemSettings()
