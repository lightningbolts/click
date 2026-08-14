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
import compose.project.click.click.platform.rememberReduceTransparencyEnabled // pragma: allowlist secret

@Composable
actual fun HeaderGlassBackdrop(
    modifier: Modifier,
    collapseFraction: Float,
) {
    val reduceTransparency = rememberReduceTransparencyEnabled()
    val fraction = collapseFraction.coerceIn(0f, 1f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val alpha = 0.55f + 0.3f * fraction

    Box(
        modifier =
            modifier
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= 31 && !reduceTransparency) {
                        renderEffect = BlurEffect(24f, 24f, TileMode.Clamp)
                    }
                }.background(surfaceColor.copy(alpha = alpha)),
    )
}
