package compose.project.click.click.ui.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.platform.KeyboardHeightProvider // pragma: allowlist secret
import compose.project.click.click.platform.rememberKeyboardHeightProvider // pragma: allowlist secret
import compose.project.click.click.ui.theme.LocalPlatformStyle // pragma: allowlist secret
import compose.project.click.click.util.collectAsStateLifecycleAware // pragma: allowlist secret

/** Extra visual breathing room above the composer; the composer itself is measured by the layout. */
internal val ChatComposerStripReserve = 0.dp

/**
 * iOS keyboard lift via UIKit notifications + GPU [graphicsLayer].
 * [timelineBottomPadding] stays static so LazyColumn is not relaid out every keyboard frame.
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
    val navBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val liftPx = ((nativeKeyboardHeightPoints * density.density) - navBottomPx).coerceAtLeast(0f)

    return ChatNativeKeyboardInsets(
        composerLiftPx = liftPx,
        timelineBottomPadding = 0.dp,
        threadDockNativeKeyboardLiftPx = liftPx,
    )
}

/** Isolated lift read for toasts/chrome so the chat timeline tree does not recompose every frame. */
@Composable
fun rememberChatKeyboardLiftDp(
    keyboardHeightProvider: KeyboardHeightProvider,
): Dp {
    val density = LocalDensity.current
    val platformStyle = LocalPlatformStyle.current
    if (!platformStyle.isIOS) return 0.dp
    val nativeKeyboardHeightPoints by keyboardHeightProvider.keyboardHeight.collectAsStateLifecycleAware()
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    val imePx = WindowInsets.ime.getBottom(density)
    val trackedPx = (nativeKeyboardHeightPoints * density.density).roundToInt()
    val liftPx = (maxOf(imePx, trackedPx) - navBottomPx).coerceAtLeast(0)
    return with(density) { liftPx.toDp() }
}

