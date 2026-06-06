package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/** Fade-out dismiss for buttons inside [GlassAlertDialog] (do not call [onDismissRequest] directly). */
val LocalGlassAlertAnimatedDismiss = staticCompositionLocalOf<() -> Unit> {
    error("LocalGlassAlertAnimatedDismiss used outside GlassAlertDialog")
}

/**
 * Centered OLED alert with a custom animated scrim (full-screen [Popup], not platform [Dialog])
 * so brightness does not pop when dismissing — the scrim stays composed through [fadeOut].
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    showActionRow: Boolean = true,
    @Suppress("UNUSED_PARAMETER") properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
) {
    val transitionState = remember { MutableTransitionState(false) }
    val fadeInSpec = tween<Float>(durationMillis = 280, easing = FastOutSlowInEasing)
    val fadeOutSpec = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)

    LaunchedEffect(Unit) {
        transitionState.targetState = true
    }

    fun requestDismiss() {
        if (!transitionState.targetState) return
        transitionState.targetState = false
    }

    LaunchedEffect(transitionState.isIdle, transitionState.currentState, transitionState.targetState) {
        if (transitionState.isIdle && !transitionState.currentState && !transitionState.targetState) {
            onDismissRequest()
        }
    }

    if (!transitionState.currentState && !transitionState.targetState && transitionState.isIdle) {
        return
    }

    CompositionLocalProvider(LocalGlassAlertAnimatedDismiss provides ::requestDismiss) {
        Popup(
            onDismissRequest = { requestDismiss() },
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visibleState = transitionState,
                    enter = fadeIn(animationSpec = fadeInSpec),
                    exit = fadeOut(animationSpec = fadeOutSpec),
                    label = "glass_alert_scrim",
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = GlassSheetTokens.ScrimBaseAlpha))
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
                        enter = fadeIn(animationSpec = fadeInSpec),
                        exit = fadeOut(animationSpec = fadeOutSpec),
                        label = "glass_alert_content",
                    ) {
                        val shape = RoundedCornerShape(GlassSheetTokens.BentoExteriorCorner)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp)
                                .clip(shape)
                                .border(1.dp, GlassSheetTokens.GlassBorder, shape)
                                .background(GlassSheetTokens.OledBlack)
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (icon != null) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    icon()
                                }
                            }
                            androidx.compose.material3.ProvideTextStyle(
                                MaterialTheme.typography.titleMedium.copy(color = GlassSheetTokens.OnOled),
                            ) {
                                title()
                            }
                            if (text != null) {
                                androidx.compose.material3.ProvideTextStyle(
                                    MaterialTheme.typography.bodyMedium.copy(color = GlassSheetTokens.OnOledMuted),
                                ) {
                                    text()
                                }
                            }
                            if (showActionRow && (confirmButton != null || dismissButton != null)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    dismissButton?.invoke()
                                    confirmButton?.invoke()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** TextButton tuned for OLED glass dialogs. */
@Composable
fun GlassDialogTextButton(
    label: String,
    onClick: () -> Unit,
    contentColor: Color = MaterialTheme.colorScheme.primary,
) {
    TextButton(onClick = onClick) {
        Text(label, color = contentColor)
    }
}
