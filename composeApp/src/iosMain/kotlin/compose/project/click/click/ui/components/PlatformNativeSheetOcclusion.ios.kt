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

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformNativeSheetOcclusion(active: Boolean) {
    val host = LocalUIViewController.current
    DisposableEffect(active, host) {
        if (!active) {
            return@DisposableEffect onDispose { }
        }

        val root = host.presentationRoot()
        // Hiding the first presented sheet container hides its entire nested sheet chain (Nearby +
        // Event detail) while keeping the UIKit controllers presented and stateful underneath.
        val sheetContainer = root.firstPresentedSheetContainer()
        if (sheetContainer != null) {
            sheetContainer.hidden = true
            sheetContainer.userInteractionEnabled = false
        }

        onDispose {
            if (sheetContainer != null) {
                sheetContainer.hidden = false
                sheetContainer.userInteractionEnabled = true
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
private fun UIViewController.firstPresentedSheetContainer(): UIView? {
    var presented = presentedViewController
    while (presented != null) {
        if (presented.sheetPresentationController != null) {
            return presented.presentationController?.containerView
        }
        presented = presented.presentedViewController
    }
    return null
}
