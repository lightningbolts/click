@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.viewinterop.UIKitView
import compose.project.click.click.platform.rememberReduceTransparencyEnabled // pragma: allowlist secret
import compose.project.click.click.ui.theme.LocalIsDarkMode // pragma: allowlist secret
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIColor
import platform.UIKit.UIVisualEffectView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun HeaderGlassBackdrop(
    modifier: Modifier,
    collapseFraction: Float,
) {
    val fraction = collapseFraction.coerceIn(0f, 1f)
    val isDarkMode = LocalIsDarkMode.current
    val reduceTransparency = rememberReduceTransparencyEnabled()
    val usesNativeLiquidGlass =
        remember {
            NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 26 }
        }
    val backdropAlpha = 0.55f + 0.3f * fraction
    val accessibleMaterial =
        remember(isDarkMode) {
            if (isDarkMode) {
                UIColor.colorWithRed(0x10 / 255.0, green = 0x12 / 255.0, blue = 0x12 / 255.0, alpha = 0.96)
            } else {
                UIColor.colorWithRed(0xF9 / 255.0, green = 0xF9 / 255.0, blue = 0xF9 / 255.0, alpha = 0.96)
            }
        }
    val blurStyle =
        if (isDarkMode) {
            UIBlurEffectStyle.UIBlurEffectStyleSystemMaterialDark
        } else {
            UIBlurEffectStyle.UIBlurEffectStyleSystemMaterialLight
        }

    if (reduceTransparency) {
        Box(
            modifier =
                modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        )
        return
    }

    if (usesNativeLiquidGlass) {
        Box(
            modifier =
                modifier
                    .alpha(backdropAlpha)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)),
        )
        return
    }

    UIKitView(
        factory = {
            UIVisualEffectView(effect = UIBlurEffect.effectWithStyle(blurStyle)).apply {
                backgroundColor = UIColor.clearColor
            }
        },
        modifier = modifier.alpha(backdropAlpha),
        update = { view ->
            val effectView = view as? UIVisualEffectView ?: return@UIKitView
            if (reduceTransparency) {
                effectView.effect = null
                effectView.backgroundColor = accessibleMaterial
            } else {
                effectView.effect = UIBlurEffect.effectWithStyle(blurStyle)
                effectView.backgroundColor = UIColor.clearColor
            }
        },
    )
}
