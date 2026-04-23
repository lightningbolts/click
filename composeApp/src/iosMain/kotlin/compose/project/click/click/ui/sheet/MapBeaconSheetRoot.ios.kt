@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package compose.project.click.click.ui.sheet // pragma: allowlist secret

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.window.ComposeUIViewController
import com.mohamedrejeb.calf.ui.utils.toUIColor
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UIModalTransitionStyleCoverVertical
import platform.UIKit.UIPresentationController
import platform.UIKit.UISheetPresentationControllerDetent
import platform.UIKit.UIViewController
import platform.UIKit.presentationController
import platform.UIKit.sheetPresentationController
import platform.darwin.NSObject

/**
 * iOS: Calf’s [AdaptiveBottomSheet] uses `.largeDetent()` whenever `skipPartiallyExpanded = true`,
 * which is always full-screen. We present a page sheet with **medium detent only** so the panel
 * matches a half-height sheet and avoids empty gray / light chrome above short Compose content.
 */
private class MapIosHalfSheetDelegate(
    private val onDismissed: () -> Unit,
) : NSObject(), UIAdaptivePresentationControllerDelegateProtocol {
    override fun presentationControllerShouldDismiss(presentationController: UIPresentationController): Boolean {
        return true
    }

    override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
        onDismissed()
    }
}

private class MapIosHalfSheetManager(
    private val parentUIViewController: UIViewController,
    private var isDark: Boolean,
    private var containerColor: Color,
    private val onDismissFromSwipe: () -> Unit,
    private val sheetContent: @Composable () -> Unit,
) {
    private val delegate by lazy {
        MapIosHalfSheetDelegate(
            onDismissed = {
                markDismissedByPresentation()
                onDismissFromSwipe()
            },
        )
    }

    private fun markDismissedByPresentation() {
        isPresented = false
        isAnimating = false
    }

    private var isInitialized = false
    private var isPresented = false
    private var isAnimating = false

    private val sheetViewController: UIViewController by lazy {
        ComposeUIViewController(sheetContent).apply {
            modalPresentationStyle = UIModalPresentationPageSheet
            modalTransitionStyle = UIModalTransitionStyleCoverVertical
            presentationController?.delegate = delegate
            sheetPresentationController?.setDetents(
                listOf(UISheetPresentationControllerDetent.mediumDetent()),
            )
            sheetPresentationController?.prefersGrabberVisible = false
            isInitialized = true
        }
    }

    fun applyTheme(dark: Boolean) {
        isDark = dark
        if (isInitialized) {
            sheetViewController.overrideUserInterfaceStyle = if (dark) {
                UIUserInterfaceStyle.UIUserInterfaceStyleDark
            } else {
                UIUserInterfaceStyle.UIUserInterfaceStyleLight
            }
        }
    }

    fun applyContainerColor(color: Color) {
        containerColor = color
        if (isInitialized) {
            sheetViewController.view.backgroundColor = color.toUIColor()
        }
    }

    fun show() {
        if (isPresented || isAnimating) return
        isAnimating = true
        applyTheme(isDark)
        applyContainerColor(containerColor)
        parentUIViewController.presentViewController(
            viewControllerToPresent = sheetViewController,
            animated = true,
            completion = {
                isPresented = true
                isAnimating = false
            },
        )
    }

    fun hide() {
        if (!isPresented || isAnimating) return
        isAnimating = true
        sheetViewController.dismissViewControllerAnimated(
            flag = true,
            completion = {
                isPresented = false
                isAnimating = false
            },
        )
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
actual fun MapBeaconSheetRoot(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    scrimColor: Color,
    contentWindowInsets: @Composable () -> WindowInsets,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return

    val parent = LocalUIViewController.current
    val isDark = isSystemInDarkTheme()
    val onDismissState = rememberUpdatedState(onDismissRequest)
    val containerColorState = rememberUpdatedState(containerColor)
    val contentState = rememberUpdatedState(content)
    val modifierState = rememberUpdatedState(modifier)

    val manager = remember(parent) {
        MapIosHalfSheetManager(
            parentUIViewController = parent,
            isDark = isDark,
            containerColor = containerColorState.value,
            onDismissFromSwipe = { onDismissState.value.invoke() },
            sheetContent = {
                Column(
                    modifierState.value
                        .fillMaxWidth()
                        .fillMaxHeight(),
                ) {
                    contentState.value(this)
                }
            },
        )
    }

    LaunchedEffect(isDark) {
        manager.applyTheme(isDark)
    }

    LaunchedEffect(containerColor) {
        manager.applyContainerColor(containerColor)
    }

    DisposableEffect(manager) {
        manager.show()
        onDispose {
            manager.hide()
        }
    }
}
