@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import compose.project.click.click.platform.rememberReduceTransparencyEnabled // pragma: allowlist secret

@Composable
actual fun HeaderGlassBackdrop(
    modifier: Modifier,
    collapseFraction: Float,
) {
    val reduceTransparency = rememberReduceTransparencyEnabled()
    val fraction = collapseFraction.coerceIn(0f, 1f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val alpha = if (reduceTransparency) 0.96f else 0.55f + 0.3f * fraction
    val blurRadiusPx = with(LocalDensity.current) { 24.dp.toPx() }

    Box(
        modifier =
            modifier
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= 31 && !reduceTransparency) {
                        renderEffect = BlurEffect(blurRadiusPx, blurRadiusPx, TileMode.Clamp)
                    }
                }.background(surfaceColor.copy(alpha = alpha)),
    )
}
