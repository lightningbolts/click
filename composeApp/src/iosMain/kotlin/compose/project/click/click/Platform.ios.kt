package compose.project.click.click

import compose.project.click.click.ui.utils.openIosUrlMain
import compose.project.click.click.util.isBeaconOriginalSongDeepLinkUrl
import platform.Foundation.NSURL
import platform.UIKit.UIDevice

class IOSPlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun openBeaconOriginalMediaUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isEmpty() || !isBeaconOriginalSongDeepLinkUrl(trimmed)) return false
    val nsUrl = NSURL.URLWithString(trimmed) ?: return false
    openIosUrlMain(nsUrl)
    return true
}