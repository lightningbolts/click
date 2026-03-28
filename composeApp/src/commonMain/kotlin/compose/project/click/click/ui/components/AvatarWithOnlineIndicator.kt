package compose.project.click.click.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val OnlineGreen = Color(0xFF22C55E)

/**
 * Wraps a circular avatar and draws a small online indicator at the bottom-end when [isOnline].
 */
@Composable
fun AvatarWithOnlineIndicator(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    indicatorSize: Dp = 10.dp,
    indicatorBorder: Dp = 1.5.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        content()
        if (isOnline) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(indicatorSize)
                    .clip(CircleShape)
                    .background(OnlineGreen)
                    .border(
                        width = indicatorBorder,
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    )
            )
        }
    }
}
