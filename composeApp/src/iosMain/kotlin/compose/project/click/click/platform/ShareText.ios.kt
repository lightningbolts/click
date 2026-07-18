package compose.project.click.click.platform

import compose.project.click.click.ui.utils.iosTopViewControllerForPresentation
import platform.UIKit.UIActivityViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

actual fun shareText(text: String, subject: String?) {
    val body = text.trim()
    if (body.isEmpty()) return
    val items = buildList {
        subject?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        add(body)
    }
    val activityViewController = UIActivityViewController(items, null)
    dispatch_async(dispatch_get_main_queue()) {
        val root = iosTopViewControllerForPresentation() ?: return@dispatch_async
        root.presentViewController(activityViewController, animated = true, completion = null)
    }
}
