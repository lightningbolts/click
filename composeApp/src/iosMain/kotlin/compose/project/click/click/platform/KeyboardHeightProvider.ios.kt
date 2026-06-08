package compose.project.click.click.platform

import androidx.compose.animation.core.CubicBezierEasing
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSRunLoop
import platform.Foundation.NSValue
import platform.Foundation.NSDefaultRunLoopMode
import platform.QuartzCore.CACurrentMediaTime
import platform.QuartzCore.CADisplayLink
import platform.UIKit.UIApplication
import platform.UIKit.UIKeyboardAnimationCurveUserInfoKey
import platform.UIKit.UIKeyboardAnimationDurationUserInfoKey
import platform.UIKit.UIKeyboardFrameBeginUserInfoKey
import platform.UIKit.UIKeyboardFrameEndUserInfoKey
import platform.UIKit.UIKeyboardDidChangeFrameNotification
import platform.UIKit.UIKeyboardDidHideNotification
import platform.UIKit.UIKeyboardWillChangeFrameNotification
import platform.UIKit.UIKeyboardWillHideNotification
import platform.UIKit.CGRectValue
import platform.UIKit.UIScreen
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import platform.darwin.sel_registerName
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val UIKIT_ANIMATION_CURVE_EASE_IN_OUT = 0
private const val UIKIT_ANIMATION_CURVE_KEYBOARD = 7

/** UIView.AnimationCurve.keyboard — matches ChatView keyboard dock easing. */
private val keyboardCurveEasing = CubicBezierEasing(0.17f, 0.84f, 0.44f, 1f)
private val easeInOutEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val easeInEasing = CubicBezierEasing(0.42f, 0f, 1f, 1f)
private val easeOutEasing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

@OptIn(ExperimentalForeignApi::class)
private class KeyboardHeightDisplayLinkTarget : NSObject() {
    var onFrame: (() -> Unit)? = null

    @ObjCAction
    fun handleFrame(displayLink: CADisplayLink) {
        onFrame?.invoke()
    }
}

/**
 * Tracks keyboard overlap each display refresh. Prefers the live UIKit keyboard window position
 * (pixel-locked to the system animation) and falls back to notification-timed easing only while
 * the window is not yet measurable.
 */
@OptIn(ExperimentalForeignApi::class)
private object IosKeyboardHeightTracker {
    private val notificationCenter = NSNotificationCenter.defaultCenter
    private val _keyboardHeight = MutableStateFlow(0f)
    val keyboardHeight: StateFlow<Float> = _keyboardHeight.asStateFlow()

    private val _animationDurationMillis = MutableStateFlow(0)
    val animationDurationMillis: StateFlow<Int> = _animationDurationMillis.asStateFlow()

    private val _animationCurve = MutableStateFlow(UIKIT_ANIMATION_CURVE_EASE_IN_OUT)
    val animationCurve: StateFlow<Int> = _animationCurve.asStateFlow()

    private var willChangeFrameObserver: Any? = null
    private var willHideObserver: Any? = null
    private var didChangeFrameObserver: Any? = null
    private var didHideObserver: Any? = null
    private var started = false

    private val displayLinkTarget = KeyboardHeightDisplayLinkTarget()
    private var displayLink: CADisplayLink? = null
    private var trackingFrames = false
    private var animFrom = 0f
    private var animTo = 0f
    private var animStartTime = 0.0
    private var animDurationSec = 0.0
    private var animCurve = UIKIT_ANIMATION_CURVE_KEYBOARD

    fun currentHeightPoints(): Float = _keyboardHeight.value

    fun ensureStarted() {
        if (started) return
        started = true

        willChangeFrameObserver = notificationCenter.addObserverForName(
            name = UIKeyboardWillChangeFrameNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification: NSNotification? ->
            beginTracking(notification)
        }

        willHideObserver = notificationCenter.addObserverForName(
            name = UIKeyboardWillHideNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification: NSNotification? ->
            beginTracking(notification, targetHeight = 0f)
        }

        didChangeFrameObserver = notificationCenter.addObserverForName(
            name = UIKeyboardDidChangeFrameNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification: NSNotification? ->
            val overlap = maxOf(
                notification.keyboardOverlapHeight(),
                liveKeyboardOverlapHeightPoints(),
            )
            _keyboardHeight.value = overlap
            endTracking()
        }

        didHideObserver = notificationCenter.addObserverForName(
            name = UIKeyboardDidHideNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            _keyboardHeight.value = 0f
            endTracking()
        }
    }

    private fun beginTracking(notification: NSNotification?, targetHeight: Float? = null) {
        updateAnimationMetadata(notification)
        animFrom = notification.keyboardOverlapBeginHeight()
        animTo = targetHeight ?: notification.keyboardOverlapHeight()
        animStartTime = CACurrentMediaTime()
        val durationMs = _animationDurationMillis.value.takeIf { it > 0 } ?: 250
        animDurationSec = durationMs / 1000.0
        animCurve = _animationCurve.value
        trackingFrames = true
        publishTrackedHeight()
        startDisplayLink()
    }

    private fun publishTrackedHeight() {
        val live = liveKeyboardOverlapHeightPoints()
        val interpolated = interpolatedHeight()
        _keyboardHeight.value = when {
            live > 0f -> max(live, interpolated)
            trackingFrames -> interpolated
            else -> _keyboardHeight.value
        }
    }

    private fun interpolatedHeight(): Float {
        if (!trackingFrames) return animTo
        val elapsed = CACurrentMediaTime() - animStartTime
        val linearT = (elapsed / animDurationSec).toFloat()
        if (linearT >= 1f) return animTo
        val easedT = interpolateKeyboardT(linearT.coerceIn(0f, 1f), animCurve)
        return animFrom + (animTo - animFrom) * easedT
    }

    private fun endTracking() {
        trackingFrames = false
        stopDisplayLink()
    }

    private fun startDisplayLink() {
        if (displayLink != null) return
        displayLinkTarget.onFrame = {
            if (!trackingFrames) {
                stopDisplayLink()
            } else {
                publishTrackedHeight()
            }
        }
        displayLink = CADisplayLink.displayLinkWithTarget(
            target = displayLinkTarget,
            selector = sel_registerName("handleFrame:"),
        )
        displayLink?.addToRunLoop(NSRunLoop.mainRunLoop, NSDefaultRunLoopMode)
    }

    private fun stopDisplayLink() {
        displayLink?.invalidate()
        displayLink = null
        displayLinkTarget.onFrame = null
    }

    private fun updateAnimationMetadata(notification: NSNotification?) {
        _animationDurationMillis.value = notification.animationDurationMillis()
        _animationCurve.value = notification.animationCurve()
    }
}

@OptIn(ExperimentalForeignApi::class)
actual class KeyboardHeightProvider actual constructor() {
    init {
        IosKeyboardHeightTracker.ensureStarted()
    }

    actual val keyboardHeight: StateFlow<Float> = IosKeyboardHeightTracker.keyboardHeight
    actual val animationDurationMillis: StateFlow<Int> = IosKeyboardHeightTracker.animationDurationMillis
    actual val animationCurve: StateFlow<Int> = IosKeyboardHeightTracker.animationCurve

    actual fun currentKeyboardHeightPoints(): Float = IosKeyboardHeightTracker.currentHeightPoints()

    actual fun dispose() = Unit
}

/** App launch hook — keyboard observers must be live before the first chat composer focuses. */
fun ensureKeyboardOverlapTrackingStarted() {
    IosKeyboardHeightTracker.ensureStarted()
}

private fun interpolateKeyboardT(t: Float, curve: Int): Float {
    val easing = when (curve) {
        UIKIT_ANIMATION_CURVE_KEYBOARD -> keyboardCurveEasing
        1 -> easeInEasing
        2 -> easeOutEasing
        3 -> return t
        else -> easeInOutEasing
    }
    return easing.transform(t)
}

@OptIn(ExperimentalForeignApi::class)
private fun liveKeyboardOverlapHeightPoints(): Float {
    val screenBounds = UIScreen.mainScreen.bounds.useContents { this }
    val screenTop = screenBounds.origin.y
    val screenBottom = screenTop + screenBounds.size.height
    var maxOverlap = 0.0

    fun consider(window: UIWindow) {
        if (window.isKeyWindow()) return
        window.frame.useContents {
            val frameTop = origin.y
            val frameBottom = origin.y + size.height
            if (frameBottom <= screenTop + 40.0) return
            val visibleTop = max(frameTop, screenTop)
            val visibleBottom = min(frameBottom, screenBottom)
            val overlap = (visibleBottom - visibleTop).coerceAtLeast(0.0)
            if (overlap > maxOverlap) maxOverlap = overlap
        }
    }

    val app = UIApplication.sharedApplication
    app.windows.forEach { win ->
        (win as? UIWindow)?.let { consider(it) }
    }
    app.connectedScenes.forEach { scene ->
        val windowScene = scene as? UIWindowScene ?: return@forEach
        windowScene.windows.forEach { win ->
            (win as? UIWindow)?.let { consider(it) }
        }
    }
    return maxOverlap.toFloat()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSNotification?.keyboardOverlapHeight(): Float {
    val frame = this?.userInfo?.get(UIKeyboardFrameEndUserInfoKey) as? NSValue ?: return 0f
    return frame.overlapWithScreen()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSNotification?.keyboardOverlapBeginHeight(): Float {
    val frame = this?.userInfo?.get(UIKeyboardFrameBeginUserInfoKey) as? NSValue
        ?: return keyboardOverlapHeight()
    return frame.overlapWithScreen()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSValue.overlapWithScreen(): Float {
    val screenBounds = UIScreen.mainScreen.bounds.useContents { this }
    val screenTop = screenBounds.origin.y
    val screenBottom = screenTop + screenBounds.size.height
    return CGRectValue().useContents {
        val frameTop = origin.y
        val frameBottom = origin.y + size.height
        val visibleTop = max(frameTop, screenTop)
        val visibleBottom = min(frameBottom, screenBottom)
        (visibleBottom - visibleTop).coerceAtLeast(0.0).toFloat()
    }
}

private fun NSNotification?.animationDurationMillis(): Int {
    val durationSeconds = this?.userInfo
        ?.get(UIKeyboardAnimationDurationUserInfoKey)
        .let { it as? NSNumber }
        ?.doubleValue
        ?: 0.0
    return (durationSeconds * 1_000.0).roundToInt().coerceAtLeast(0)
}

private fun NSNotification?.animationCurve(): Int {
    return this?.userInfo
        ?.get(UIKeyboardAnimationCurveUserInfoKey)
        .let { it as? NSNumber }
        ?.intValue
        ?: UIKIT_ANIMATION_CURVE_EASE_IN_OUT
}
