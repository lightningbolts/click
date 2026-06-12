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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex

/** Shared motion + chrome for centered popups (media lightbox, rename group, alerts, confirms). */
object UnifiedPopupTokens {
    const val FadeInMillis = 320
    const val FadeOutMillis = 220
    const val ScaleInInitial = 0.92f
    const val ScaleOutTarget = 0.94f
    const val ContentClearDelayMillis = 240
    const val OverlayZIndex = 80f
}

/** Fade-out dismiss for buttons inside [UnifiedPopupAlert] (do not call [onDismissRequest] directly). */
val LocalUnifiedPopupAnimatedDismiss = staticCompositionLocalOf<() -> Unit> {
    error("LocalUnifiedPopupAnimatedDismiss used outside UnifiedPopupAlert")
}

/**
 * Full-screen overlay with animated scrim rendered in-tree (not platform [Dialog]) so content stays
 * centered and dimming covers the full host surface — modeled after media expansion / group rename.
 */
@Composable
fun UnifiedPopupOverlay(
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

    val fadeInSpec = tween<Float>(durationMillis = UnifiedPopupTokens.FadeInMillis, easing = FastOutSlowInEasing)
    val fadeOutSpec = tween<Float>(durationMillis = UnifiedPopupTokens.FadeOutMillis, easing = FastOutSlowInEasing)
    val scaleInSpec = tween<Float>(durationMillis = UnifiedPopupTokens.FadeInMillis, easing = FastOutSlowInEasing)
    val scaleOutSpec = tween<Float>(durationMillis = UnifiedPopupTokens.FadeOutMillis, easing = FastOutSlowInEasing)

    PlatformBackHandler(enabled = transitionState.targetState) {
        requestDismiss()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(UnifiedPopupTokens.OverlayZIndex),
    ) {
        AnimatedVisibility(
            visibleState = transitionState,
            enter = fadeIn(animationSpec = fadeInSpec),
            exit = fadeOut(animationSpec = fadeOutSpec),
            label = "unified_popup_scrim",
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
                    initialScale = UnifiedPopupTokens.ScaleInInitial,
                    animationSpec = scaleInSpec,
                ),
                exit = fadeOut(animationSpec = fadeOutSpec) + scaleOut(
                    targetScale = UnifiedPopupTokens.ScaleOutTarget,
                    animationSpec = scaleOutSpec,
                ),
                label = "unified_popup_content",
            ) {
                content()
            }
        }
    }
}

/** OLED card shell shared by form popups (rename group, etc.). */
@Composable
fun UnifiedPopupCard(
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 22.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(GlassSheetTokens.BentoExteriorCorner)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .clip(shape)
            .border(1.dp, GlassSheetTokens.GlassBorder, shape),
        shape = shape,
        color = GlassSheetTokens.OledBlack,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            content()
        }
    }
}

/**
 * Centered OLED alert with animated scrim — unified replacement for legacy alert dialogs.
 */
@Composable
fun UnifiedPopupAlert(
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
    val fadeInSpec = tween<Float>(durationMillis = UnifiedPopupTokens.FadeInMillis, easing = FastOutSlowInEasing)
    val fadeOutSpec = tween<Float>(durationMillis = UnifiedPopupTokens.FadeOutMillis, easing = FastOutSlowInEasing)

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

    CompositionLocalProvider(
        LocalUnifiedPopupAnimatedDismiss provides ::requestDismiss,
        LocalGlassAlertAnimatedDismiss provides ::requestDismiss,
    ) {
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
                    label = "unified_alert_scrim",
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
                        enter = fadeIn(animationSpec = fadeInSpec) + scaleIn(
                            initialScale = UnifiedPopupTokens.ScaleInInitial,
                            animationSpec = tween(UnifiedPopupTokens.FadeInMillis, easing = FastOutSlowInEasing),
                        ),
                        exit = fadeOut(animationSpec = fadeOutSpec) + scaleOut(
                            targetScale = UnifiedPopupTokens.ScaleOutTarget,
                            animationSpec = tween(UnifiedPopupTokens.FadeOutMillis, easing = FastOutSlowInEasing),
                        ),
                        label = "unified_alert_content",
                    ) {
                        UnifiedPopupCard(horizontalPadding = 28.dp) {
                            if (icon != null) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    icon()
                                }
                            }
                            ProvideTextStyle(
                                MaterialTheme.typography.titleMedium.copy(color = GlassSheetTokens.OnOled),
                            ) {
                                title()
                            }
                            if (text != null) {
                                ProvideTextStyle(
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

/** TextButton tuned for unified popup alerts. */
@Composable
fun UnifiedPopupTextButton(
    label: String,
    onClick: () -> Unit,
    contentColor: Color = MaterialTheme.colorScheme.primary,
) {
    TextButton(onClick = onClick) {
        Text(label, color = contentColor)
    }
}

/**
 * Confirmation / form dialog with unified overlay motion (scale + fade).
 */
@Composable
fun UnifiedPopupFormDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = "Cancel",
    body: @Composable () -> Unit,
) {
    UnifiedPopupOverlay(
        visible = visible,
        onDismissRequest = onDismissRequest,
    ) {
        Surface(
            modifier = modifier
                .padding(horizontal = 28.dp)
                .widthIn(max = 360.dp),
            shape = RoundedCornerShape(GlassSheetTokens.BentoExteriorCorner),
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
                            UnifiedPopupTextButton(
                                label = dismissLabel,
                                contentColor = GlassSheetTokens.OnOledMuted,
                                onClick = onDismissRequest,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        UnifiedPopupTextButton(
                            label = confirmLabel,
                            contentColor = GlassSheetTokens.OnOled,
                            onClick = onConfirm,
                        )
                    }
                }
            }
        }
    }
}
