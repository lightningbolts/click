package compose.project.click.click.ui.chat

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.platform.KeyboardHeightProvider // pragma: allowlist secret
import compose.project.click.click.platform.rememberKeyboardHeightProvider // pragma: allowlist secret
import compose.project.click.click.ui.theme.LocalPlatformStyle // pragma: allowlist secret
import compose.project.click.click.util.collectAsStateLifecycleAware // pragma: allowlist secret

private const val UIKIT_ANIMATION_CURVE_KEYBOARD = 7

private fun Int.toUIKitKeyboardEasing() = when (this) {
    0 -> CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    1 -> CubicBezierEasing(0.42f, 0f, 1f, 1f)
    2 -> CubicBezierEasing(0f, 0f, 0.58f, 1f)
    3 -> androidx.compose.animation.core.LinearEasing
    UIKIT_ANIMATION_CURVE_KEYBOARD -> CubicBezierEasing(0.17f, 0.84f, 0.44f, 1f)
    else -> CubicBezierEasing(0.17f, 0.84f, 0.44f, 1f)
}

/** Extra visual breathing room above the composer; the composer itself is measured by the layout. */
internal val ChatComposerStripReserve = 0.dp

/**
 * iOS keyboard lift: timeline gets [timelineBottomPadding]; only the composer strip is translated
 * via [composerLiftPx]. The thread dock itself stays put ([threadDockNativeKeyboardLiftPx] = 0).
 */
data class ChatNativeKeyboardInsets(
    val composerLiftPx: Float,
    val timelineBottomPadding: Dp,
    val threadDockNativeKeyboardLiftPx: Float?,
)

@Composable
fun rememberChatNativeKeyboardInsets(
    keyboardHeightProvider: KeyboardHeightProvider = rememberKeyboardHeightProvider(),
): ChatNativeKeyboardInsets {
    val density = LocalDensity.current
    val platformStyle = LocalPlatformStyle.current
    if (!platformStyle.isIOS) {
        return ChatNativeKeyboardInsets(
            composerLiftPx = 0f,
            timelineBottomPadding = 0.dp,
            threadDockNativeKeyboardLiftPx = null,
        )
    }

    val nativeKeyboardHeightPoints by keyboardHeightProvider.keyboardHeight.collectAsStateLifecycleAware()
    val nativeKeyboardDurationMillis by keyboardHeightProvider.animationDurationMillis.collectAsStateLifecycleAware()
    val nativeKeyboardAnimationCurve by keyboardHeightProvider.animationCurve.collectAsStateLifecycleAware()

    val navBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val keyboardHeightPx = with(density) { nativeKeyboardHeightPoints.dp.toPx() }
    val targetLiftPx = (keyboardHeightPx - navBottomPx).coerceAtLeast(0f)

    // Single UIKit-timed transition — composer only; never stack IME offset on the whole thread.
    val transition = updateTransition(targetState = targetLiftPx, label = "chat_native_keyboard_lift")
    val animatedLiftPx by transition.animateFloat(
        transitionSpec = {
            val duration = nativeKeyboardDurationMillis.takeIf { it > 0 } ?: 250
            if (duration <= 0) {
                snap()
            } else {
                tween(
                    durationMillis = duration.coerceIn(1, 600),
                    easing = nativeKeyboardAnimationCurve.toUIKitKeyboardEasing(),
                )
            }
        },
        label = "lift_px",
    ) { value -> value }

    return ChatNativeKeyboardInsets(
        composerLiftPx = animatedLiftPx,
        timelineBottomPadding = with(density) { animatedLiftPx.toDp() },
        threadDockNativeKeyboardLiftPx = 0f,
    )
}
