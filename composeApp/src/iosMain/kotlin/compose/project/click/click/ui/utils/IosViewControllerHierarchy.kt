@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package compose.project.click.click.ui.utils

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * Resolves the top-most [UIViewController] for modal presentation. `UIApplication.keyWindow`
 * is often nil on iOS 13+ scene-based apps (including Compose Multiplatform), which breaks
 * share sheets and other UI that still anchor off the legacy key window.
 */
fun iosTopViewControllerForPresentation(): UIViewController? {
    val app = UIApplication.sharedApplication
    app.keyWindow?.rootViewController?.let { return it.topPresented() }

    val scenes = app.connectedScenes
    scenes.forEach { scene ->
        val ws = scene as? UIWindowScene ?: return@forEach
        if (ws.activationState != UISceneActivationStateForegroundActive) return@forEach
        ws.windows.forEach { win ->
            val window = win as? UIWindow ?: return@forEach
            if (window.isKeyWindow()) {
                window.rootViewController?.let { return it.topPresented() }
            }
        }
    }

    scenes.forEach { scene ->
        val ws = scene as? UIWindowScene ?: return@forEach
        if (ws.activationState != UISceneActivationStateForegroundActive) return@forEach
        (ws.windows.firstOrNull() as? UIWindow)?.rootViewController?.let { return it.topPresented() }
    }

    return null
}

private fun UIViewController.topPresented(): UIViewController {
    var vc: UIViewController = this
    while (vc.presentedViewController != null) {
        vc = vc.presentedViewController ?: break
    }
    return vc
}
