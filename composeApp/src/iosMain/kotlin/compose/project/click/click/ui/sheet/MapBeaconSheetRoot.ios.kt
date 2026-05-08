@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package compose.project.click.click.ui.sheet // pragma: allowlist secret

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.window.ComposeUIViewController
import com.mohamedrejeb.calf.ui.utils.toUIColor
import compose.project.click.click.ui.theme.PlatformThemeProvider
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
 * matches a half-height sheet and avoids empty gray/white bands above short Compose content.
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
    private var isChromeDark: Boolean,
    private var containerColor: Color,
    private val onDismissFromSwipe: () -> Unit,
    private val schemeState: MutableState<ColorScheme>,
    private val typographyState: MutableState<Typography>,
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

    fun syncFromParent(
        scheme: ColorScheme,
        typography: Typography,
        container: Color,
    ) {
        schemeState.value = scheme
        typographyState.value = typography
        // Beacon/map sheets pass OLED black — force dark sheet chrome even when app theme is light.
        isChromeDark = scheme.background.luminance() < 0.5f || container.luminance() < 0.12f
        containerColor = container
        applyTheme(isChromeDark)
        applyContainerColor(container)
    }

    private val sheetViewController: UIViewController by lazy {
        ComposeUIViewController {
            val scheme = schemeState.value
            val typography = typographyState.value
            MaterialTheme(
                colorScheme = scheme,
                typography = typography,
            ) {
                PlatformThemeProvider {
                    Column(
                        modifierState.value
                            .fillMaxWidth()
                            .fillMaxHeight(),
                    ) {
                        contentState.value(this)
                    }
                }
            }
        }.apply {
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

    lateinit var contentState: androidx.compose.runtime.State<@Composable ColumnScope.() -> Unit>
    lateinit var modifierState: androidx.compose.runtime.State<Modifier>

    fun applyTheme(dark: Boolean) {
        isChromeDark = dark
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
        applyTheme(isChromeDark)
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
    appColorScheme: ColorScheme,
    appTypography: Typography,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return

    val parent = LocalUIViewController.current
    val onDismissState = rememberUpdatedState(onDismissRequest)
    val containerColorState = rememberUpdatedState(containerColor)
    val contentState = rememberUpdatedState(content)
    val modifierState = rememberUpdatedState(modifier)
    val schemeState = remember { mutableStateOf(appColorScheme) }
    val typographyState = remember { mutableStateOf(appTypography) }

    val manager = remember(parent) {
        val m = MapIosHalfSheetManager(
            parentUIViewController = parent,
            isChromeDark = appColorScheme.background.luminance() < 0.5f,
            containerColor = containerColorState.value,
            onDismissFromSwipe = { onDismissState.value.invoke() },
            schemeState = schemeState,
            typographyState = typographyState,
        )
        m.contentState = contentState
        m.modifierState = modifierState
        m
    }

    SideEffect {
        manager.syncFromParent(
            appColorScheme,
            appTypography,
            containerColorState.value,
        )
    }

    DisposableEffect(manager) {
        manager.show()
        onDispose {
            manager.hide()
        }
    }
}
