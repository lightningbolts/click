package compose.project.click.click.ui.utils

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Deep-link directly into the Click app's dedicated page in iOS Settings.
 *
 * Previously this went through [openIosUrlMain] which gates on `canOpenURL`. On iOS 15+,
 * `canOpenURL` returns `false` for `app-settings:` URLs unless the scheme is declared in
 * `LSApplicationQueriesSchemes` — which it shouldn't be (Apple docs: "do not add
 * app-settings to LSApplicationQueriesSchemes"). The safe pattern is to skip the probe
 * entirely and call `openURL(_:options:completionHandler:)` directly: iOS guarantees the
 * settings scheme is resolvable, and we simply swallow any asynchronous failure rather
 * than showing a misleading "generic settings" fallback.
 */
actual fun openApplicationSystemSettings() {
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
    dispatch_async(dispatch_get_main_queue()) {
        UIApplication.sharedApplication.openURL(
            url,
            options = emptyMap<Any?, Any>(),
            completionHandler = null,
        )
    }
}
