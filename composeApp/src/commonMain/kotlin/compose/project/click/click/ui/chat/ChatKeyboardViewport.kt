package compose.project.click.click.ui.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.components.rememberTabBarOverlayHeight
import compose.project.click.click.ui.theme.LocalPlatformStyle
import kotlinx.coroutines.flow.collect

/**
 * Keyboard motion for chat is deliberately split into two consumers:
 *
 * 1. The composer/accessory dock always follows the keyboard top.
 * 2. The message timeline follows only when the keyboard session begins at the newest message.
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
 * Pure state machine for one keyboard-visible session. The follow decision is captured exactly
 * once on the hidden -> visible edge, held while keyboard height changes, and cleared only after
 * the visible -> hidden edge. Keeping this outside Compose makes the no-jump contract directly
 * unit-testable.
 */
internal class ChatKeyboardSessionLatch {
    private var wasVisible = false
    private var followsKeyboard = false

    fun update(
        liftPx: Float,
        shouldFollowOnOpen: Boolean,
    ): Boolean {
        val visible = liftPx > KEYBOARD_VISIBLE_EPSILON_PX
        when {
            visible && !wasVisible -> followsKeyboard = shouldFollowOnOpen
            !visible && wasVisible -> followsKeyboard = false
        }
        wasVisible = visible
        return followsKeyboard
    }
}

/**
 * Latches the timeline anchoring policy for one keyboard-visible session.
 *
 * The decision is made once, when keyboard lift changes from zero to non-zero. This is important:
 * if the timeline stopped following merely because the user started dragging, it would jump down
 * by the full keyboard height under their finger. Likewise, a history reader who opens the
 * keyboard must not suddenly be pulled to the latest message halfway through the animation.
 */
@Composable
fun rememberChatTimelineKeyboardFollow(
    nativeKeyboardLiftPxState: MutableFloatState? = null,
    shouldFollowOnKeyboardOpen: () -> Boolean,
): State<Boolean> {
    val style = LocalPlatformStyle.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val navInsets = WindowInsets.navigationBars
    val shouldFollowState = rememberUpdatedState(shouldFollowOnKeyboardOpen)
    val followState = remember { mutableStateOf(false) }
    val sessionLatch = remember { ChatKeyboardSessionLatch() }

    LaunchedEffect(style.isIOS, nativeKeyboardLiftPxState, density) {
        snapshotFlow {
            if (style.isIOS) {
                nativeKeyboardLiftPxState?.floatValue?.coerceAtLeast(0f) ?: 0f
            } else {
                effectiveChatKeyboardLiftPx(
                    imeBottomPx = imeInsets.getBottom(density),
                    navigationBottomPx = navInsets.getBottom(density),
                ).toFloat()
            }
        }.collect { liftPx ->
            followState.value =
                sessionLatch.update(
                    liftPx = liftPx,
                    shouldFollowOnOpen = shouldFollowState.value(),
                )
        }
    }

    return followState
}

/**
 * Moves only the message viewport for keyboard sessions that began at latest. When the user was
 * reading history at focus time, the viewport stays in place and the keyboard simply occludes its
 * lower region as the composer moves above the IME.
 *
 * [followKeyboard] is evaluated from the graphics/placement phase so keyboard frames do not force
 * the chat subtree to recompose.
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
 *
 * Do not clip this translated dock at its own layout bounds. Its resting layout intentionally
 * reserves bottom chrome space while its rendered position may travel hundreds of pixels upward;
 * the chat screen's outer viewport is the correct clipping boundary.
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
                .graphicsLayer {
                    val liftPx = nativeKeyboardLiftPxState?.floatValue?.coerceAtLeast(0f) ?: 0f
                    translationY = -liftPx
                }
        }

        Modifier
            .padding(bottom = navBottomDp + extraBottom)
            .offset {
                val liftPx =
                    effectiveChatKeyboardLiftPx(
                        imeBottomPx = imeInsets.getBottom(density),
                        navigationBottomPx = navInsets.getBottom(density),
                    )
                IntOffset(0, -liftPx)
            }
    }

private const val KEYBOARD_VISIBLE_EPSILON_PX = 0.5f
