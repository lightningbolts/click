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
import kotlinx.cinterop.useContents
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIColor
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.additionalSafeAreaInsets
import platform.UIKit.presentationController

/**
 * iOS portal for full-screen app destinations that must appear above an existing native page-sheet
 * stack.
 *
 * The Compose hosting controller returned by [ComposeUIViewController] is already owned by Compose's
 * UIKit integration. Do not manually add/remove it as a UIKit child: doing so creates a second parent
 * relationship and crashes with UIViewControllerHierarchyInconsistency when profile media or event
 * hub content opens.
 *
 * Instead, mount only its view into the active UIWindow (falling back to the presentation container)
 * and constrain it to that full-screen container. A detached Compose hosting controller can report a
 * zero safe-area even after its view is inserted into the window, so copy the window's top safe-area
 * into additionalSafeAreaInsets only when the portal still reports zero. That keeps the exact same
 * native media/hub chrome implementation aligned below the status bar without inventing a second
 * controller hierarchy.
 *
 * On teardown, reattach the shared native chrome to the caller before removing the portal view.
 * [IosHostNavBarLayer] retains its attached controller, so this breaks the chrome -> temporary portal
 * host retain path before the portal leaves the hierarchy and prevents stale hub/media chrome from
 * leaking into later screens.
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

            val portalTopInset = overlayView.safeAreaInsets.useContents { top }
            val containerTopInset = container.safeAreaInsets.useContents { top }
            if (portalTopInset <= 1.0 && containerTopInset > 1.0) {
                overlayController.additionalSafeAreaInsets =
                    UIEdgeInsetsMake(containerTopInset, 0.0, 0.0, 0.0)
                container.layoutIfNeeded()
                overlayView.layoutIfNeeded()
            }

            container.bringSubviewToFront(overlayView)
        }

        onDispose {
            // Break shared chrome -> temporary portal-controller retention before removing its view.
            IosNavChrome.shared.attach(host)
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
