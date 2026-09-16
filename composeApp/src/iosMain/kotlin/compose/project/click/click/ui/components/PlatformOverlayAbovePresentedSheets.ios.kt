@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.window.ComposeUIViewController
import compose.project.click.click.ui.theme.PlatformStyleProvider // pragma: allowlist secret
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.presentationController

/**
 * iOS portal for full-screen app destinations that must appear above an existing native page-sheet
 * stack. The overlay view is inserted above the live presentation stack instead of dismissing/hiding
 * the sheets. This matters because a hidden presented sheet still leaves UIKit's modal tint/hit-testing
 * state active on its presenter.
 */
@Composable
actual fun PlatformOverlayAbovePresentedSheets(
    liftAbovePresentedSheets: Boolean,
    content: @Composable () -> Unit,
) {
    if (!liftAbovePresentedSheets) {
        content()
        return
    }

    val host = LocalUIViewController.current
    val latestContent = rememberUpdatedState(content)
    val latestScheme = rememberUpdatedState(MaterialTheme.colorScheme)
    val latestTypography = rememberUpdatedState(MaterialTheme.typography)
    val overlayController =
        remember(host) {
            ComposeUIViewController {
                MaterialTheme(
                    colorScheme = latestScheme.value,
                    typography = latestTypography.value,
                ) {
                    PlatformStyleProvider {
                        latestContent.value.invoke()
                    }
                }
            }
        }

    DisposableEffect(host, overlayController) {
        val root = host.presentationRootController()
        val topmost = root.topmostPresentedController()
        // Prefer the real UIWindow. A presentation-controller container can extend through the
        // status-bar region and report a zero top safe area to the detached Compose host, which is
        // what placed profile-media native chrome under the status bar. A direct window child stays
        // above the presented sheet while inheriting the window's actual safe-area geometry.
        val container: UIView? =
            topmost.view.window
                ?: host.view.window
                ?: topmost.presentationController?.containerView

        if (container != null) {
            val overlayView = overlayController.view
            overlayView.translatesAutoresizingMaskIntoConstraints = false
            overlayView.backgroundColor = UIColor.clearColor
            overlayView.setOpaque(false)
            container.addSubview(overlayView)
            NSLayoutConstraint.activateConstraints(
                listOf(
                    overlayView.topAnchor.constraintEqualToAnchor(container.topAnchor),
                    overlayView.leadingAnchor.constraintEqualToAnchor(container.leadingAnchor),
                    overlayView.trailingAnchor.constraintEqualToAnchor(container.trailingAnchor),
                    overlayView.bottomAnchor.constraintEqualToAnchor(container.bottomAnchor),
                ),
            )
            container.bringSubviewToFront(overlayView)
        }

        onDispose {
            overlayController.view.removeFromSuperview()
        }
    }
}

private fun UIViewController.presentationRootController(): UIViewController {
    var root = this
    while (root.presentingViewController != null) {
        root = root.presentingViewController ?: break
    }
    return root
}

private fun UIViewController.topmostPresentedController(): UIViewController {
    var top = this
    while (top.presentedViewController != null) {
        top = top.presentedViewController ?: break
    }
    return top
}
