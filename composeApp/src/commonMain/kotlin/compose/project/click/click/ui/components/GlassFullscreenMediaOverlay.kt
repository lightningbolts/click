package compose.project.click.click.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex

/**
 * Full-screen media lightbox with an animated custom scrim rendered in-tree (not [Popup] /
 * platform [Dialog]) so the overlay stays centered and the dimming covers the full host surface.
 */
@Composable
fun GlassFullscreenMediaOverlay(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrimAlpha: Float = GlassSheetTokens.ScrimBaseAlpha,
    content: @Composable () -> Unit,
) {
    val transitionState = remember { MutableTransitionState(false) }
    var userDismissPending by remember { mutableStateOf(false) }

    if (visible && !userDismissPending) {
        transitionState.targetState = true
    } else if (!visible && !userDismissPending) {
        transitionState.targetState = false
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

    val fadeInSpec = tween<Float>(durationMillis = 420, easing = FastOutSlowInEasing)
    val fadeOutSpec = tween<Float>(durationMillis = 280, easing = FastOutSlowInEasing)
    val scaleInSpec = tween<Float>(durationMillis = 420, easing = FastOutSlowInEasing)
    val scaleOutSpec = tween<Float>(durationMillis = 280, easing = FastOutSlowInEasing)

    PlatformBackHandler(enabled = transitionState.targetState) {
        requestDismiss()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(80f),
    ) {
        AnimatedVisibility(
            visibleState = transitionState,
            enter = fadeIn(animationSpec = fadeInSpec),
            exit = fadeOut(animationSpec = fadeOutSpec),
            label = "glass_media_scrim",
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { requestDismiss() },
                    ),
            )
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = fadeIn(animationSpec = fadeInSpec) + scaleIn(
                    initialScale = 0.92f,
                    animationSpec = scaleInSpec,
                ),
                exit = fadeOut(animationSpec = fadeOutSpec) + scaleOut(
                    targetScale = 0.94f,
                    animationSpec = scaleOutSpec,
                ),
                label = "glass_media_content",
            ) {
                content()
            }
        }
    }
}
