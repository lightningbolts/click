package compose.project.click.click.ui.utils

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Opens a URL on the main queue using `open(_:options:completionHandler:)` (iOS 10+). */
fun openIosUrlMain(url: NSURL?) {
    if (url == null) return
    dispatch_async(dispatch_get_main_queue()) {
        val app = UIApplication.sharedApplication
        if (!app.canOpenURL(url)) return@dispatch_async
        app.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}
