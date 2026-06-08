package compose.project.click.click.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSRunLoop
import platform.Foundation.NSValue
import platform.Foundation.NSDefaultRunLoopMode
import platform.QuartzCore.CADisplayLink
import platform.UIKit.UIApplication
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

/**
 * Tracks the **live** UIKit keyboard window each display refresh (no eased interpolation).
 * Compose IME insets are merged at the UI layer via [maxOf] so the dock never trails the keyboard.
 */
@OptIn(ExperimentalForeignApi::class)
private object IosKeyboardOverlapTracker {
    private val notificationCenter = NSNotificationCenter.defaultCenter
    private val _keyboardHeight = MutableStateFlow(0f)
    val keyboardHeight: StateFlow<Float> = _keyboardHeight.asStateFlow()

    private val displayLinkTarget = DisplayLinkTarget()
    private var displayLink: CADisplayLink? = null
    private var trackingFrames = false
    private var animationFallbackTarget = 0f
    private var started = false

    private var willChangeObserver: Any? = null
    private var willHideObserver: Any? = null
    private var didChangeObserver: Any? = null
    private var didHideObserver: Any? = null

    fun ensureStarted() {
        if (started) return
        started = true

        willChangeObserver = notificationCenter.addObserverForName(
            name = UIKeyboardWillChangeFrameNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification ->
            beginTracking(notification.keyboardOverlapHeight())
        }

        willHideObserver = notificationCenter.addObserverForName(
            name = UIKeyboardWillHideNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            beginTracking(0f)
        }

        didChangeObserver = notificationCenter.addObserverForName(
            name = UIKeyboardDidChangeFrameNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification ->
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

    private fun beginTracking(fallbackTarget: Float) {
        animationFallbackTarget = fallbackTarget
        trackingFrames = true
        val live = liveKeyboardOverlapHeightPoints()
        _keyboardHeight.value = maxOf(live, fallbackTarget)
        startDisplayLink()
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
                val live = liveKeyboardOverlapHeightPoints()
                _keyboardHeight.value = if (live > 0f) live else animationFallbackTarget
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

    @OptIn(ExperimentalForeignApi::class)
    private class DisplayLinkTarget : NSObject() {
        var onFrame: (() -> Unit)? = null

        @ObjCAction
        fun handleFrame(displayLink: CADisplayLink) {
            onFrame?.invoke()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual class KeyboardHeightProvider actual constructor() {
    private val _animationDurationMillis = MutableStateFlow(0)
    actual val animationDurationMillis: StateFlow<Int> = _animationDurationMillis.asStateFlow()

    private val _animationCurve = MutableStateFlow(0)
    actual val animationCurve: StateFlow<Int> = _animationCurve.asStateFlow()

    init {
        IosKeyboardOverlapTracker.ensureStarted()
    }

    actual val keyboardHeight: StateFlow<Float> = IosKeyboardOverlapTracker.keyboardHeight

    actual fun dispose() = Unit
}

/** App launch hook — keyboard observers must be live before the first chat composer focuses. */
fun ensureKeyboardOverlapTrackingStarted() {
    IosKeyboardOverlapTracker.ensureStarted()
}

@OptIn(ExperimentalForeignApi::class)
private fun liveKeyboardOverlapHeightPoints(): Float {
    val screenBounds = UIScreen.mainScreen.bounds.useContents { this }
    val screenHeight = screenBounds.size.height
    val screenOriginY = screenBounds.origin.y
    var overlap = 0.0

    fun consider(window: UIWindow) {
        if (window.hidden) return
        if (window.isKeyWindow()) return
        window.frame.useContents {
            val height = size.height
            val topY = origin.y
            if (height < 80.0) return
            if (topY < screenHeight * 0.2) return
            val keyboardOverlap = (screenHeight + screenOriginY - topY).coerceAtLeast(0.0)
            if (keyboardOverlap > overlap) overlap = keyboardOverlap
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
    return overlap.toFloat()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSNotification?.keyboardOverlapHeight(): Float {
    val frame = this?.userInfo?.get(UIKeyboardFrameEndUserInfoKey) as? NSValue ?: return 0f
    val screenHeight = UIScreen.mainScreen.bounds.useContents { size.height }
    val frameTop = frame.CGRectValue().useContents { origin.y }
    return (screenHeight - frameTop).coerceAtLeast(0.0).toFloat()
}
