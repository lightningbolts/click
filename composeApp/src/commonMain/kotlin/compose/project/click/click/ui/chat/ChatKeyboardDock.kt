package compose.project.click.click.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.platform.KeyboardHeightProvider
import compose.project.click.click.platform.rememberKeyboardHeightProvider
import compose.project.click.click.ui.theme.LocalPlatformStyle
import kotlinx.coroutines.flow.combine

/**
 * Chat IME ownership rule:
 *
 * - Android chat threads use `WindowInsets.ime` only, in `chatThreadKeyboardDock`.
 * - iOS chat threads use `KeyboardHeightProvider` notifications only.
 * - Forms and sheets use `imePadding`; chat surfaces must never add it.
 *
 * iOS lift is a [MutableFloatState] driven from keyboard flows **without** collectAsState, so
 * animation starts in the same coroutine turn as the notification (no composition-frame lag).
 */

/** Extra visual breathing room above the composer; the composer itself is measured by the layout. */
internal val ChatComposerStripReserve = 0.dp

/**
 * Native keyboard lift used by connection and hub chats.
 *
 * On iOS, [liftPxState] drives a single thread-dock graphics-layer lift. [timelineBottomPadding]
 * stays 0 so LazyColumn does not relayout per keyboard frame. Android uses IME insets instead.
 */
data class ChatNativeKeyboardInsets(
    /** iOS only — read inside graphicsLayer; null on Android. */
    val liftPxState: MutableFloatState?,
    val timelineBottomPadding: Dp = 0.dp,
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
    val liftPxState = remember { mutableFloatStateOf(0f) }
    val navBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()

    LaunchedEffect(keyboardHeightProvider, platformStyle.isIOS, density, navBottomPx) {
        if (!platformStyle.isIOS) {
            liftPxState.floatValue = 0f
            return@LaunchedEffect
        }
        keyboardHeightProvider.syncFromSystem()
        val liftAnimatable = Animatable(liftPxState.floatValue)
        combine(
            keyboardHeightProvider.keyboardHeight,
            keyboardHeightProvider.animationDurationMillis,
            keyboardHeightProvider.animationCurve,
        ) { heightPoints, durationMillis, curve ->
            Triple(heightPoints, durationMillis, curve)
        }.collect { (heightPoints, durationMillis, curve) ->
            val keyboardHeightPx = with(density) { heightPoints.dp.toPx() }
            val targetPx = (keyboardHeightPx - navBottomPx).coerceAtLeast(0f)
            val duration = durationMillis.coerceAtLeast(0)
            if (duration == 0) {
                liftAnimatable.snapTo(targetPx)
                liftPxState.floatValue = targetPx
            } else {
                liftAnimatable.animateTo(
                    targetValue = targetPx,
                    animationSpec = tween(
                        durationMillis = duration,
                        easing = curve.toUIKitKeyboardEasing(),
                    ),
                ) {
                    liftPxState.floatValue = value
                }
            }
        }
    }

    return if (platformStyle.isIOS) {
        ChatNativeKeyboardInsets(liftPxState = liftPxState)
    } else {
        ChatNativeKeyboardInsets(liftPxState = null)
    }
}
