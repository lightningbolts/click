package compose.project.click.click.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.getPlatform

@Immutable
data class PlatformStyle(
    val isIOS: Boolean,
    val cardCornerRadius: Dp,
    val compactCardCornerRadius: Dp,
    val buttonCornerRadius: Dp,
    val cardBorderWidth: Dp,
    val glassBackgroundAlpha: Float,
    val glassBorderAlpha: Float,
    val glassBorderPrimaryAlpha: Float,
    val useShadowElevation: Boolean,
    val useRipple: Boolean,
)

val LocalPlatformStyle = staticCompositionLocalOf {
    PlatformStyle(
        isIOS = false,
        cardCornerRadius = 28.dp,
        compactCardCornerRadius = 8.dp,
        buttonCornerRadius = 16.dp,
        cardBorderWidth = 1.dp,
        glassBackgroundAlpha = 0.05f,
        glassBorderAlpha = 0.10f,
        glassBorderPrimaryAlpha = 0.15f,
        useShadowElevation = true,
        useRipple = true,
    )
}

private val iOSPlatformStyle = PlatformStyle(
    isIOS = true,
    cardCornerRadius = 28.dp,
    compactCardCornerRadius = 8.dp,
    buttonCornerRadius = 12.dp,
    cardBorderWidth = 0.5.dp,
    glassBackgroundAlpha = 0.08f,
    glassBorderAlpha = 0.14f,
    glassBorderPrimaryAlpha = 0.20f,
    useShadowElevation = false,
    useRipple = false,
)

private val androidPlatformStyle = PlatformStyle(
    isIOS = false,
    cardCornerRadius = 28.dp,
    compactCardCornerRadius = 8.dp,
    buttonCornerRadius = 16.dp,
    cardBorderWidth = 1.dp,
    glassBackgroundAlpha = 0.05f,
    glassBorderAlpha = 0.10f,
    glassBorderPrimaryAlpha = 0.15f,
    useShadowElevation = true,
    useRipple = true,
)

@Composable
fun PlatformThemeProvider(content: @Composable () -> Unit) {
    val isIOS = getPlatform().name.contains("iOS", ignoreCase = true)
    val style = if (isIOS) iOSPlatformStyle else androidPlatformStyle
    CompositionLocalProvider(LocalPlatformStyle provides style) {
        content()
    }
}
