package compose.project.click.click.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.LocalPlatformStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Shared motion + chrome for compact toasts and center nudge overlays. */
object UnifiedToastTokens {
    const val EnterMillis = 240
    const val ExitMillis = 180
    const val DefaultDurationMs = 2_400L
    const val LongDurationMs = 4_000L
    const val CompactCornerDp = 14
    const val OverlayCornerDp = 28
    const val MaxWidthDp = 300
}

data class UnifiedToastContent(
    val message: String,
    val actionLabel: String? = null,
)

@Stable
class UnifiedToastState {
    var content by mutableStateOf<UnifiedToastContent?>(null)
        private set

    val message: String? get() = content?.message

    private var hideJob: Job? = null
    private var pendingAction: (() -> Unit)? = null

    fun show(
        scope: CoroutineScope,
        text: String,
        durationMs: Long = UnifiedToastTokens.DefaultDurationMs,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        hideJob?.cancel()
        pendingAction = onAction
        content = UnifiedToastContent(
            message = text,
            actionLabel = actionLabel?.trim()?.takeIf { it.isNotEmpty() },
        )
        hideJob = scope.launch {
            delay(durationMs)
            if (content?.message == text) {
                content = null
                pendingAction = null
            }
        }
    }

    fun performAction() {
        val action = pendingAction
        dismiss()
        action?.invoke()
    }

    fun dismiss() {
        hideJob?.cancel()
        hideJob = null
        content = null
        pendingAction = null
    }
}

@Composable
fun rememberUnifiedToastState(): UnifiedToastState = remember { UnifiedToastState() }

/**
 * Compact toast pill — opaque surface + hard border (connections FAB row, chat composer feedback, etc.).
 * Optional [UnifiedToastContent.actionLabel] renders a trailing text action.
 */
@Composable
fun UnifiedToastHost(
    state: UnifiedToastState,
    modifier: Modifier = Modifier,
    opaque: Boolean = false,
) {
    val platformStyle = LocalPlatformStyle.current
    val enterMs = if (platformStyle.isIOS) UnifiedToastTokens.EnterMillis + 40 else UnifiedToastTokens.EnterMillis
    val exitMs = UnifiedToastTokens.ExitMillis
    val toast = state.content
    val toastShape = RoundedCornerShape(UnifiedToastTokens.CompactCornerDp.dp)

    Box(
        modifier = modifier,
        contentAlignment = if (opaque) Alignment.Center else Alignment.CenterEnd,
    ) {
        AnimatedVisibility(
            visible = toast != null,
            enter = slideInVertically(
                animationSpec = tween(enterMs, easing = FastOutSlowInEasing),
                initialOffsetY = { it / 3 },
            ) + fadeIn(tween(enterMs)),
            exit = slideOutVertically(
                animationSpec = tween(exitMs, easing = FastOutSlowInEasing),
                targetOffsetY = { it / 3 },
            ) + fadeOut(tween(exitMs)),
            label = "unified_toast_compact",
        ) {
            if (toast != null) {
                val backgroundModifier = if (opaque) {
                    Modifier
                        .background(GlassSheetTokens.OledBlack(), toastShape)
                        .border(1.dp, GlassSheetTokens.GlassBorder(), toastShape)
                } else {
                    Modifier
                        .clip(toastShape)
                        .background(GlassSheetTokens.GlassSurface(), toastShape)
                        .border(1.dp, GlassSheetTokens.GlassBorder(), toastShape)
                }
                val onSurface = if (opaque) GlassSheetTokens.OnOled() else MaterialTheme.colorScheme.onSurface
                Row(
                    modifier = Modifier
                        .widthIn(max = UnifiedToastTokens.MaxWidthDp.dp)
                        .then(backgroundModifier)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = toast.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    toast.actionLabel?.let { label ->
                        TextButton(
                            onClick = { state.performAction() },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        ) {
                            Text(
                                text = label,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-bleed center nudge overlay with optional dismiss action (map nudges, etc.).
 */
@Composable
fun UnifiedToastOverlay(
    visible: Boolean,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "Got it",
) {
    val enterTransition = fadeIn(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
    ) + scaleIn(
        initialScale = 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
    )
    val exitTransition = fadeOut(
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
    ) + scaleOut(
        targetScale = 0.96f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = enterTransition,
            exit = exitTransition,
            label = "unified_toast_overlay",
        ) {
            LiquidGlassPill(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .graphicsLayer { alpha = 1f },
                cornerRadiusDp = UnifiedToastTokens.OverlayCornerDp,
                backgroundStrength = 0.85f,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onDismiss) {
                        Text(dismissLabel)
                    }
                }
            }
        }
    }
}
