package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.Dp
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIColor
import platform.UIKit.UIVisualEffectView
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
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
    val blurStyle = remember(useDarkMaterial) {
        val style = if (useDarkMaterial) {
            UIBlurEffectStyle.UIBlurEffectStyleSystemThinMaterialDark
        } else {
            UIBlurEffectStyle.UIBlurEffectStyleSystemThinMaterial
        }
        UIBlurEffect.effectWithStyle(style)
    }
    val cornerRadiusPx = cornerRadius.value

    UIKitView(
        modifier = modifier,
        factory = {
            val effectView = UIVisualEffectView(blurStyle)
            effectView.clipsToBounds = true
            if (showBorder) {
                effectView.layer.borderWidth = 0.5
                effectView.layer.borderColor = UIColor.whiteColor.colorWithAlphaComponent(0.12).CGColor
            }
            GlassEffectHostView(effectView)
        },
        update = { host ->
            val effectView = host.effectView
            effectView.effect = blurStyle
            val radius = cornerRadiusPx.toDouble()
            effectView.layer.cornerRadius = radius
            effectView.layer.masksToBounds = true
            if (showBorder) {
                effectView.layer.borderWidth = 0.5
                effectView.layer.borderColor = UIColor.whiteColor.colorWithAlphaComponent(0.12).CGColor
            } else {
                effectView.layer.borderWidth = 0.0
            }
            host.setNeedsLayout()
        },
        onRelease = { host ->
            host.effectView.removeFromSuperview()
        },
    )
}

@OptIn(ExperimentalForeignApi::class)
private class GlassEffectHostView(
    val effectView: UIVisualEffectView,
) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    init {
        addSubview(effectView)
        clipsToBounds = true
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        effectView.setFrame(bounds)
    }
}
