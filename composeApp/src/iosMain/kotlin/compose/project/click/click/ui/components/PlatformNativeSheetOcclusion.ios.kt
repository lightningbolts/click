@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.presentationController
import platform.UIKit.sheetPresentationController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformNativeSheetOcclusion(active: Boolean) {
    val host = LocalUIViewController.current
    DisposableEffect(active, host) {
        if (!active) {
            return@DisposableEffect onDispose { }
        }

        val root = host.presentationRoot()
        val hiddenContainers = mutableListOf<UIView>()
        var disposed = false

        fun hidePresentedSheetChain() {
            if (disposed) return
            root.presentedSheetContainers().forEach { container ->
                if (hiddenContainers.none { it === container }) {
                    hiddenContainers += container
                }
                container.hidden = true
                container.userInteractionEnabled = false
            }
        }

        // The event hub state is published from Compose while UIKit may still be finishing the
        // nested Event-detail sheet transaction. Hide what exists now and repeat on the next main
        // run-loop turn so both Nearby and its child detail container are occluded together.
        hidePresentedSheetChain()
        dispatch_async(dispatch_get_main_queue()) {
            hidePresentedSheetChain()
        }

        onDispose {
            disposed = true
            hiddenContainers.forEach { container ->
                container.hidden = false
                container.userInteractionEnabled = true
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun UIViewController.presentationRoot(): UIViewController {
    var current = this
    while (current.presentingViewController != null) {
        current = current.presentingViewController ?: break
    }
    return current
}

@OptIn(ExperimentalForeignApi::class)
private fun UIViewController.presentedSheetContainers(): List<UIView> {
    val containers = mutableListOf<UIView>()
    var presented = presentedViewController
    while (presented != null) {
        if (presented.sheetPresentationController != null) {
            presented.presentationController?.containerView?.let(containers::add)
        }
        presented = presented.presentedViewController
    }
    return containers
}
