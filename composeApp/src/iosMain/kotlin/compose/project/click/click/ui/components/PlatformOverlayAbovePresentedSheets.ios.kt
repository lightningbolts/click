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
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.UIKit.presentationController
import platform.UIKit.removeFromParentViewController
import platform.UIKit.willMoveToParentViewController

/**
 * iOS portal for full-screen app destinations that must appear above an existing native page-sheet
 * stack.
 *
 * The overlay view is mounted in the active UIWindow so it can cover a presented profile sheet, but
 * the Compose controller remains a child of the full-screen presentation root. Controller
 * containment is important here: without it the portal host can report a zero top safe-area, which
 * places the shared native navigation controls inside the status bar.
 *
 * On teardown the shared native chrome is reattached to the caller before the portal controller is
 * removed. [IosHostNavBarLayer] retains its attached controller; breaking that retain first lets the
 * portal composition dispose and unregister its native-chrome owner instead of leaking hub/media
 * chrome into later screens after an interactive back gesture.
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
            root.addChildViewController(overlayController)

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
            overlayController.didMoveToParentViewController(root)
        }

        onDispose {
            // Break shared chrome -> portal-controller retention before UIKit containment teardown.
            IosNavChrome.shared.attach(host)
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
