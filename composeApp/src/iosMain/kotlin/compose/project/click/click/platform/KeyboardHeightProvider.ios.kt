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
import platform.UIKit.UIKeyboardAnimationCurveUserInfoKey
import platform.UIKit.UIKeyboardAnimationDurationUserInfoKey
import platform.UIKit.UIKeyboardFrameEndUserInfoKey
import platform.UIKit.UIKeyboardDidChangeFrameNotification
import platform.UIKit.UIKeyboardDidHideNotification
import platform.UIKit.UIKeyboardWillChangeFrameNotification
import platform.UIKit.UIKeyboardWillHideNotification
import platform.UIKit.CGRectValue
import platform.UIKit.UIScreen
import platform.darwin.NSObject
import platform.darwin.sel_registerName
import kotlin.math.roundToInt

private const val UIKIT_ANIMATION_CURVE_EASE_IN_OUT = 0
private const val UIKIT_ANIMATION_CURVE_KEYBOARD = 7

/** UIKit keyboard curve (UIView.AnimationCurve.keyboard). */
private val keyboardCurveEasing = CubicBezierEasing(0.17f, 0.59f, 0.27f, 0.77f)
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

@OptIn(ExperimentalForeignApi::class)
actual class KeyboardHeightProvider actual constructor() {
    private val notificationCenter = NSNotificationCenter.defaultCenter
    private val _keyboardHeight = MutableStateFlow(0f)
    actual val keyboardHeight: StateFlow<Float> = _keyboardHeight.asStateFlow()

    private val _animationDurationMillis = MutableStateFlow(0)
    actual val animationDurationMillis: StateFlow<Int> = _animationDurationMillis.asStateFlow()

    private val _animationCurve = MutableStateFlow(UIKIT_ANIMATION_CURVE_EASE_IN_OUT)
    actual val animationCurve: StateFlow<Int> = _animationCurve.asStateFlow()

    private var willChangeFrameObserver: Any? = null
    private var willHideObserver: Any? = null
    private var didChangeFrameObserver: Any? = null
    private var didHideObserver: Any? = null
    private var disposed = false

    private val displayLinkTarget = KeyboardHeightDisplayLinkTarget()
    private var displayLink: CADisplayLink? = null
    private var animFrom = 0f
    private var animTo = 0f
    private var animStartTime = 0.0
    private var animDurationSec = 0.0
    private var animCurve = UIKIT_ANIMATION_CURVE_KEYBOARD

    init {
        willChangeFrameObserver = notificationCenter.addObserverForName(
            name = UIKeyboardWillChangeFrameNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification: NSNotification? ->
            updateAnimationMetadata(notification)
            animateHeightTo(notification.keyboardOverlapHeight())
        }

        willHideObserver = notificationCenter.addObserverForName(
            name = UIKeyboardWillHideNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification: NSNotification? ->
            updateAnimationMetadata(notification)
            animateHeightTo(0f)
        }

        didChangeFrameObserver = notificationCenter.addObserverForName(
            name = UIKeyboardDidChangeFrameNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification: NSNotification? ->
            stopDisplayLink()
            _keyboardHeight.value = notification.keyboardOverlapHeight()
        }

        didHideObserver = notificationCenter.addObserverForName(
            name = UIKeyboardDidHideNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            stopDisplayLink()
            _keyboardHeight.value = 0f
        }
    }

    actual fun dispose() {
        if (disposed) return
        stopDisplayLink()
        willChangeFrameObserver?.let { notificationCenter.removeObserver(it) }
        willChangeFrameObserver = null
        willHideObserver?.let { notificationCenter.removeObserver(it) }
        willHideObserver = null
        didChangeFrameObserver?.let { notificationCenter.removeObserver(it) }
        didChangeFrameObserver = null
        didHideObserver?.let { notificationCenter.removeObserver(it) }
        didHideObserver = null
        disposed = true
    }

    private fun updateAnimationMetadata(notification: NSNotification?) {
        _animationDurationMillis.value = notification.animationDurationMillis()
        _animationCurve.value = notification.animationCurve()
    }

    /**
     * UIKit-matched keyboard lift using CADisplayLink (ProMotion-safe) and notification timing.
     */
    private fun animateHeightTo(targetHeight: Float) {
        val from = _keyboardHeight.value
        if (from == targetHeight) return

        val durationMs = _animationDurationMillis.value.takeIf { it > 0 } ?: 250
        val curve = _animationCurve.value
        if (durationMs <= 0) {
            stopDisplayLink()
            _keyboardHeight.value = targetHeight
            return
        }

        stopDisplayLink()
        animFrom = from
        animTo = targetHeight
        animStartTime = CACurrentMediaTime()
        animDurationSec = durationMs / 1000.0
        animCurve = curve

        displayLinkTarget.onFrame = {
            val elapsed = CACurrentMediaTime() - animStartTime
            val linearT = (elapsed / animDurationSec).toFloat()
            if (linearT >= 1f) {
                _keyboardHeight.value = animTo
                stopDisplayLink()
            } else {
                val easedT = interpolateKeyboardT(linearT.coerceIn(0f, 1f), animCurve)
                _keyboardHeight.value = animFrom + (animTo - animFrom) * easedT
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
private fun NSNotification?.keyboardOverlapHeight(): Float {
    val frame = this?.userInfo?.get(UIKeyboardFrameEndUserInfoKey) as? NSValue ?: return 0f
    val screenHeight = UIScreen.mainScreen.bounds.useContents { size.height }
    val frameTop = frame.CGRectValue().useContents { origin.y }
    return (screenHeight - frameTop).coerceAtLeast(0.0).toFloat()
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
