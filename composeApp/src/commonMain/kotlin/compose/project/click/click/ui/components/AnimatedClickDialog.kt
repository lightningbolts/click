package compose.project.click.click.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Confirmation / form dialog that animates in and out (scale + fade) instead of appearing and
 * disappearing instantly — mirroring the motion of the create / join hub sheets.
 *
 * The host stays composed while [visible] is true *and* while the exit transition is still running,
 * so the dismiss animation is allowed to play before the dialog window is torn down. Always call
 * this unconditionally (drive it with [visible]); do not wrap it in an `if (visible)` block.
 */
@Composable
fun AnimatedClickDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = "Cancel",
    body: @Composable () -> Unit,
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible

    // Keep the window mounted through both the enter and the exit transition.
    if (!transitionState.currentState && !transitionState.targetState && transitionState.isIdle) {
        return
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = fadeIn(tween(160, easing = FastOutSlowInEasing)) +
                    scaleIn(
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                        initialScale = 0.86f,
                    ),
                exit = fadeOut(tween(150, easing = FastOutSlowInEasing)) +
                    scaleOut(
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                        targetScale = 0.86f,
                    ),
                label = "click_dialog",
            ) {
                Surface(
                    modifier = modifier
                        .padding(horizontal = 28.dp)
                        .widthIn(max = 360.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = GlassSheetTokens.OledBlack,
                    contentColor = GlassSheetTokens.OnOled,
                    tonalElevation = 6.dp,
                ) {
                    CompositionLocalProvider(LocalContentColor provides GlassSheetTokens.OnOled) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = GlassSheetTokens.OnOled,
                            )
                            Spacer(Modifier.padding(top = 12.dp))
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                body()
                            }
                            Spacer(Modifier.padding(top = 20.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (dismissLabel != null) {
                                    DialogTextButton(
                                        label = dismissLabel,
                                        color = GlassSheetTokens.OnOledMuted,
                                        onClick = onDismissRequest,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                DialogTextButton(
                                    label = confirmLabel,
                                    color = GlassSheetTokens.OnOled,
                                    onClick = onConfirm,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogTextButton(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(text = label, color = color, fontWeight = FontWeight.SemiBold)
    }
}
