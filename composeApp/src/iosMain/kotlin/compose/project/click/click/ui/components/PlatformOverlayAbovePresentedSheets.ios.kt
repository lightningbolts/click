@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.window.ComposeUIViewController
import compose.project.click.click.ui.theme.LocalIsDarkMode // pragma: allowlist secret
import compose.project.click.click.ui.theme.PlatformStyleProvider // pragma: allowlist secret
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.UIKit.UIColor
import platform.UIKit.UIModalPresentationOverFullScreen
import platform.UIKit.UIView
import platform.UIKit.UIViewController

private const val MODAL_LIFT_ENTER_SECONDS = 0.28
private const val MODAL_LIFT_EXIT_SECONDS = 0.22
private const val MODAL_LIFT_ENTER_OFFSET_PT = 30.0
private const val MODAL_LIFT_EXIT_OFFSET_PT = 22.0

/**
 * Full-screen iOS portal for content launched from an already-presented native sheet.
 *
 * This must be a real UIKit presentation, not a detached view manually inserted into UIWindow.
 * A detached ComposeUIViewController never participates in the presentation hierarchy, so its
 * safe-area guide can remain at y=0 and native Liquid Glass chrome is laid over the status bar.
 * Presenting the controller over full-screen gives it the same UIKit safe-area/layout contract as
 * the working chat media path while keeping the underlying native sheet stack mounted.
 */
@Composable
actual fun PlatformOverlayAbovePresentedSheets(
    liftAbovePresentedSheets: Boolean,
    dismissing: Boolean,
    revealUnderlyingPresentation: Boolean,
    presentationMotion: PlatformOverlayPresentationMotion,
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
    val latestDarkMode = rememberUpdatedState(LocalIsDarkMode.current)
    val overlayController =
        remember(host) {
            ComposeUIViewController {
                MaterialTheme(
                    colorScheme = latestScheme.value,
                    typography = latestTypography.value,
                ) {
                    // This is a separate Compose root, so CompositionLocals from the app root do
                    // not cross the UIKit presentation boundary automatically. Preserve dark-mode
                    // ownership explicitly or native Liquid Glass title/glyph colors fall back to
                    // LocalIsDarkMode's light default while the app itself is dark.
                    CompositionLocalProvider(LocalIsDarkMode provides latestDarkMode.value) {
                        PlatformStyleProvider {
                            latestContent.value.invoke()
                        }
                    }
                }
            }.apply {
                modalPresentationStyle = UIModalPresentationOverFullScreen
                modalPresentationCapturesStatusBarAppearance = true
            }
        }

    DisposableEffect(host, overlayController, revealUnderlyingPresentation, presentationMotion) {
        val presenter = host.presentationRootController().topmostPresentedController()
        val overlayView = overlayController.view
        overlayView.userInteractionEnabled = true
        overlayView.transform = CGAffineTransformMakeTranslation(0.0, 0.0)
        if (presentationMotion == PlatformOverlayPresentationMotion.ModalLift) {
            overlayView.alpha = 0.0
            overlayView.transform = CGAffineTransformMakeTranslation(0.0, MODAL_LIFT_ENTER_OFFSET_PT)
        } else {
            overlayView.alpha = 1.0
        }
        if (revealUnderlyingPresentation) {
            overlayView.backgroundColor = UIColor.clearColor
            overlayView.setOpaque(false)
        }
        if (overlayController.presentingViewController == null) {
            presenter.presentViewController(
                viewControllerToPresent = overlayController,
                animated = false,
                completion = {
                    if (presentationMotion == PlatformOverlayPresentationMotion.ModalLift) {
                        UIView.animateWithDuration(
                            MODAL_LIFT_ENTER_SECONDS,
                            animations = {
                                overlayView.alpha = 1.0
                                overlayView.transform = CGAffineTransformMakeTranslation(0.0, 0.0)
                            },
                        )
                    }
                },
            )
        }

        onDispose {
            if (overlayController.presentingViewController != null) {
                overlayController.dismissViewControllerAnimated(
                    flag = false,
                    completion = null,
                )
            }
        }
    }

    LaunchedEffect(dismissing, presentationMotion, overlayController) {
        val overlayView = overlayController.view
        if (dismissing) {
            overlayView.userInteractionEnabled = false
            when (presentationMotion) {
                PlatformOverlayPresentationMotion.Standard -> {
                    UIView.animateWithDuration(
                        UnifiedPopupMotion.Media.fadeOutMillis / 1000.0,
                        animations = {
                            overlayView.alpha = 0.0
                        },
                    )
                }
                PlatformOverlayPresentationMotion.ModalLift -> {
                    UIView.animateWithDuration(
                        MODAL_LIFT_EXIT_SECONDS,
                        animations = {
                            overlayView.alpha = 0.0
                            overlayView.transform =
                                CGAffineTransformMakeTranslation(0.0, MODAL_LIFT_EXIT_OFFSET_PT)
                        },
                    )
                }
            }
        } else if (presentationMotion == PlatformOverlayPresentationMotion.Standard) {
            overlayView.alpha = 1.0
            overlayView.transform = CGAffineTransformMakeTranslation(0.0, 0.0)
            overlayView.userInteractionEnabled = true
        } else {
            overlayView.userInteractionEnabled = true
        }
    }
}

private fun UIViewController.presentationRootController(): UIViewController {
    view.window?.rootViewController?.let { return it }
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
