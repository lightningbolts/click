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
 * 2. The message timeline follows when the keyboard session begins at the newest edge.
 *
 * The newest edge is index 0 for the reverse-layout timeline. It is valid even before the first
 * message has been inserted, which matters for a brand-new/empty hub: opening the keyboard first
 * must still reserve the keyboard viewport so the first outbound message cannot appear underneath
 * the keyboard.
 */
internal fun chatTimelineShouldFollowKeyboard(
    firstVisibleItemIndex: Int,
    initialTimelineScrollDone: Boolean,
    userScrollInProgress: Boolean,
): Boolean =
    firstVisibleItemIndex == 0 && !userScrollInProgress

internal fun effectiveChatKeyboardLiftPx(
    imeBottomPx: Int,
    navigationBottomPx: Int,
): Int = (imeBottomPx - navigationBottomPx).coerceAtLeast(0)

/**
 * Pure state machine for one keyboard-visible session. The follow decision is captured exactly
 * once on the hidden -> visible edge, held while keyboard height changes, and cleared only after
 * the visible -> hidden edge.
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
