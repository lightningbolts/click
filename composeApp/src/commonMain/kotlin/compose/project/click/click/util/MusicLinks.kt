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
    val authority =
        lower
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringBefore("?")
            .substringBefore("#")
    val hostPart = authority.substringAfterLast("@").substringBefore(":")
    return hostPart == "spotify.com" ||
        hostPart.endsWith(".spotify.com") ||
        hostPart == "music.apple.com" ||
        hostPart.endsWith(".music.apple.com") ||
        hostPart == "itunes.apple.com" ||
        hostPart.endsWith(".itunes.apple.com") ||
        hostPart == "www.youtube.com" ||
        hostPart == "youtube.com" ||
        hostPart == "m.youtube.com" ||
        hostPart == "youtu.be" ||
        hostPart == "music.youtube.com" ||
        hostPart.endsWith(".music.youtube.com")
}

/**
 * URLs allowed when opening a full-track deep link from a soundtrack beacon
 * (https streaming hosts plus `spotify:` deep links).
 */
fun isBeaconOriginalSongDeepLinkUrl(url: String): Boolean {
    val t = url.trim()
    if (t.isEmpty()) return false
    if (t.startsWith("spotify:", ignoreCase = true)) {
        val p = t.lowercase()
        return p.startsWith("spotify:track:") ||
            p.startsWith("spotify:album:") ||
            p.startsWith("spotify:playlist:") ||
            p.startsWith("spotify:episode:")
    }
    return isValidStreamingUrl(t)
}

/**
 * Exactly the server's share allowlist (click-web `isAllowedMusicShareUrl`): https links to Spotify,
 * Apple Music or YouTube. The create/edit forms check this before posting so the server never has
 * to reject the link; playback keeps the broader [isValidStreamingUrl].
 */
fun isAllowedMusicShareUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (!trimmed.startsWith("https://", ignoreCase = true)) return false
    val authority =
        trimmed
            .substring("https://".length)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
    if ('@' in authority) return false
    val host = authority.substringBefore(':').lowercase()

    fun hostOrSub(domain: String) = host == domain || host.endsWith(".$domain")
    return hostOrSub("open.spotify.com") ||
        hostOrSub("spotify.link") ||
        hostOrSub("music.apple.com") ||
        hostOrSub("itunes.apple.com") ||
        host == "www.youtube.com" ||
        host == "youtube.com" ||
        host == "youtu.be" ||
        hostOrSub("music.youtube.com")
}
