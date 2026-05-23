package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.LocalPlatformStyle // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret

@Composable
actual fun AdaptiveButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    val style = LocalPlatformStyle.current
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = Color.Gray.copy(alpha = 0.1f),
            disabledContentColor = Color.Gray,
        ),
        border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(style.buttonCornerRadius),
        content = content,
    )
}
