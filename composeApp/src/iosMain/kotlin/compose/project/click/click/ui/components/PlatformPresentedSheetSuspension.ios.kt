@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.uikit.LocalUIViewController
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.presentationController

private data class SuspendedPresentation(
    val controller: UIViewController,
    val delegate: UIAdaptivePresentationControllerDelegateProtocol?,
)

/**
 * Event hub chat must run in the root Compose/UIKit host to share the exact same IosNavChrome and
 * UITabBar instances as 1:1/group chat. The Map/Nearby/Event flow is presented as native page sheets,
 * so temporarily dismiss those controllers without firing their swipe-dismiss delegates, retain the
 * exact controller instances, then put the same stack back when the full-screen route closes.
 *
 * This is intentionally different from hiding sheet views (UIKit still dims/disables the presenter)
 * and from portaling another ComposeUIViewController above them (which creates a second chrome host).
 */
private class PresentedSheetSuspensionCoordinator(
    private val host: UIViewController,
) {
    private var suspended: List<SuspendedPresentation> = emptyList()
    private var suspensionRoot: UIViewController? = null
    private var isSuspended = false
    private var generation = 0

    fun suspendPresentedStack(onReady: () -> Unit) {
        if (isSuspended) {
            val root = suspensionRoot ?: host.presentationRootController()
            if (root.presentedViewController == null) onReady()
            return
        }

        val root = host.presentationRootController()
        suspensionRoot = root
        isSuspended = true
        val stack = mutableListOf<SuspendedPresentation>()
        var controller = root.presentedViewController
        while (controller != null) {
            stack +=
                SuspendedPresentation(
                    controller = controller,
                    delegate = controller.presentationController?.delegate,
                )
            controller = controller.presentedViewController
        }
        suspended = stack

        if (stack.isEmpty()) {
            onReady()
            return
        }

        // These are temporary programmatic dismissals. A sheet delegate interprets DidDismiss as a
        // user swipe and clears the backing Compose selection, which would destroy the state we need
        // to restore after Event Hub Back.
        stack.forEach { it.controller.presentationController?.delegate = null }
        val token = ++generation
        root.dismissViewControllerAnimated(
            flag = false,
            completion = {
                if (isSuspended && generation == token) {
                    onReady()
                }
            },
        )
    }

    fun restore() {
        if (!isSuspended) return
        isSuspended = false
        ++generation

        val root = suspensionRoot ?: host.presentationRootController()
        suspensionRoot = null
        val stack = suspended
        suspended = emptyList()
        if (stack.isEmpty()) return

        restorePresentation(
            index = 0,
            presenter = root,
            stack = stack,
        )
    }

    private fun restorePresentation(
        index: Int,
        presenter: UIViewController,
        stack: List<SuspendedPresentation>,
    ) {
        if (index >= stack.size) return
        val item = stack[index]
        presenter.presentViewController(
            viewControllerToPresent = item.controller,
            animated = false,
            completion = {
                item.controller.presentationController?.delegate = item.delegate
                restorePresentation(
                    index = index + 1,
                    presenter = item.controller,
                    stack = stack,
                )
            },
        )
    }
}

@Composable
actual fun PlatformPresentedSheetSuspension(active: Boolean): Boolean {
    val host = LocalUIViewController.current
    val coordinator = remember(host) { PresentedSheetSuspensionCoordinator(host) }
    var ready by remember(coordinator, active) { mutableStateOf(!active) }

    LaunchedEffect(active, coordinator) {
        if (active) {
            coordinator.suspendPresentedStack { ready = true }
        } else {
            coordinator.restore()
        }
    }

    DisposableEffect(coordinator) {
        onDispose {
            coordinator.restore()
        }
    }

    return if (active) ready else true
}

private fun UIViewController.presentationRootController(): UIViewController {
    // LocalUIViewController may be the Compose child hosted *inside* a native map/event page sheet.
    // Walking only presentingViewController from that child yields no sheet stack, so Event Hub is
    // rendered behind the still-visible map. Resolve from the live UIWindow when the hub actually
    // opens; the window owns the real presentation chain.
    view.window?.rootViewController?.let { return it }
    var root = this
    while (root.presentingViewController != null) {
        root = root.presentingViewController ?: break
    }
    return root
}
