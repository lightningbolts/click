package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Material preset for native / fallback liquid glass backdrops. */
enum class NativeLiquidGlassMaterial {
    /** Maps to thin system material for current light/dark theme. */
    AdaptiveThin,
    DarkThin,
    LightThin,
}

/**
 * Platform backdrop blur: UIKit [UIVisualEffectView] on iOS, Compose blur / frosted tint on Android.
 * Place foreground content in a sibling [androidx.compose.foundation.layout.Box] above this view.
 */
@Composable
expect fun NativeLiquidGlassView(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 0.dp,
    material: NativeLiquidGlassMaterial = NativeLiquidGlassMaterial.AdaptiveThin,
    showBorder: Boolean = false,
)

/** Convenience wrapper used by dialogs, headers, and cards. */
@Composable
fun PlatformGlassBackdrop(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 0.dp,
    material: NativeLiquidGlassMaterial = NativeLiquidGlassMaterial.AdaptiveThin,
    showBorder: Boolean = false,
) {
    NativeLiquidGlassView(
        modifier = modifier,
        cornerRadius = cornerRadius,
        material = material,
        showBorder = showBorder,
    )
}
