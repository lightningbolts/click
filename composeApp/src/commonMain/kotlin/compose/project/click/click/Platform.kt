package compose.project.click.click

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/**
 * Opens Spotify / Apple Music / YouTube / etc. for the full recording using the native handler.
 * Call only after [compose.project.click.click.util.isBeaconOriginalSongDeepLinkUrl] passes.
 */
expect fun openBeaconOriginalMediaUrl(url: String): Boolean