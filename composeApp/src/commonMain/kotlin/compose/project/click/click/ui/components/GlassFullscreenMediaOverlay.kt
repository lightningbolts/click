@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Window-level dialog props that fill the physical screen, including the home-indicator strip. */
internal expect fun fullscreenMediaDialogProperties(): DialogProperties

/**
 * Full-screen media lightbox.
 *
 * Uses a window [Dialog] rather than [UnifiedPopupOverlay]'s [androidx.compose.ui.window.Popup]
 * so the viewer is not clipped by a UIKit / Calf bottom sheet (other-user profile Media tab)
 * and sits above chat chrome the same way from every call site.
 *
 * Fade only — no scale. The photo consumes taps; dismiss is close / system back, not a scrim tap.
 */
@Composable
fun GlassFullscreenMediaOverlay(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrimAlpha: Float = GlassSheetTokens.ScrimBaseAlpha,
    motion: UnifiedPopupMotion = UnifiedPopupMotion.Media,
    content: @Composable () -> Unit,
) {
    val transitionState = remember { MutableTransitionState(false) }
    var userDismissPending by remember { mutableStateOf(false) }

    LaunchedEffect(visible, userDismissPending) {
        if (userDismissPending) return@LaunchedEffect
        transitionState.targetState = visible
    }

    fun requestDismiss() {
        if (!transitionState.targetState) return
        userDismissPending = true
        transitionState.targetState = false
    }

    LaunchedEffect(transitionState.isIdle, transitionState.currentState, transitionState.targetState) {
        if (
            transitionState.isIdle &&
            !transitionState.currentState &&
            !transitionState.targetState &&
            userDismissPending
        ) {
            userDismissPending = false
            onDismissRequest()
        }
    }

    if (!transitionState.currentState && !transitionState.targetState && transitionState.isIdle) {
        return
    }

    val fadeInSpec = tween<Float>(durationMillis = motion.fadeInMillis, easing = FastOutSlowInEasing)
    val fadeOutSpec = tween<Float>(durationMillis = motion.fadeOutMillis, easing = FastOutSlowInEasing)

    Dialog(
        onDismissRequest = { requestDismiss() },
        properties = fullscreenMediaDialogProperties(),
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = fadeIn(animationSpec = fadeInSpec),
                exit = fadeOut(animationSpec = fadeOutSpec),
                label = "media_lightbox_scrim",
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = scrimAlpha)),
                )
            }
            AnimatedVisibility(
                visibleState = transitionState,
                enter = fadeIn(animationSpec = fadeInSpec),
                exit = fadeOut(animationSpec = fadeOutSpec),
                label = "media_lightbox_content",
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
    }
}
