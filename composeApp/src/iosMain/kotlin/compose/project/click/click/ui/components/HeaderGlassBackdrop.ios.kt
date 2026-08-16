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

/**
 * Tab-root chrome uses the host-view UINavigationBar. This backdrop is only for leftover
 * compact headers. On iOS 26, skip UIKitView entirely — that overlay class paints over Compose.
 */
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
    if (usesNativeLiquidGlass && !reduceTransparency) {
        return
    }
    val backdropAlpha = 0.55f + 0.3f * fraction
    val blurStyle =
        if (isDarkMode) {
            UIBlurEffectStyle.UIBlurEffectStyleSystemThinMaterialDark
        } else {
            UIBlurEffectStyle.UIBlurEffectStyleSystemThinMaterialLight
        }

    if (reduceTransparency) {
        Box(
            modifier =
                modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
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
            effectView.effect = UIBlurEffect.effectWithStyle(blurStyle)
            effectView.backgroundColor = UIColor.clearColor
        },
    )
}
