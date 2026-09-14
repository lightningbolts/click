package compose.project.click.click.ui.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.MutableFloatState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.components.rememberTabBarOverlayHeight
import compose.project.click.click.ui.theme.LocalPlatformStyle

/**
 * Keyboard motion for chat is deliberately split into two consumers:
 *
 * 1. The composer/accessory dock always follows the keyboard top.
 * 2. The message timeline follows only while the user is pinned to the newest message.
 *
 * This prevents the old "rigid slab" behavior where the header, history viewport, timeline, and
 * composer all appeared to be pushed upward together. A user reading history keeps the same
 * visible message while the keyboard changes; a user at latest keeps the newest message visually
 * attached to the composer.
 *
 * Neither path uses `imePadding`: iOS consumes the native keyboard animation sampled by
 * [rememberChatNativeKeyboardInsets], while Android reads animated IME placement in the modifier
 * placement phase. That keeps keyboard frames out of the message composition path.
 */

internal fun chatTimelineShouldFollowKeyboard(
    firstVisibleItemIndex: Int,
    initialTimelineScrollDone: Boolean,
    userScrollInProgress: Boolean,
): Boolean =
    initialTimelineScrollDone &&
        firstVisibleItemIndex == 0 &&
        !userScrollInProgress

internal fun effectiveChatKeyboardLiftPx(
    imeBottomPx: Int,
    navigationBottomPx: Int,
): Int = (imeBottomPx - navigationBottomPx).coerceAtLeast(0)

/**
 * Moves only the message viewport while it is pinned to latest. When the user is reading history,
 * the viewport stays in place and the keyboard simply occludes its lower region as the composer
 * moves above the IME.
 *
 * [followKeyboard] is evaluated from the graphics/placement phase so scroll position changes do
 * not force the chat subtree to recompose on every keyboard frame.
 */
fun Modifier.chatTimelineKeyboardViewport(
    nativeKeyboardLiftPxState: MutableFloatState? = null,
    followKeyboard: () -> Boolean,
): Modifier =
    composed {
        val style = LocalPlatformStyle.current
        val density = LocalDensity.current
        val imeInsets = WindowInsets.ime
        val navInsets = WindowInsets.navigationBars

        if (style.isIOS) {
            return@composed Modifier.graphicsLayer {
                val liftPx = nativeKeyboardLiftPxState?.floatValue?.coerceAtLeast(0f) ?: 0f
                translationY = if (followKeyboard()) -liftPx else 0f
            }
        }

        Modifier.offset {
            if (!followKeyboard()) return@offset IntOffset.Zero
            val liftPx =
                effectiveChatKeyboardLiftPx(
                    imeBottomPx = imeInsets.getBottom(density),
                    navigationBottomPx = navInsets.getBottom(density),
                )
            IntOffset(0, -liftPx)
        }
    }

/**
 * Pins the composer, typing/reply/edit accessories, and staged-media chrome to the keyboard top.
 * The message viewport is intentionally not part of this modifier; use
 * [chatTimelineKeyboardViewport] for its independent anchoring policy.
 */
fun Modifier.chatComposerKeyboardMotion(
    extraBottom: Dp = 0.dp,
    nativeKeyboardLiftPxState: MutableFloatState? = null,
    clearNativeTabBar: Boolean = false,
): Modifier =
    composed {
        val density = LocalDensity.current
        val style = LocalPlatformStyle.current
        val imeInsets = WindowInsets.ime
        val navInsets = WindowInsets.navigationBars
        val navBottomPx = navInsets.getBottom(density)
        val navBottomDp = with(density) { navBottomPx.toDp() }

        if (style.isIOS) {
            val bottomPad =
                if (clearNativeTabBar) {
                    rememberTabBarOverlayHeight()
                } else {
                    navBottomDp
                }
            return@composed Modifier
                .padding(bottom = bottomPad + extraBottom)
                .clipToBounds()
                .graphicsLayer {
                    val liftPx = nativeKeyboardLiftPxState?.floatValue?.coerceAtLeast(0f) ?: 0f
                    translationY = -liftPx
                }
        }

        Modifier
            .padding(bottom = navBottomDp + extraBottom)
            .clipToBounds()
            .offset {
                val liftPx =
                    effectiveChatKeyboardLiftPx(
                        imeBottomPx = imeInsets.getBottom(density),
                        navigationBottomPx = navInsets.getBottom(density),
                    )
                IntOffset(0, -liftPx)
            }
    }
