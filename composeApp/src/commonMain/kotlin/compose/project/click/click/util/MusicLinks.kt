package compose.project.click.click.util // pragma: allowlist secret

/**
 * Opens a Spotify / Apple Music / web streaming link in the best native handler.
 * Returns true when a handler was invoked.
 *
 * The URL is validated against a streaming-domain allowlist before opening so that
 * server-provided URLs from other users cannot trigger arbitrary intents (e.g. `tel:`,
 * `sms:`, or phishing links).
 */
expect fun openMusicStreamingUrl(url: String): Boolean

/**
 * Returns `true` when [url] points to a recognised music-streaming host
 * (`spotify.com`, `music.apple.com`, `itunes.apple.com` and their subdomains).
 *
 * Shared between beacon insertion (client-side gate) and beacon playback
 * (server-provided URL gate) so the same allowlist is enforced at both ends.
 */
fun isValidStreamingUrl(url: String): Boolean {
    val lower = url.trim().lowercase()
    val schemeOk = lower.startsWith("http://") || lower.startsWith("https://")
    if (!schemeOk) return false
    // Extract host from URL to prevent domain spoofing via substring matching.
    // e.g. "https://evil.com/path?q=spotify.com" must NOT pass.
    val authority = lower.removePrefix("https://").removePrefix("http://")
        .substringBefore("/").substringBefore("?").substringBefore("#")
    val hostPart = authority.substringAfterLast("@").substringBefore(":")
    return hostPart == "spotify.com" || hostPart.endsWith(".spotify.com") ||
        hostPart == "music.apple.com" || hostPart.endsWith(".music.apple.com") ||
        hostPart == "itunes.apple.com" || hostPart.endsWith(".itunes.apple.com")
}
