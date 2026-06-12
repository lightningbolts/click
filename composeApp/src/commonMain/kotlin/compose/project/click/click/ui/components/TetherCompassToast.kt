package compose.project.click.click.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val TETHER_TOAST_VISIBLE_MS = 30_000L

@Composable
fun TetherCompassToast(
    message: String?,
    modifier: Modifier = Modifier,
    visibleDurationMs: Long = TETHER_TOAST_VISIBLE_MS,
    onDismissed: () -> Unit = {},
) {
    var visible by remember { mutableStateOf(false) }
    val displayMessage = message?.takeIf { it.isNotBlank() }

    LaunchedEffect(displayMessage, visibleDurationMs) {
        if (displayMessage == null) {
            visible = false
            return@LaunchedEffect
        }
        visible = true
        delay(visibleDurationMs)
        visible = false
        delay(UnifiedToastTokens.ExitMillis.toLong())
        onDismissed()
    }

    AnimatedVisibility(
        visible = visible && displayMessage != null,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        enter = fadeIn(animationSpec = tween(UnifiedToastTokens.EnterMillis)),
        exit = fadeOut(animationSpec = tween(UnifiedToastTokens.ExitMillis)),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF1D4ED8),
                                Color(0xFF2563EB),
                                Color(0xFF1D4ED8),
                            ),
                        ),
                        shape = RoundedCornerShape(GlassSheetTokens.BentoExteriorCorner),
                    )
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Filled.Explore,
                        contentDescription = null,
                        tint = Color.White,
                    )
                    Text(
                        text = displayMessage.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
