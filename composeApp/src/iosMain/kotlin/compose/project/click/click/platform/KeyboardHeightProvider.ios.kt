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
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIKeyboardAnimationCurveUserInfoKey
import platform.UIKit.UIKeyboardAnimationDurationUserInfoKey
import platform.UIKit.UIKeyboardDidHideNotification
import platform.UIKit.UIKeyboardFrameEndUserInfoKey
import platform.UIKit.UIKeyboardWillChangeFrameNotification
import platform.UIKit.UIKeyboardWillHideNotification
import platform.UIKit.CGRectValue
import platform.UIKit.UIScreen
import kotlin.math.roundToInt

/*
 * UIKit does not expose a synchronous "current keyboard frame" query. Keep the latest observed
 * native state process-wide so a provider created during focus transfer can rehydrate even when
 * UIKeyboardWillChangeFrame was delivered to the outgoing screen. Access is main-queue-only:
 * observers are registered on mainQueue and syncFromSystem is called by Compose on the UI thread.
 */
private var latestKeyboardHeight = 0f
private var latestAnimationDurationMillis = 0
private var latestAnimationCurve = UIKIT_ANIMATION_CURVE_EASE_IN_OUT

@OptIn(ExperimentalForeignApi::class)
actual class KeyboardHeightProvider actual constructor() {
    private val notificationCenter = NSNotificationCenter.defaultCenter
    private val _keyboardHeight = MutableStateFlow(latestKeyboardHeight)
    actual val keyboardHeight: StateFlow<Float> = _keyboardHeight.asStateFlow()

    private val _animationDurationMillis = MutableStateFlow(latestAnimationDurationMillis)
    actual val animationDurationMillis: StateFlow<Int> = _animationDurationMillis.asStateFlow()

    private val _animationCurve = MutableStateFlow(latestAnimationCurve)
    actual val animationCurve: StateFlow<Int> = _animationCurve.asStateFlow()

    private var willChangeFrameObserver: Any? = null
    private var willHideObserver: Any? = null
    private var didHideObserver: Any? = null
    private var didEnterBackgroundObserver: Any? = null
    private var disposed = false

    init {
        willChangeFrameObserver = notificationCenter.addObserverForName(
            name = UIKeyboardWillChangeFrameNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification: NSNotification? ->
            notification.keyboardOverlapHeight()?.let { overlap ->
                updateAnimation(notification)
                updateHeight(overlap)
            }
        }

        willHideObserver = notificationCenter.addObserverForName(
            name = UIKeyboardWillHideNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification: NSNotification? ->
            updateAnimation(notification)
            updateHeight(0f)
        }

        // A rapid responder change can interrupt a will-hide transition. Did-hide is the
        // authoritative terminal state and prevents a partially animated residual lift.
        didHideObserver = notificationCenter.addObserverForName(
            name = UIKeyboardDidHideNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) {
            updateHeight(0f)
        }

        // UIKit removes the keyboard while the scene is backgrounded without guaranteeing the
        // matching frame/hide notification. Clear the cached state before a call or multitask
        // return so the first resumed frame cannot inherit a stale gap.
        didEnterBackgroundObserver = notificationCenter.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) {
            latestAnimationDurationMillis = 0
            _animationDurationMillis.value = 0
            updateHeight(0f)
        }
    }

    actual fun syncFromSystem() {
        _animationDurationMillis.value = latestAnimationDurationMillis
        _animationCurve.value = latestAnimationCurve
        _keyboardHeight.value = latestKeyboardHeight
    }

    actual fun dispose() {
        if (disposed) return
        willChangeFrameObserver?.let { notificationCenter.removeObserver(it) }
        willChangeFrameObserver = null
        willHideObserver?.let { notificationCenter.removeObserver(it) }
        willHideObserver = null
        didHideObserver?.let { notificationCenter.removeObserver(it) }
        didHideObserver = null
        didEnterBackgroundObserver?.let { notificationCenter.removeObserver(it) }
        didEnterBackgroundObserver = null
        disposed = true
    }

    private fun updateAnimation(notification: NSNotification?) {
        latestAnimationDurationMillis = notification.animationDurationMillis()
        latestAnimationCurve = notification.animationCurve()
        _animationDurationMillis.value = latestAnimationDurationMillis
        _animationCurve.value = latestAnimationCurve
    }

    private fun updateHeight(height: Float) {
        latestKeyboardHeight = height.coerceAtLeast(0f)
        _keyboardHeight.value = latestKeyboardHeight
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSNotification?.keyboardOverlapHeight(): Float? {
    val frame = this?.userInfo?.get(UIKeyboardFrameEndUserInfoKey) as? NSValue ?: return null
    val screenHeight = UIScreen.mainScreen.bounds.useContents { size.height }
    val (frameTop, frameBottom) = frame.CGRectValue().useContents {
        origin.y to (origin.y + size.height)
    }

    // Floating/undocked keyboards do not occlude the bottom composer. Treating their frame top
    // as a bottom inset creates a large false gap. A one-point tolerance absorbs coordinate
    // rounding at the docked screen edge.
    val touchesBottomEdge = frameBottom >= screenHeight - 1.0
    return if (touchesBottomEdge) {
        (screenHeight - frameTop).coerceAtLeast(0.0).toFloat()
    } else {
        0f
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

private const val UIKIT_ANIMATION_CURVE_EASE_IN_OUT = 0
