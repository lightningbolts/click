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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Full-screen media lightbox.
 *
 * Drawn in the caller’s Compose tree (not a window [androidx.compose.ui.window.Popup] or
 * [androidx.compose.ui.window.Dialog]).
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

    LaunchedEffect(visible) {
        if (!visible) {
            userDismissPending = false
            transitionState.targetState = false
        } else if (!userDismissPending) {
            transitionState.targetState = true
        }
    }

    fun requestDismiss() {
        if (userDismissPending) return
        if (!transitionState.currentState && !transitionState.targetState) return
        userDismissPending = true
        transitionState.targetState = false
    }

    LaunchedEffect(
        transitionState.isIdle,
        transitionState.currentState,
        transitionState.targetState,
        userDismissPending,
    ) {
        if (
            transitionState.isIdle &&
            !transitionState.currentState &&
            !transitionState.targetState &&
            userDismissPending
        ) {
            onDismissRequest()
        }
    }

    if (!transitionState.currentState && !transitionState.targetState && transitionState.isIdle) {
        return
    }

    PlatformBackHandler(enabled = transitionState.targetState, onBack = ::requestDismiss)

    val fadeInSpec = tween<Float>(durationMillis = motion.fadeInMillis, easing = FastOutSlowInEasing)
    val fadeOutSpec = tween<Float>(durationMillis = motion.fadeOutMillis, easing = FastOutSlowInEasing)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .zIndex(UnifiedPopupTokens.OverlayZIndex),
    ) {
        AnimatedVisibility(
            visibleState = transitionState,
            enter = fadeIn(animationSpec = fadeInSpec),
            exit = fadeOut(animationSpec = fadeOutSpec),
            label = "media_overlay_scrim",
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrimAlpha)),
            )
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = fadeIn(animationSpec = fadeInSpec),
                exit = fadeOut(animationSpec = fadeOutSpec),
                label = "media_overlay_content",
            ) {
                content()
            }
        }
    }
}

/**
 * Close control for photo / media lightboxes. Same 40pt liquid-glass circle as compact native
 * chrome, inset from the safe drawing edge — not stacked under the chat header.
 */
@Composable
fun MediaLightboxTopChrome(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    showClose: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val safeTop = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val insetTop = maxOf(statusTop, safeTop)
    val resolvedTop =
        if (insetTop > 0.dp) {
            insetTop
        } else {
            0.dp
        }
    val buttonSize = NativeHeaderMetrics.ChromeButtonSizePt.dp
    val topGutter =
        ((NativeHeaderMetrics.CompactBarHeightPt - NativeHeaderMetrics.ChromeButtonSizePt) / 2.0).dp
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .zIndex(2f)
                .padding(
                    start = NativeHeaderMetrics.LeadingInsetPt.dp,
                    end = NativeHeaderMetrics.TrailingInsetPt.dp,
                    top = resolvedTop + topGutter,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showClose) {
            ClickCircularGlassIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "Close",
                onClick = onClose,
                size = buttonSize,
                tint = Color.White,
            )
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
internal fun MediaLightboxSaveShareTrailing(
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    val buttonSize = NativeHeaderMetrics.ChromeButtonSizePt.dp
    ClickCircularGlassIconButton(
        icon = Icons.Outlined.Download,
        contentDescription = "Save",
        onClick = onSave,
        size = buttonSize,
        tint = Color.White,
    )
    ClickCircularGlassIconButton(
        icon = Icons.Outlined.Share,
        contentDescription = "Share",
        onClick = onShare,
        size = buttonSize,
        tint = Color.White,
    )
}
