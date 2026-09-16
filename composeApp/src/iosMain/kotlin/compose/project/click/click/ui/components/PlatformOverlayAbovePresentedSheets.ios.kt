@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.window.ComposeUIViewController
import compose.project.click.click.ui.theme.PlatformStyleProvider // pragma: allowlist secret
import kotlinx.cinterop.useContents
import kotlinx.coroutines.delay
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIColor
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.additionalSafeAreaInsets
import platform.UIKit.presentationController

/**
 * iOS portal for full-screen app destinations that must appear above an existing native page-sheet
 * stack. The overlay view is inserted at the front of the top presentation controller's full-screen
 * container instead of dismissing/hiding the sheets. This matters because a hidden presented sheet
 * still leaves UIKit's modal tint/hit-testing state active on its presenter.
 *
 * ComposeUIViewController can expose an inner hosting controller whose safe-area is still zero during
 * the first portal frames even though its view already fills the real window. Native Click chrome
 * binds to LocalUIViewController, so repair the safe-area on that actual inner controller rather than
 * parenting/reparenting the outer Compose controller. This keeps profile-media and event-hub native
 * Liquid Glass chrome on the same geometry as ordinary full-screen chat without creating a second
 * UIViewController parent relationship.
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
    val hostWindow = host.view.window
    val fallbackTopInset =
        if (hostWindow != null) {
            hostWindow.safeAreaInsets.useContents { top }
        } else {
            host.view.safeAreaInsets.useContents { top }
        }
    val latestTopInset = rememberUpdatedState(fallbackTopInset)
    val latestContent = rememberUpdatedState(content)
    val latestScheme = rememberUpdatedState(MaterialTheme.colorScheme)
    val latestTypography = rememberUpdatedState(MaterialTheme.typography)
    val overlayController =
        remember(host) {
            ComposeUIViewController {
                PortalNativeChromeSafeArea(latestTopInset.value)
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
            topmost.presentationController?.containerView
                ?: topmost.view.window
                ?: host.view.window

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
            container.bringSubviewToFront(overlayView)
        }

        onDispose {
            overlayController.view.removeFromSuperview()
        }
    }
}

@Composable
private fun PortalNativeChromeSafeArea(fallbackTopInset: Double) {
    val controller = LocalUIViewController.current
    LaunchedEffect(controller, fallbackTopInset) {
        repeat(4) { attempt ->
            if (attempt > 0) delay(16L)
            val currentTop = controller.view.safeAreaInsets.useContents { top }
            if (currentTop > 1.0) return@LaunchedEffect

            val window = controller.view.window
            val resolvedTop =
                if (window != null) {
                    window.safeAreaInsets.useContents { top }
                } else {
                    fallbackTopInset
                }
            if (resolvedTop > 1.0) {
                controller.additionalSafeAreaInsets = UIEdgeInsetsMake(resolvedTop, 0.0, 0.0, 0.0)
                controller.view.layoutIfNeeded()
                return@LaunchedEffect
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
