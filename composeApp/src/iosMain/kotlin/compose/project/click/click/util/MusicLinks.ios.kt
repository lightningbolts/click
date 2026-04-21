package compose.project.click.click.util // pragma: allowlist secret

import compose.project.click.click.ui.utils.openIosUrlMain // pragma: allowlist secret
import platform.Foundation.NSURL

actual fun openMusicStreamingUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return false
    val nsUrl = NSURL.URLWithString(trimmed) ?: return false
    openIosUrlMain(nsUrl)
    return true
}
