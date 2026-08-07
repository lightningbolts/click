@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package compose.project.click.click.ui.sheet // pragma: allowlist secret

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.window.ComposeUIViewController
import compose.project.click.click.ui.components.LocalSheetOnDismissRequest
import compose.project.click.click.ui.components.LocalSheetScrollOwnedByHost
import compose.project.click.click.ui.components.LocalSheetUsesPlatformGrabber
import compose.project.click.click.ui.components.ProvideSheetSurfaceDrag
import compose.project.click.click.ui.components.SheetFingerDismissHost
import compose.project.click.click.ui.theme.PlatformStyleProvider
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSProcessInfo
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIColor
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UIModalTransitionStyleCoverVertical
import platform.UIKit.UIPresentationController
import platform.UIKit.UIScreen
import platform.UIKit.UIScrollView
import platform.UIKit.UIScrollViewContentInsetAdjustmentBehavior
import platform.UIKit.UISheetPresentationControllerDetent
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.presentationController
import platform.UIKit.sheetPresentationController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.math.abs

/**
 * iOS platform sheets — native [UISheetPresentationController] Liquid Glass.
 *
 * - **Page background**: clear (system glass). No OLED wash.
 * - **Components**: solid (via [ClickSheetDialogChrome] theme).
 * - [useUiKitScrollHost]: UIScrollView owns scroll → system swipe-down dismiss at top.
 * - Otherwise: Compose scroll + whole-sheet UIView drag-to-dismiss.
 */
private class MapIosNativeSheetDelegate(
    private val onDismissed: () -> Unit,
) : NSObject(), UIAdaptivePresentationControllerDelegateProtocol {
    override fun presentationControllerShouldDismiss(
        presentationController: UIPresentationController,
    ): Boolean = true

    override fun presentationControllerDidDismiss(
        presentationController: UIPresentationController,
    ) {
        onDismissed()
    }
}

private fun iosMajorVersion(): Long =
    NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion }

private class MapIosNativeSheetManager(
    private val parentUIViewController: UIViewController,
    private var isChromeDark: Boolean,
    private val expandable: Boolean,
    private val useUiKitScrollHost: Boolean,
    private val onDismissFromSwipe: () -> Unit,
    private val schemeState: MutableState<ColorScheme>,
    private val typographyState: MutableState<Typography>,
) {
    private val delegate by lazy {
        MapIosNativeSheetDelegate(
            onDismissed = {
                isPresented = false
                isAnimating = false
                resetSurfaceDrag()
                onDismissFromSwipe()
            },
        )
    }

    private var isInitialized = false
    private var isPresented = false
    private var isAnimating = false
    private var contentHeightConstraint: NSLayoutConstraint? = null
    private var lastContentHeightPt = 0.0
    private var retainedComposeVc: UIViewController? = null

    private val scrollView: UIScrollView by lazy {
        UIScrollView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            alwaysBounceVertical = true
            scrollsToTop = true
            backgroundColor = UIColor.clearColor
            setOpaque(false)
            contentInsetAdjustmentBehavior =
                UIScrollViewContentInsetAdjustmentBehavior.UIScrollViewContentInsetAdjustmentAutomatic
        }
    }

    private val composeContainer: UIView by lazy {
        UIView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            backgroundColor = UIColor.clearColor
            setOpaque(false)
        }
    }

    private val sheetViewController: UIViewController by lazy {
        if (useUiKitScrollHost) buildScrollHostedSheet() else buildFillSheet()
    }

    fun setSurfaceDragOffsetPx(offsetPx: Float) {
        if (!isInitialized) return
        val y = if (offsetPx <= 0.5f) 0.0 else offsetPx.toDouble()
        sheetViewController.view.setTransform(CGAffineTransformMakeTranslation(0.0, y))
    }

    private fun resetSurfaceDrag() {
        if (!isInitialized) return
        sheetViewController.view.setTransform(CGAffineTransformMakeTranslation(0.0, 0.0))
    }

    private fun applyClearHost(host: UIViewController) {
        host.view.backgroundColor = UIColor.clearColor
        host.view.setOpaque(false)
        host.view.layer.backgroundColor = UIColor.clearColor.CGColor
        if (iosMajorVersion() < 26L) {
            host.view.backgroundColor =
                UIColor.colorWithRed(0.0, green = 0.0, blue = 0.0, alpha = 0.45)
        }
    }

    private fun configurePageSheet(host: UIViewController) {
        host.modalPresentationStyle = UIModalPresentationPageSheet
        host.modalTransitionStyle = UIModalTransitionStyleCoverVertical
        host.presentationController?.delegate = delegate
        val sheet = host.sheetPresentationController
        sheet?.setDetents(
            if (expandable) {
                listOf(
                    UISheetPresentationControllerDetent.mediumDetent(),
                    UISheetPresentationControllerDetent.largeDetent(),
                )
            } else {
                listOf(UISheetPresentationControllerDetent.mediumDetent())
            },
        )
        sheet?.prefersGrabberVisible = true
        sheet?.prefersScrollingExpandsWhenScrolledToEdge = useUiKitScrollHost
        applyClearHost(host)
    }

    private fun buildFillSheet(): UIViewController {
        val host = ComposeUIViewController {
            val scheme = schemeState.value
            val typography = typographyState.value
            MaterialTheme(colorScheme = scheme, typography = typography) {
                PlatformStyleProvider {
                    CompositionLocalProvider(
                        LocalSheetOnDismissRequest provides onDismissFromSwipe,
                        LocalSheetUsesPlatformGrabber provides true,
                        LocalSheetScrollOwnedByHost provides false,
                    ) {
                        ProvideSheetSurfaceDrag(
                            onDragOffsetPx = { setSurfaceDragOffsetPx(it) },
                        ) {
                            SheetFingerDismissHost(
                                onDismissRequest = onDismissFromSwipe,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(),
                            ) {
                                Column(
                                    modifier = modifierState.value
                                        .fillMaxWidth()
                                        .fillMaxHeight(),
                                ) {
                                    contentState.value(this)
                                }
                            }
                        }
                    }
                }
            }
        }
        configurePageSheet(host)
        applyClearHost(host)
        isInitialized = true
        return host
    }

    private fun buildScrollHostedSheet(): UIViewController {
        val screenHeightPt = UIScreen.mainScreen.bounds.useContents { size.height }
        val host = object : UIViewController(nibName = null, bundle = null) {
            override fun viewDidLoad() {
                super.viewDidLoad()
                view.backgroundColor = UIColor.clearColor
                view.setOpaque(false)
                view.addSubview(scrollView)
                NSLayoutConstraint.activateConstraints(
                    listOf(
                        scrollView.topAnchor.constraintEqualToAnchor(view.topAnchor),
                        scrollView.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor),
                        scrollView.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor),
                        scrollView.bottomAnchor.constraintEqualToAnchor(view.bottomAnchor),
                    ),
                )
            }
        }

        val composeVc = ComposeUIViewController {
            val scheme = schemeState.value
            val typography = typographyState.value
            MaterialTheme(colorScheme = scheme, typography = typography) {
                PlatformStyleProvider {
                    CompositionLocalProvider(
                        LocalSheetOnDismissRequest provides onDismissFromSwipe,
                        LocalSheetUsesPlatformGrabber provides true,
                        LocalSheetScrollOwnedByHost provides true,
                    ) {
                        Column(
                            modifier = modifierState.value
                                .fillMaxWidth()
                                .onSizeChanged { size ->
                                    updateComposeContentHeightPx(size.height.toDouble())
                                },
                        ) {
                            contentState.value(this)
                        }
                    }
                }
            }
        }

        val composeView = composeVc.view
        composeView.translatesAutoresizingMaskIntoConstraints = false
        composeView.backgroundColor = UIColor.clearColor
        composeView.setOpaque(false)
        composeContainer.addSubview(composeView)
        val heightConstraint = composeView.heightAnchor.constraintEqualToConstant(screenHeightPt)
        contentHeightConstraint = heightConstraint
        lastContentHeightPt = screenHeightPt
        NSLayoutConstraint.activateConstraints(
            listOf(
                composeView.topAnchor.constraintEqualToAnchor(composeContainer.topAnchor),
                composeView.leadingAnchor.constraintEqualToAnchor(composeContainer.leadingAnchor),
                composeView.trailingAnchor.constraintEqualToAnchor(composeContainer.trailingAnchor),
                composeView.bottomAnchor.constraintEqualToAnchor(composeContainer.bottomAnchor),
                heightConstraint,
            ),
        )
        retainedComposeVc = composeVc

        scrollView.addSubview(composeContainer)
        NSLayoutConstraint.activateConstraints(
            listOf(
                composeContainer.topAnchor.constraintEqualToAnchor(scrollView.topAnchor),
                composeContainer.leadingAnchor.constraintEqualToAnchor(scrollView.leadingAnchor),
                composeContainer.trailingAnchor.constraintEqualToAnchor(scrollView.trailingAnchor),
                composeContainer.bottomAnchor.constraintEqualToAnchor(scrollView.bottomAnchor),
                composeContainer.widthAnchor.constraintEqualToAnchor(scrollView.widthAnchor),
            ),
        )

        configurePageSheet(host)
        isInitialized = true
        return host
    }

    private fun updateComposeContentHeightPx(heightPx: Double) {
        if (heightPx <= 1.0) return
        val scale = UIScreen.mainScreen.scale
        val heightPt = (heightPx / scale).coerceAtLeast(1.0)
        if (abs(heightPt - lastContentHeightPt) < 0.5) return
        lastContentHeightPt = heightPt
        dispatch_async(dispatch_get_main_queue()) {
            contentHeightConstraint?.constant = heightPt
            val width = scrollView.bounds.useContents { size.width }
            if (width > 0.0) {
                scrollView.setContentSize(CGSizeMake(width, heightPt))
            }
            scrollView.layoutIfNeeded()
        }
    }

    lateinit var contentState: androidx.compose.runtime.State<@Composable ColumnScope.() -> Unit>
    lateinit var modifierState: androidx.compose.runtime.State<Modifier>

    fun syncFromParent(scheme: ColorScheme, typography: Typography) {
        schemeState.value = scheme
        typographyState.value = typography
        isChromeDark = scheme.background.luminance() < 0.5f
        applyTheme(isChromeDark)
        if (isInitialized) applyClearHost(sheetViewController)
    }

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

    fun show() {
        if (isPresented || isAnimating) return
        isAnimating = true
        applyTheme(isChromeDark)
        applyClearHost(sheetViewController)
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
        resetSurfaceDrag()
        sheetViewController.dismissViewControllerAnimated(
            flag = true,
            completion = {
                isPresented = false
                isAnimating = false
                retainedComposeVc = null
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
    expandable: Boolean,
    useUiKitScrollHost: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return

    val parent = LocalUIViewController.current
    val onDismissState = rememberUpdatedState(onDismissRequest)
    val contentState = rememberUpdatedState(content)
    val modifierState = rememberUpdatedState(modifier)
    val schemeState = remember { mutableStateOf(appColorScheme) }
    val typographyState = remember { mutableStateOf(appTypography) }

    val manager = remember(parent, expandable, useUiKitScrollHost) {
        val m = MapIosNativeSheetManager(
            parentUIViewController = parent,
            isChromeDark = appColorScheme.background.luminance() < 0.5f,
            expandable = expandable,
            useUiKitScrollHost = useUiKitScrollHost,
            onDismissFromSwipe = { onDismissState.value.invoke() },
            schemeState = schemeState,
            typographyState = typographyState,
        )
        m.contentState = contentState
        m.modifierState = modifierState
        m
    }

    SideEffect {
        manager.syncFromParent(appColorScheme, appTypography)
    }

    DisposableEffect(manager) {
        manager.show()
        onDispose { manager.hide() }
    }
}
