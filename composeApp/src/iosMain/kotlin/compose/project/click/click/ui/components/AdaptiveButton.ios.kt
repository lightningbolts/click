package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    val shape = RoundedCornerShape(style.buttonCornerRadius)
    Box(modifier = modifier) {
        NativeLiquidGlassView(
            modifier = Modifier.fillMaxSize(),
            cornerRadius = style.buttonCornerRadius,
            material = NativeLiquidGlassMaterial.AdaptiveThin,
            showBorder = true,
        )
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = PrimaryBlue,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = Color.Gray,
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
            ),
            shape = shape,
            content = content,
        )
    }
}
