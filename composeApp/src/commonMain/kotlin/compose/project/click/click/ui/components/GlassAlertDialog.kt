package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Fade-out dismiss for buttons inside [GlassAlertDialog] (do not call [onDismissRequest] directly). */
val LocalGlassAlertAnimatedDismiss = staticCompositionLocalOf<() -> Unit> {
    error("LocalGlassAlertAnimatedDismiss used outside GlassAlertDialog")
}

/**
 * Centered OLED dialog with fade in/out (replaces abrupt platform dialog appearance).
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
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
) {
    var open by remember { mutableStateOf(true) }
    var contentVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun requestDismiss() {
        if (!open) return
        contentVisible = false
        scope.launch {
            delay(200)
            open = false
            onDismissRequest()
        }
    }

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    if (!open) return

    CompositionLocalProvider(LocalGlassAlertAnimatedDismiss provides ::requestDismiss) {
    Dialog(
        onDismissRequest = { requestDismiss() },
        properties = properties,
    ) {
        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            ),
            exit = fadeOut(
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            ),
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
