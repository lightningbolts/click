package compose.project.click.click.ui.chat

import androidx.compose.runtime.MutableFloatState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.UIKit.UIApplication
import platform.UIKit.UIView
import platform.UIKit.UIViewAnimationOptionBeginFromCurrentState
import platform.UIKit.UIViewAnimationOptionCurveEaseIn
import platform.UIKit.UIViewAnimationOptionCurveEaseInOut
import platform.UIKit.UIViewAnimationOptionCurveEaseOut
import platform.UIKit.UIViewAnimationOptionCurveLinear
import kotlin.math.abs
import kotlin.time.TimeSource

/**
 * Runs the keyboard's UIView animation on a proxy that lives in the key window (so presentation
 * sampling works), then copies presentation ty into Compose each tick. A detached proxy jumps to
 * the end value immediately — that was the teleport bug.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual class ComposerLiftAnimator actual constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val proxy = UIView(frame = CGRectMake(0.0, 0.0, 1.0, 1.0)).apply {
        setUserInteractionEnabled(false)
        alpha = 0.0
        setHidden(true)
    }
    private var sampleJob: Job? = null
    private var disposed = false
    private var attached = false

    private fun ensureAttached() {
        if (attached || disposed) return
        val root = UIApplication.sharedApplication.keyWindow
            ?: UIApplication.sharedApplication.windows.firstOrNull() as? platform.UIKit.UIWindow
            ?: return
        root.addSubview(proxy)
        attached = true
    }

    actual fun animateTo(
        liftPxState: MutableFloatState,
        targetPx: Float,
        durationMs: Int,
        curve: Int,
        density: Float,
    ) {
        if (disposed) return
        ensureAttached()
        sampleJob?.cancel()
        val durationSec = (durationMs.coerceAtLeast(0)).toDouble() / 1_000.0
        val fromPx = liftPxState.floatValue
        if (durationSec <= 0.0 || abs(targetPx - fromPx) < 0.5f) {
            snapTo(liftPxState, targetPx)
            return
        }

        val scale = density.toDouble().coerceAtLeast(1.0)
        val fromPoints = fromPx / scale
        val toPoints = targetPx / scale

        CATransaction.begin()
        CATransaction.setDisableActions(true)
        proxy.setTransform(CGAffineTransformMakeTranslation(0.0, fromPoints))
        CATransaction.commit()

        UIView.animateWithDuration(
            duration = durationSec,
            delay = 0.0,
            options = uiKitKeyboardAnimationOptions(curve),
            animations = {
                proxy.setTransform(CGAffineTransformMakeTranslation(0.0, toPoints))
            },
            completion = { _ ->
                if (!disposed) liftPxState.floatValue = targetPx
            },
        )

        // First sample NOW (same turn as the keyboard notification) — do not wait for delay.
        proxy.layer.presentationLayer()?.affineTransform()?.useContents {
            liftPxState.floatValue = (ty * scale).toFloat()
        } ?: run { liftPxState.floatValue = fromPx }

        sampleJob = scope.launch {
            val start = TimeSource.Monotonic.markNow()
            val limitMs = durationMs + 32L
            while (start.elapsedNow().inWholeMilliseconds < limitMs) {
                val presentedPoints = proxy.layer.presentationLayer()?.affineTransform()?.useContents {
                    ty
                }
                if (presentedPoints != null) {
                    liftPxState.floatValue = (presentedPoints * scale).toFloat()
                }
                delay(1L)
            }
            liftPxState.floatValue = targetPx
        }
    }

    actual fun snapTo(liftPxState: MutableFloatState, targetPx: Float) {
        sampleJob?.cancel()
        sampleJob = null
        ensureAttached()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        proxy.setTransform(CGAffineTransformMakeTranslation(0.0, 0.0))
        CATransaction.commit()
        liftPxState.floatValue = targetPx
    }

    actual fun cancel() {
        sampleJob?.cancel()
        sampleJob = null
        proxy.layer.removeAllAnimations()
    }

    actual fun dispose() {
        disposed = true
        cancel()
        if (attached) {
            proxy.removeFromSuperview()
            attached = false
        }
        scope.cancel()
    }
}

private fun uiKitKeyboardAnimationOptions(curve: Int): ULong {
    val curveOption = when (curve) {
        0 -> UIViewAnimationOptionCurveEaseInOut
        1 -> UIViewAnimationOptionCurveEaseIn
        2 -> UIViewAnimationOptionCurveEaseOut
        3 -> UIViewAnimationOptionCurveLinear
        else -> (curve.toULong() shl 16)
    }
    return curveOption or UIViewAnimationOptionBeginFromCurrentState
}
