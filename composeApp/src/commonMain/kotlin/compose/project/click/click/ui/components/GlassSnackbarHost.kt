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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.LocalPlatformStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CompactSnackbarShape = RoundedCornerShape(14.dp)

@Stable
class GlassToastState {
    var message by mutableStateOf<String?>(null)
        private set

    private var hideJob: Job? = null

    fun show(scope: CoroutineScope, text: String, durationMs: Long = 2_400L) {
        hideJob?.cancel()
        message = text
        hideJob = scope.launch {
            delay(durationMs)
            if (message == text) {
                message = null
            }
        }
    }

    fun dismiss() {
        hideJob?.cancel()
        hideJob = null
        message = null
    }
}

@Composable
fun rememberGlassToastState(): GlassToastState = remember { GlassToastState() }

/**
 * Compact glass toast (e.g. on the bottom row beside the create-group FAB).
 */
@Composable
fun GlassToastHost(
    state: GlassToastState,
    modifier: Modifier = Modifier,
) {
    val platformStyle = LocalPlatformStyle.current
    val enterMs = if (platformStyle.isIOS) 280 else 200
    val text = state.message

    Box(modifier = modifier, contentAlignment = Alignment.CenterEnd) {
        AnimatedVisibility(
            visible = text != null,
            enter = slideInVertically(
                animationSpec = tween(enterMs, easing = FastOutSlowInEasing),
                initialOffsetY = { it / 3 },
            ) + fadeIn(tween(enterMs)),
            exit = slideOutVertically(
                animationSpec = tween(180, easing = FastOutSlowInEasing),
                targetOffsetY = { it / 3 },
            ) + fadeOut(tween(180)),
        ) {
            if (text != null) {
                Text(
                    text = text,
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

@Composable
fun GlassSnackbarHost(
    state: GlassToastState,
    modifier: Modifier = Modifier,
) = GlassToastHost(state = state, modifier = modifier)
