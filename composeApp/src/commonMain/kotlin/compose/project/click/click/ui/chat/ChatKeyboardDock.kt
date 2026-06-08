package compose.project.click.click.ui.chat

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.platform.KeyboardHeightProvider
import compose.project.click.click.platform.rememberKeyboardHeightProvider
import compose.project.click.click.ui.theme.LocalPlatformStyle
import compose.project.click.click.util.collectAsStateLifecycleAware

/** Extra visual breathing room above the composer; the composer itself is measured by the layout. */
internal val ChatComposerStripReserve = 0.dp

/**
 * Native keyboard lift split used by connection and hub chats on iOS: the timeline keeps
 * scrollable space via [timelineBottomPadding] while the composer follows the keyboard on a
 * graphics layer. Android lifts the whole thread dock via IME insets instead.
 */
data class ChatNativeKeyboardInsets(
    val composerLiftPx: Float,
    val timelineBottomPadding: Dp,
    val threadDockNativeKeyboardLiftPx: Float?,
)

private fun Int.toUIKitKeyboardEasing() = when (this) {
    0 -> CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    1 -> CubicBezierEasing(0.42f, 0f, 1f, 1f)
    2 -> CubicBezierEasing(0f, 0f, 0.58f, 1f)
    3 -> LinearEasing
    else -> CubicBezierEasing(0.17f, 0.84f, 0.44f, 1f)
}

@Composable
fun rememberChatNativeKeyboardInsets(
    keyboardHeightProvider: KeyboardHeightProvider = rememberKeyboardHeightProvider(),
): ChatNativeKeyboardInsets {
    val density = LocalDensity.current
    val platformStyle = LocalPlatformStyle.current
    val nativeKeyboardHeightPoints by keyboardHeightProvider.keyboardHeight.collectAsStateLifecycleAware()
    val nativeKeyboardDurationMillis by keyboardHeightProvider.animationDurationMillis.collectAsStateLifecycleAware()
    val nativeKeyboardAnimationCurve by keyboardHeightProvider.animationCurve.collectAsStateLifecycleAware()
    val nativeKeyboardLiftTargetPx = if (platformStyle.isIOS) {
        val navBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
        val keyboardHeightPx = with(density) { nativeKeyboardHeightPoints.dp.toPx() }
        (keyboardHeightPx - navBottomPx).coerceAtLeast(0f)
    } else {
        0f
    }
    val animatedNativeKeyboardLiftPx by animateFloatAsState(
        targetValue = nativeKeyboardLiftTargetPx,
        animationSpec = tween(
            durationMillis = nativeKeyboardDurationMillis,
            easing = nativeKeyboardAnimationCurve.toUIKitKeyboardEasing(),
        ),
        label = "native_keyboard_lift",
    )
    val composerLiftPx = if (platformStyle.isIOS) animatedNativeKeyboardLiftPx else 0f
    val timelineBottomPadding = if (platformStyle.isIOS) {
        with(density) { animatedNativeKeyboardLiftPx.toDp() }
    } else {
        0.dp
    }
    val threadDockNativeKeyboardLiftPx = if (platformStyle.isIOS) 0f else null
    return ChatNativeKeyboardInsets(
        composerLiftPx = composerLiftPx,
        timelineBottomPadding = timelineBottomPadding,
        threadDockNativeKeyboardLiftPx = threadDockNativeKeyboardLiftPx,
    )
}
