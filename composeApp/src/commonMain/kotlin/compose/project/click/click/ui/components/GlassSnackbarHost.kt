package compose.project.click.click.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.LocalPlatformStyle

private val CompactSnackbarShape = RoundedCornerShape(14.dp)

/**
 * Compact glass toast aligned on the bottom bar row (e.g. beside the create-group FAB).
 * Does not use full-width Material snackbar chrome.
 */
@Composable
fun GlassSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val platformStyle = LocalPlatformStyle.current
    val enterMs = if (platformStyle.isIOS) 280 else 200
    val message = hostState.currentSnackbarData?.visuals?.message

    Box(modifier = modifier, contentAlignment = Alignment.CenterEnd) {
        AnimatedVisibility(
            visible = message != null,
            enter = slideInVertically(
                animationSpec = tween(enterMs, easing = FastOutSlowInEasing),
                initialOffsetY = { it / 3 },
            ) + fadeIn(tween(enterMs)),
            exit = slideOutVertically(
                animationSpec = tween(180, easing = FastOutSlowInEasing),
                targetOffsetY = { it / 3 },
            ) + fadeOut(tween(180)),
        ) {
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassSheetTokens.OnOled,
                    modifier = Modifier
                        .widthIn(max = 260.dp)
                        .clip(CompactSnackbarShape)
                        .background(GlassSheetTokens.GlassSurface)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Dismiss any visible toast immediately (e.g. when leaving the screen or opening chat). */
fun SnackbarHostState.dismissGlassSnackbar() {
    currentSnackbarData?.dismiss()
}

/** Brief feedback; pairs with [GlassSnackbarHost] reading [SnackbarHostState.currentSnackbarData]. */
suspend fun SnackbarHostState.showGlassSnackbar(
    message: String,
    duration: SnackbarDuration = SnackbarDuration.Short,
): SnackbarResult {
    currentSnackbarData?.dismiss()
    return showSnackbar(
        visuals = object : SnackbarVisuals {
            override val message: String = message
            override val actionLabel: String? = null
            override val withDismissAction: Boolean = false
            override val duration: SnackbarDuration = duration
        },
    )
}
