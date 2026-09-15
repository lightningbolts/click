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
 * stack.
 *
 * The overlay is attached to the active UIWindow when possible rather than directly to a
 * UIPresentationController container. Presentation containers do not reliably propagate the
 * window's safe-area geometry to an independently hosted Compose controller; that was placing the
 * event-hub native header inside the status bar. The controller is also enrolled in UIKit child
 * containment for the exact lifetime of the portal so its Compose/native-chrome owners are torn
 * down when the portal leaves instead of continuing to publish stale chrome state off-screen.
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
        val container: UIView? =
            topmost.view.window
                ?: host.view.window
                ?: topmost.presentationController?.containerView

        if (container != null) {
            topmost.addChildViewController(overlayController)

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
            container.layoutIfNeeded()
            container.bringSubviewToFront(overlayView)
            overlayController.didMoveToParentViewController(topmost)
        }

        onDispose {
            if (overlayController.parentViewController != null) {
                overlayController.willMoveToParentViewController(null)
            }
            overlayController.view.removeFromSuperview()
            if (overlayController.parentViewController != null) {
                overlayController.removeFromParentViewController()
            }
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
