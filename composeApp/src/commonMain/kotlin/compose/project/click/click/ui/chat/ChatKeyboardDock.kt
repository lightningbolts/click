package compose.project.click.click.ui.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.LocalPlatformStyle

/** Extra visual breathing room above the composer; the composer itself is measured by the layout. */
internal val ChatComposerStripReserve = 0.dp

/**
 * iOS keyboard lift via native [WindowInsets.ime] + GPU [graphicsLayer].
 * [timelineBottomPadding] stays static so LazyColumn is not relaid out every keyboard frame.
 */
data class ChatNativeKeyboardInsets(
    val composerLiftPx: Float,
    val timelineBottomPadding: Dp,
    val threadDockNativeKeyboardLiftPx: Float?,
)

@Composable
fun rememberChatNativeKeyboardInsets(): ChatNativeKeyboardInsets {
    val density = LocalDensity.current
    val platformStyle = LocalPlatformStyle.current
    if (!platformStyle.isIOS) {
        return ChatNativeKeyboardInsets(
            composerLiftPx = 0f,
            timelineBottomPadding = 0.dp,
            threadDockNativeKeyboardLiftPx = null,
        )
    }

    val imePx = WindowInsets.ime.getBottom(density).toFloat()
    val navBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val liftPx = (imePx - navBottomPx).coerceAtLeast(0f)

    return ChatNativeKeyboardInsets(
        composerLiftPx = liftPx,
        timelineBottomPadding = 0.dp,
        threadDockNativeKeyboardLiftPx = liftPx,
    )
}

/** Isolated lift read for toasts/chrome so the chat timeline tree does not recompose every frame. */
@Composable
fun rememberChatKeyboardLiftDp(): Dp {
    val density = LocalDensity.current
    val platformStyle = LocalPlatformStyle.current
    if (!platformStyle.isIOS) return 0.dp
    val imePx = WindowInsets.ime.getBottom(density).toFloat()
    val navBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val liftPx = (imePx - navBottomPx).coerceAtLeast(0f)
    return with(density) { liftPx.toDp() }
}

/** GPU translate for composer / timeline — no layout reflow. */
fun Modifier.chatNativeGpuLift(liftPx: Float): Modifier = composed {
    if (!LocalPlatformStyle.current.isIOS) return@composed this
    graphicsLayer {
        translationY = -liftPx.coerceAtLeast(0f)
    }
}
