package compose.project.click.click.ui.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.platform.KeyboardHeightProvider
import compose.project.click.click.platform.rememberKeyboardHeightProvider
import compose.project.click.click.ui.components.rememberTabBarOverlayHeight
import compose.project.click.click.ui.theme.LocalPlatformStyle

/**
 * Chat IME ownership rule:
 *
 * - Android: `WindowInsets.ime` in `chatThreadKeyboardDock`.
 * - iOS: [KeyboardHeightProvider] → [liftPxState] → graphicsLayer (never `imePadding`).
 */

internal val ChatComposerStripReserve = 0.dp

data class ChatNativeKeyboardInsets(
    val liftPxState: MutableFloatState?,
    val timelineBottomPadding: Dp = 0.dp,
)

/**
 * @param subtractTabBarOverlay When true (connections chat on iOS), lift is keyboard−tabOverlay
 * because the native bar stays visible. Hub chat hides the bar — pass false (keyboard−nav).
 */
@Composable
fun rememberChatNativeKeyboardInsets(
    keyboardHeightProvider: KeyboardHeightProvider = rememberKeyboardHeightProvider(),
    subtractTabBarOverlay: Boolean = true,
): ChatNativeKeyboardInsets {
    val density = LocalDensity.current
    val platformStyle = LocalPlatformStyle.current
    val liftPxState = remember { mutableFloatStateOf(0f) }
    val navBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val tabOverlayPx = with(density) { rememberTabBarOverlayHeight().toPx() }
    val subtractPx = if (subtractTabBarOverlay) tabOverlayPx else navBottomPx
    val densityState = rememberUpdatedState(density)
    val subtractState = rememberUpdatedState(subtractPx)

    DisposableEffect(keyboardHeightProvider, platformStyle.isIOS, subtractTabBarOverlay) {
        if (!platformStyle.isIOS) {
            liftPxState.floatValue = 0f
            return@DisposableEffect onDispose { }
        }
        val animator = ComposerLiftAnimator()
        keyboardHeightProvider.syncFromSystem()
        keyboardHeightProvider.setComposerLiftListener { heightPoints, durationMs, curve ->
            val keyboardHeightPx = with(densityState.value) { heightPoints.dp.toPx() }
            val targetPx = (keyboardHeightPx - subtractState.value).coerceAtLeast(0f)
            animator.animateTo(
                liftPxState = liftPxState,
                targetPx = targetPx,
                durationMs = durationMs,
                curve = curve,
                density = densityState.value.density,
            )
        }
        val already = keyboardHeightProvider.keyboardHeight.value
        if (already > 0f) {
            val keyboardHeightPx = with(density) { already.dp.toPx() }
            animator.snapTo(liftPxState, (keyboardHeightPx - subtractPx).coerceAtLeast(0f))
        }
        onDispose {
            keyboardHeightProvider.setComposerLiftListener(null)
            animator.dispose()
            liftPxState.floatValue = 0f
        }
    }

    return if (platformStyle.isIOS) {
        ChatNativeKeyboardInsets(liftPxState = liftPxState)
    } else {
        ChatNativeKeyboardInsets(liftPxState = null)
    }
}
