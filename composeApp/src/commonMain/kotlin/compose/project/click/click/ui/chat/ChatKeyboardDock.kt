package compose.project.click.click.ui.chat

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.platform.KeyboardHeightProvider
import compose.project.click.click.platform.rememberKeyboardHeightProvider
import compose.project.click.click.ui.theme.LocalPlatformStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.TimeSource

/**
 * Chat IME ownership rule:
 *
 * - Android chat threads use `WindowInsets.ime` only, in `chatThreadKeyboardDock`.
 * - iOS chat threads use [KeyboardHeightProvider] notifications only.
 * - Forms and sheets use `imePadding`; chat surfaces must never add it.
 *
 * iOS lift is driven by [KeyboardHeightProvider.setComposerLiftListener] on the main queue
 * with [Dispatchers.Main.immediate], so the tween starts in the same turn as UIKit's keyboard
 * animation (no StateFlow → LaunchedEffect → collect lag).
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

private fun Int.toUIKitKeyboardEasing(): Easing = when (this) {
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
    val densityState = rememberUpdatedState(density)
    val navBottomState = rememberUpdatedState(navBottomPx)

    DisposableEffect(keyboardHeightProvider, platformStyle.isIOS) {
        if (!platformStyle.isIOS) {
            liftPxState.floatValue = 0f
            return@DisposableEffect onDispose { }
        }
        keyboardHeightProvider.syncFromSystem()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var animJob: Job? = null
        keyboardHeightProvider.setComposerLiftListener { heightPoints, durationMillis, curve ->
            val keyboardHeightPx = with(densityState.value) { heightPoints.dp.toPx() }
            val targetPx = (keyboardHeightPx - navBottomState.value).coerceAtLeast(0f)
            val duration = durationMillis.coerceAtLeast(0)
            val fromPx = liftPxState.floatValue
            animJob?.cancel()
            if (duration == 0 || abs(targetPx - fromPx) < 0.5f) {
                liftPxState.floatValue = targetPx
                return@setComposerLiftListener
            }
            val easing = curve.toUIKitKeyboardEasing()
            // Main.immediate: first iteration runs now (same run-loop turn as UIKit).
            animJob = scope.launch {
                val startMark = TimeSource.Monotonic.markNow()
                while (true) {
                    val elapsed = startMark.elapsedNow().inWholeMilliseconds.toFloat()
                    val t = (elapsed / duration.toFloat()).coerceIn(0f, 1f)
                    liftPxState.floatValue = fromPx + (targetPx - fromPx) * easing.transform(t)
                    if (t >= 1f) break
                    withFrameMillis { }
                }
            }
        }
        // Rehydrate if keyboard is already up when the chat mounts.
        val already = keyboardHeightProvider.keyboardHeight.value
        if (already > 0f) {
            val keyboardHeightPx = with(density) { already.dp.toPx() }
            liftPxState.floatValue = (keyboardHeightPx - navBottomPx).coerceAtLeast(0f)
        }
        onDispose {
            keyboardHeightProvider.setComposerLiftListener(null)
            animJob?.cancel()
            scope.cancel()
            liftPxState.floatValue = 0f
        }
    }

    return if (platformStyle.isIOS) {
        ChatNativeKeyboardInsets(liftPxState = liftPxState)
    } else {
        ChatNativeKeyboardInsets(liftPxState = null)
    }
}
