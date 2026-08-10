@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package compose.project.click.click.ui.sheet // pragma: allowlist secret

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.Alignment
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
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSValue
import platform.UIKit.CGRectValue
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIColor
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.UIKeyboardAnimationCurveUserInfoKey
import platform.UIKit.UIKeyboardAnimationDurationUserInfoKey
import platform.UIKit.UIKeyboardFrameEndUserInfoKey
import platform.UIKit.UIKeyboardWillChangeFrameNotification
import platform.UIKit.UIKeyboardWillHideNotification
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UIModalTransitionStyleCoverVertical
import platform.UIKit.UIPresentationController
import platform.UIKit.UIScreen
import platform.UIKit.UIScrollView
import platform.UIKit.UIScrollViewContentInsetAdjustmentBehavior
import platform.UIKit.UISheetPresentationControllerDetent
import platform.UIKit.UISheetPresentationControllerDetentIdentifierLarge
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
 * - **Page background**: app-themed opaque surface below the native presentation material.
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

private class MapIosNativeSheetManager(
    parentUIViewController: UIViewController,
    private var isChromeDark: Boolean,
    private var sheetColor: Color,
    private val expandable: Boolean,
    private val useUiKitScrollHost: Boolean,
    private val onDismissFromSwipe: () -> Unit,
    private val schemeState: MutableState<ColorScheme>,
    private val typographyState: MutableState<Typography>,
) {
    /** Updated via SideEffect — do not recreate the manager when keyboard changes the local VC. */
    var parentUIViewController: UIViewController = parentUIViewController
    private val delegate by lazy {
        MapIosNativeSheetDelegate(
            onDismissed = {
                isPresented = false
                isAnimating = false
                resetSurfaceDrag()
                uninstallKeyboardDetentObservers()
                popActive()
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
    private var keyboardExpandObserver: Any? = null
    private var keyboardCollapseObserver: Any? = null
    private var keyboardExpanded = false
    private var lastAppliedKeyboardOverlapPt = Float.NaN

    private companion object {
        /** Bottom of stack = root sheet; top = currently interactive sheet. */
        private val presentationStack = mutableListOf<MapIosNativeSheetManager>()

        val activeManager: MapIosNativeSheetManager?
            get() = presentationStack.lastOrNull()
    }

    private fun pushActive() {
        presentationStack.remove(this)
        presentationStack.add(this)
    }

    private fun popActive() {
        presentationStack.remove(this)
    }

    private val scrollView: UIScrollView by lazy {
        UIScrollView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            alwaysBounceVertical = true
            scrollsToTop = true
            backgroundColor = UIColor.clearColor
            setOpaque(false)
            contentInsetAdjustmentBehavior =
                // Compose applies [sheetImePadding]; Automatic double-insets leave a black
                // gap between the focused field and the keyboard (esp. at medium detent).
                UIScrollViewContentInsetAdjustmentBehavior.UIScrollViewContentInsetAdjustmentNever
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
        // Keyboard + sheet transform fight: translating while IME is up leaves a map band.
        if (compose.project.click.click.platform.currentNativeKeyboardHeightPoints() > 0.5f) {
            resetSurfaceDrag()
            return
        }
        val y = if (offsetPx <= 0.5f) 0.0 else offsetPx.toDouble()
        // Transform the *inner* content host only — never the presented page-sheet VC.
        // Translating sheetViewController.view fights UISheetPresentationController and
        // blank/flickers (event directory + drop beacon regression).
        surfaceDragContentView()?.setTransform(CGAffineTransformMakeTranslation(0.0, y))
    }

    private fun resetSurfaceDrag() {
        if (!isInitialized) return
        surfaceDragContentView()?.setTransform(CGAffineTransformMakeTranslation(0.0, 0.0))
    }

    /** Inner content host for fill-sheet surface-drag (never the presented page-sheet VC). */
    private var fillSurfaceDragView: UIView? = null

    private fun surfaceDragContentView(): UIView? = fillSurfaceDragView

    private fun sheetUIColor(): UIColor = UIColor(
        red = sheetColor.red.toDouble(),
        green = sheetColor.green.toDouble(),
        blue = sheetColor.blue.toDouble(),
        alpha = 1.0,
    )

    private fun applyThemedHost(host: UIViewController) {
        val background = sheetUIColor()
        host.view.backgroundColor = background
        host.view.setOpaque(true)
        host.view.layer.backgroundColor = background.CGColor
        // Scroll-hosted sheets otherwise leave clear UIScrollView / compose gaps that
        // read as black strips under the system grabber and below short content.
        if (useUiKitScrollHost && isInitialized) {
            scrollView.backgroundColor = background
            scrollView.setOpaque(true)
            composeContainer.backgroundColor = background
            composeContainer.setOpaque(true)
            retainedComposeVc?.view?.let { composeView ->
                composeView.backgroundColor = background
                composeView.setOpaque(true)
            }
        }
    }

    /** True when this manager's Compose parent lives inside [active]'s presented sheet. */
    private fun shouldStackOn(active: MapIosNativeSheetManager): Boolean {
        if (parentUIViewController == active.sheetViewController) return true
        active.retainedComposeVc?.let { if (parentUIViewController == it) return true }
        val activeView = active.sheetViewController.view
        var view: UIView? = parentUIViewController.view
        while (view != null) {
            if (view === activeView) return true
            view = view.superview
        }
        return false
    }

    private fun applyDefaultDetents(sheet: platform.UIKit.UISheetPresentationController) {
        sheet.setDetents(
            if (expandable) {
                listOf(
                    UISheetPresentationControllerDetent.mediumDetent(),
                    UISheetPresentationControllerDetent.largeDetent(),
                )
            } else {
                listOf(UISheetPresentationControllerDetent.mediumDetent())
            },
        )
    }

    /**
     * Seat the focused field just above the keyboard without rebuilding the detent *list*
     * (list swaps stutter). Expand to large via selectedDetentIdentifier, then apply
     * UIScrollView contentInset in sync with the keyboard animation and scroll the
     * first-responder (or content bottom) into the visible band above the keyboard.
     */
    private fun applyKeyboardBottomInset(
        overlapPoints: Float,
        durationSeconds: Double,
        animationCurve: Long,
    ) {
        if (!useUiKitScrollHost || !isInitialized) return
        val overlap = overlapPoints.toDouble().coerceAtLeast(0.0)
        if (
            !lastAppliedKeyboardOverlapPt.isNaN() &&
            abs(overlapPoints - lastAppliedKeyboardOverlapPt) < 1.5f
        ) {
            return
        }
        lastAppliedKeyboardOverlapPt = overlapPoints
        if (overlap > 0.5) {
            ensureLargeDetentForKeyboard()
        }
        val inset = UIEdgeInsetsMake(0.0, 0.0, overlap, 0.0)
        val animations = {
            scrollView.contentInset = inset
            scrollView.scrollIndicatorInsets = inset
            if (overlap > 0.5) {
                scrollFocusedContentAboveKeyboard(overlap, animated = false)
            }
        }
        if (durationSeconds > 0.001) {
            UIView.animateWithDuration(
                duration = durationSeconds,
                delay = 0.0,
                options = (animationCurve shl 16).toULong(),
                animations = animations,
                completion = { _ ->
                    if (overlap > 0.5) {
                        scrollFocusedContentAboveKeyboard(overlap, animated = true)
                    }
                },
            )
        } else {
            animations()
        }
        keyboardExpanded = overlap > 0.5
    }

    /** Prefer selecting large over replacing detents — avoids sheet rebuild flicker. */
    private fun ensureLargeDetentForKeyboard() {
        if (!expandable) return
        val sheet = sheetViewController.sheetPresentationController ?: return
        if (sheet.selectedDetentIdentifier == UISheetPresentationControllerDetentIdentifierLarge) {
            return
        }
        sheet.animateChanges {
            sheet.selectedDetentIdentifier = UISheetPresentationControllerDetentIdentifierLarge
        }
    }

    private fun scrollFocusedContentAboveKeyboard(keyboardOverlapPt: Double, animated: Boolean) {
        val padding = 16.0
        val visibleH = scrollView.bounds.useContents { size.height } - keyboardOverlapPt
        if (visibleH <= padding) return
        val currentY = scrollView.contentOffset.useContents { y }
        val responder = findFirstResponder(sheetViewController.view)
        val targetY = if (responder != null) {
            val frame = responder.convertRect(responder.bounds, toView = scrollView)
            val responderBottom = frame.useContents { origin.y + size.height }
            val responderTop = frame.useContents { origin.y }
            when {
                responderBottom > currentY + visibleH - padding ->
                    (responderBottom - visibleH + padding).coerceAtLeast(0.0)
                responderTop < currentY + padding ->
                    (responderTop - padding).coerceAtLeast(0.0)
                else -> return
            }
        } else {
            // Compose may not expose a UIKit first responder — nudge so bottom fields clear IME.
            val contentH = scrollView.contentSize.useContents { height }
            val maxOffset = (contentH + keyboardOverlapPt - scrollView.bounds.useContents { size.height })
                .coerceAtLeast(0.0)
            (currentY + keyboardOverlapPt * 0.35).coerceIn(0.0, maxOffset)
        }
        if (abs(targetY - currentY) < 1.0) return
        scrollView.setContentOffset(CGPointMake(0.0, targetY), animated = animated)
    }

    private fun findFirstResponder(view: UIView): UIView? {
        if (view.isFirstResponder) return view
        val subviews = view.subviews
        for (i in 0 until subviews.size.toInt()) {
            val child = subviews[i] as? UIView ?: continue
            findFirstResponder(child)?.let { return it }
        }
        return null
    }

    private fun installKeyboardDetentObservers() {
        if (!useUiKitScrollHost || keyboardExpandObserver != null) return
        val center = NSNotificationCenter.defaultCenter
        keyboardExpandObserver = center.addObserverForName(
            name = UIKeyboardWillChangeFrameNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification: NSNotification? ->
            val overlap = notification.sheetKeyboardOverlapPoints()
            applyKeyboardBottomInset(
                overlapPoints = overlap,
                durationSeconds = notification.sheetKeyboardDurationSeconds(),
                animationCurve = notification.sheetKeyboardAnimationCurve(),
            )
        }
        keyboardCollapseObserver = center.addObserverForName(
            name = UIKeyboardWillHideNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification: NSNotification? ->
            applyKeyboardBottomInset(
                overlapPoints = 0f,
                durationSeconds = notification.sheetKeyboardDurationSeconds(),
                animationCurve = notification.sheetKeyboardAnimationCurve(),
            )
        }
    }

    private fun uninstallKeyboardDetentObservers() {
        val center = NSNotificationCenter.defaultCenter
        keyboardExpandObserver?.let { center.removeObserver(it) }
        keyboardCollapseObserver?.let { center.removeObserver(it) }
        keyboardExpandObserver = null
        keyboardCollapseObserver = null
        lastAppliedKeyboardOverlapPt = Float.NaN
        if (isInitialized && useUiKitScrollHost) {
            applyKeyboardBottomInset(0f, durationSeconds = 0.0, animationCurve = 0L)
        }
        keyboardExpanded = false
    }

    private fun configurePageSheet(host: UIViewController) {
        host.modalPresentationStyle = UIModalPresentationPageSheet
        host.modalTransitionStyle = UIModalTransitionStyleCoverVertical
        host.presentationController?.delegate = delegate
        val sheet = host.sheetPresentationController
        sheet?.let { applyDefaultDetents(it) }
        // Always show the system grabber — same path as action sheets. Compose surface-drag
        // transforms fought UISheetPresentationController and flickered on profile/drop dismiss.
        sheet?.prefersGrabberVisible = true
        // Expand-to-large on scroll edge for UIKit hosts; Compose-owned scroll still has
        // medium+large detents when [expandable] so users can drag to full height.
        sheet?.prefersScrollingExpandsWhenScrolledToEdge = expandable && useUiKitScrollHost
        applyThemedHost(host)
    }

    private fun buildFillSheet(): UIViewController {
        val background = sheetUIColor()
        // Outer host is what UISheetPresentationController owns. Inner content view is what
        // we translate for swipe-dismiss — avoids blank/flicker from fighting the sheet chrome.
        val host = object : UIViewController(nibName = null, bundle = null) {
            override fun viewDidLoad() {
                super.viewDidLoad()
                view.backgroundColor = background
                view.setOpaque(true)
            }
        }
        val contentHost = UIView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            backgroundColor = background
            setOpaque(true)
        }
        fillSurfaceDragView = contentHost
        host.view.addSubview(contentHost)
        NSLayoutConstraint.activateConstraints(
            listOf(
                contentHost.topAnchor.constraintEqualToAnchor(host.view.topAnchor),
                contentHost.leadingAnchor.constraintEqualToAnchor(host.view.leadingAnchor),
                contentHost.trailingAnchor.constraintEqualToAnchor(host.view.trailingAnchor),
                contentHost.bottomAnchor.constraintEqualToAnchor(host.view.bottomAnchor),
            ),
        )

        val composeVc = ComposeUIViewController {
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
                                    .fillMaxSize(),
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
        retainedComposeVc = composeVc
        val composeView = composeVc.view
        composeView.translatesAutoresizingMaskIntoConstraints = false
        composeView.backgroundColor = background
        composeView.setOpaque(true)
        contentHost.addSubview(composeView)
        NSLayoutConstraint.activateConstraints(
            listOf(
                composeView.topAnchor.constraintEqualToAnchor(contentHost.topAnchor),
                composeView.leadingAnchor.constraintEqualToAnchor(contentHost.leadingAnchor),
                composeView.trailingAnchor.constraintEqualToAnchor(contentHost.trailingAnchor),
                composeView.bottomAnchor.constraintEqualToAnchor(contentHost.bottomAnchor),
            ),
        )

        configurePageSheet(host)
        applyThemedHost(host)
        isInitialized = true
        return host
    }

    private fun buildScrollHostedSheet(): UIViewController {
        val screenHeightPt = UIScreen.mainScreen.bounds.useContents { size.height }
        val initialBackground = sheetUIColor()
        val host = object : UIViewController(nibName = null, bundle = null) {
            override fun viewDidLoad() {
                super.viewDidLoad()
                view.backgroundColor = initialBackground
                view.setOpaque(true)
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
        scrollView.backgroundColor = initialBackground
        scrollView.setOpaque(true)
        composeContainer.backgroundColor = initialBackground
        composeContainer.setOpaque(true)

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
                                // Parent UIView is initially screen-sized. Without unbounded
                                // wrap, Compose clamps to that height and UIScrollView never
                                // learns the true content size — Join Event Route / footers
                                // clip with nowhere to scroll.
                                .wrapContentHeight(align = Alignment.Top, unbounded = true)
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
        composeView.backgroundColor = initialBackground
        composeView.setOpaque(true)
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
        installKeyboardDetentObservers()
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

    fun syncFromParent(scheme: ColorScheme, typography: Typography, containerColor: Color) {
        schemeState.value = scheme
        typographyState.value = typography
        sheetColor = containerColor
        isChromeDark = scheme.background.luminance() < 0.5f
        applyTheme(isChromeDark)
        if (isInitialized) applyThemedHost(sheetViewController)
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

    private fun presentationRoot(): UIViewController {
        var root = parentUIViewController
        while (root.presentingViewController != null) {
            root = root.presentingViewController ?: break
        }
        return root
    }

    private fun presentFrom(host: UIViewController) {
        applyTheme(isChromeDark)
        applyThemedHost(sheetViewController)
        host.presentViewController(
            viewControllerToPresent = sheetViewController,
            animated = true,
            completion = {
                isPresented = true
                isAnimating = false
                pushActive()
            },
        )
    }

    private fun dismissForReplacement(onComplete: () -> Unit) {
        if (!isPresented || isAnimating) {
            onComplete()
            return
        }
        isAnimating = true
        resetSurfaceDrag()
        uninstallKeyboardDetentObservers()
        sheetViewController.dismissViewControllerAnimated(
            flag = true,
            completion = {
                isPresented = false
                isAnimating = false
                retainedComposeVc = null
                popActive()
                onDismissFromSwipe()
                onComplete()
            },
        )
    }

    fun show() {
        if (isPresented || isAnimating) return
        resetSurfaceDrag()
        isAnimating = true
        val active = activeManager
        if (active != null && active !== this) {
            // Directory / share / profile sheets composed inside an open sheet must stack
            // on that sheet. Replacing the parent destroys nested Compose state (Directory
            // never opens; create-click footers vanish mid-transition).
            if (shouldStackOn(active)) {
                presentFrom(active.sheetViewController)
                return
            }
            active.dismissForReplacement {
                presentFrom(presentationRoot())
            }
            return
        }
        val root = presentationRoot()
        val alreadyPresented = root.presentedViewController
        // Never dismiss an unrelated presented VC just because LocalUIViewController
        // changed (keyboard focus) — that looked like "exited to Home".
        if (alreadyPresented != null && alreadyPresented != sheetViewController) {
            presentFrom(alreadyPresented)
        } else {
            presentFrom(root)
        }
    }

    fun hide() {
        if (!isPresented || isAnimating) return
        isAnimating = true
        resetSurfaceDrag()
        uninstallKeyboardDetentObservers()
        sheetViewController.dismissViewControllerAnimated(
            flag = true,
            completion = {
                isPresented = false
                isAnimating = false
                retainedComposeVc = null
                popActive()
            },
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSNotification?.sheetKeyboardOverlapPoints(): Float {
    val frame = this?.userInfo?.get(UIKeyboardFrameEndUserInfoKey) as? NSValue ?: return 0f
    val screenHeight = UIScreen.mainScreen.bounds.useContents { size.height }
    val (frameTop, frameBottom) = frame.CGRectValue().useContents {
        origin.y to (origin.y + size.height)
    }
    val touchesBottomEdge = frameBottom >= screenHeight - 1.0
    return if (touchesBottomEdge) {
        (screenHeight - frameTop).coerceAtLeast(0.0).toFloat()
    } else {
        0f
    }
}

private fun NSNotification?.sheetKeyboardDurationSeconds(): Double {
    return this?.userInfo
        ?.get(UIKeyboardAnimationDurationUserInfoKey)
        .let { it as? NSNumber }
        ?.doubleValue
        ?: 0.0
}

private fun NSNotification?.sheetKeyboardAnimationCurve(): Long {
    return this?.userInfo
        ?.get(UIKeyboardAnimationCurveUserInfoKey)
        .let { it as? NSNumber }
        ?.longValue
        ?: 0L
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

    // Key only on sheet mode — keyboard focus can change LocalUIViewController and must
    // not tear down / re-present the native sheet (create-group search → Home).
    val manager = remember(expandable, useUiKitScrollHost) {
        val m = MapIosNativeSheetManager(
            parentUIViewController = parent,
            isChromeDark = appColorScheme.background.luminance() < 0.5f,
            sheetColor = containerColor,
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
        manager.parentUIViewController = parent
        manager.syncFromParent(appColorScheme, appTypography, containerColor)
    }

    DisposableEffect(manager) {
        manager.show()
        onDispose { manager.hide() }
    }
}
