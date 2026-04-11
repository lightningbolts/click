package compose.project.click.click.ui.utils

import platform.Foundation.NSURL
import platform.UIKit.UIApplicationOpenSettingsURLString

actual fun openApplicationSystemSettings() {
    openIosUrlMain(NSURL.URLWithString(UIApplicationOpenSettingsURLString))
}
