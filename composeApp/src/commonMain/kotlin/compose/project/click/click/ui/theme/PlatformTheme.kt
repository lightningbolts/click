package compose.project.click.click.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.getPlatform

/**
 * Platform deltas for Functional Clarity. Visual language is unified;
 * [useRipple] remains Android-only per platform interaction rules.
 */
@Immutable
data class PlatformStyle(
    val isIOS: Boolean,
    val cardCornerRadius: Dp,
    val compactCardCornerRadius: Dp,
    val buttonCornerRadius: Dp,
    val cardBorderWidth: Dp,
    /** Legacy name — unused for fills; cards use opaque surfaces. */
    val glassBackgroundAlpha: Float,
    /** Legacy name — unused; borders use [BorderHard]. */
    val glassBorderAlpha: Float,
    val glassBorderPrimaryAlpha: Float,
    val useShadowElevation: Boolean,
    val useRipple: Boolean,
    val pressOffset: Dp,
)

val LocalPlatformStyle = staticCompositionLocalOf {
    PlatformStyle(
        isIOS = false,
        cardCornerRadius = 16.dp,
        compactCardCornerRadius = 8.dp,
        buttonCornerRadius = 8.dp,
        cardBorderWidth = 2.dp,
        glassBackgroundAlpha = 1f,
        glassBorderAlpha = 1f,
        glassBorderPrimaryAlpha = 1f,
        useShadowElevation = false,
        useRipple = true,
        pressOffset = 2.dp,
    )
}

private val iOSPlatformStyle = PlatformStyle(
    isIOS = true,
    cardCornerRadius = 16.dp,
    compactCardCornerRadius = 8.dp,
    buttonCornerRadius = 8.dp,
    cardBorderWidth = 2.dp,
    glassBackgroundAlpha = 1f,
    glassBorderAlpha = 1f,
    glassBorderPrimaryAlpha = 1f,
    useShadowElevation = false,
    useRipple = false,
    pressOffset = 2.dp,
)

private val androidPlatformStyle = PlatformStyle(
    isIOS = false,
    cardCornerRadius = 16.dp,
    compactCardCornerRadius = 8.dp,
    buttonCornerRadius = 8.dp,
    cardBorderWidth = 2.dp,
    glassBackgroundAlpha = 1f,
    glassBorderAlpha = 1f,
    glassBorderPrimaryAlpha = 1f,
    useShadowElevation = false,
    useRipple = true,
    pressOffset = 2.dp,
)

@Composable
fun PlatformStyleProvider(content: @Composable () -> Unit) {
    val isIOS = getPlatform().name.contains("iOS", ignoreCase = true)
    val style = if (isIOS) iOSPlatformStyle else androidPlatformStyle
    CompositionLocalProvider(LocalPlatformStyle provides style) {
        content()
    }
}

@Composable
fun clickColorScheme(isDarkMode: Boolean) =
    if (isDarkMode) {
        darkColorScheme(
            primary = PrimaryBlue,
            onPrimary = Color.White,
            secondary = Color(0xFF5E5E5E),
            onSecondary = Color.White,
            background = BackgroundDark,
            onBackground = OnSurfaceDark,
            surface = SurfaceDark,
            onSurface = OnSurfaceDark,
            primaryContainer = LightBlue,
            onPrimaryContainer = SoftBlue,
            surfaceVariant = Color(0xFF3A3C3C),
            onSurfaceVariant = OutlineVariant,
            outline = OutlineMuted,
            error = Color(0xFFBA1A1A),
        )
    } else {
        lightColorScheme(
            primary = PrimaryBlue,
            onPrimary = Color.White,
            secondary = Color(0xFF5E5E5E),
            onSecondary = Color.White,
            background = BackgroundLight,
            onBackground = OnSurfaceLight,
            surface = SurfaceLight,
            onSurface = OnSurfaceLight,
            primaryContainer = SoftBlue,
            onPrimaryContainer = DeepBlue,
            surfaceVariant = SurfaceContainerHighest,
            onSurfaceVariant = OnSurfaceVariant,
            outline = OutlineMuted,
            error = Color(0xFFBA1A1A),
        )
    }

@Composable
fun PlatformThemeProvider(
    isDarkMode: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = clickColorScheme(isDarkMode),
        typography = clickTypography(),
    ) {
        PlatformStyleProvider(content)
    }
}
