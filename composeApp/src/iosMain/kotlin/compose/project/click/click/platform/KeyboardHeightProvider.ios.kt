package compose.project.click.click.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSValue
import platform.UIKit.UIKeyboardAnimationCurveUserInfoKey
import platform.UIKit.UIKeyboardAnimationDurationUserInfoKey
import platform.UIKit.UIKeyboardFrameEndUserInfoKey
import platform.UIKit.UIKeyboardWillChangeFrameNotification
import platform.UIKit.UIKeyboardWillHideNotification
import platform.UIKit.CGRectValue
import platform.UIKit.UIScreen
import kotlin.math.roundToInt

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
    private var disposed = false

    init {
        willChangeFrameObserver = notificationCenter.addObserverForName(
            name = UIKeyboardWillChangeFrameNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification: NSNotification? ->
            updateAnimation(notification)
            _keyboardHeight.value = notification.keyboardOverlapHeight()
        }

        willHideObserver = notificationCenter.addObserverForName(
            name = UIKeyboardWillHideNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification: NSNotification? ->
            updateAnimation(notification)
            _keyboardHeight.value = 0f
        }
    }

    actual fun dispose() {
        if (disposed) return
        willChangeFrameObserver?.let { notificationCenter.removeObserver(it) }
        willChangeFrameObserver = null
        willHideObserver?.let { notificationCenter.removeObserver(it) }
        willHideObserver = null
        disposed = true
    }

    private fun updateAnimation(notification: NSNotification?) {
        _animationDurationMillis.value = notification.animationDurationMillis()
        _animationCurve.value = notification.animationCurve()
    }
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

private const val UIKIT_ANIMATION_CURVE_EASE_IN_OUT = 0
