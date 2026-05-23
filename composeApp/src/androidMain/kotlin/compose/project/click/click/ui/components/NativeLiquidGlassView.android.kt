package compose.project.click.click.ui.components // pragma: allowlist secret

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val AndroidGlassBlurRadius = 18.dp

@Composable
actual fun NativeLiquidGlassView(
    modifier: Modifier,
    cornerRadius: Dp,
    material: NativeLiquidGlassMaterial,
    showBorder: Boolean,
) {
    val useDarkMaterial = when (material) {
        NativeLiquidGlassMaterial.AdaptiveThin -> isSystemInDarkTheme()
        NativeLiquidGlassMaterial.DarkThin -> true
        NativeLiquidGlassMaterial.LightThin -> false
    }
    val tint = if (useDarkMaterial) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.White.copy(alpha = 0.55f)
    }
    val shape = RoundedCornerShape(cornerRadius)
    val borderModifier = if (showBorder) {
        Modifier.border(0.5.dp, GlassSheetTokens.GlassBorder, shape)
    } else {
        Modifier
    }
    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(AndroidGlassBlurRadius)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .clip(shape)
            .then(blurModifier)
            .background(tint, shape)
            .then(borderModifier),
    )
}
