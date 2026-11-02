package compose.project.click.click.platform

/**
 * Turns the current screen to full brightness (and keeps it on) while a code is shown for scanning,
 * then restores the user's setting. Like iOS when showing a QR or Click Pass.
 */
expect fun setCodeDisplayBrightness(enabled: Boolean)
