package compose.project.click.click.util // pragma: allowlist secret

/**
 * Opens a Spotify / Apple Music / web streaming link in the best native handler.
 * Returns true when a handler was invoked.
 */
expect fun openMusicStreamingUrl(url: String): Boolean
