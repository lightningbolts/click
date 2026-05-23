package compose.project.click.click.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * Material 3 "Liquid Glass" pill used for floating map overlays (e.g. the memories count).
 *
 * Rendering strategy per the 2026-04-16 spec review:
 *   * Android API 31+ + iOS / Desktop — the caller is free to stack [Modifier.blur] on top before
 *     invoking this composable to produce a true backdrop blur. We do **not** blur internally
 *     because the blur has to sample the map tiles *behind* the pill, which only the caller
 *     has in scope.
 *   * **Android < API 31** — `Modifier.blur` is a no-op on older devices (it only compiles;
 *     runtime requires Android 12+). We ship a **frosted-tint + procedural noise** fallback that
 *     looks intentional at small sizes: a translucent gradient fill plus a faint noise canvas so
 *     the surface reads as glass rather than flat plastic. The fallback runs on every platform
 *     unconditionally so the noise is baked in regardless of whether backdrop blur is available,
 *     matching the requirement that we do **not** restrict the app to API 31+.
 *
 * Usage:
 *   LiquidGlassPill(
 *     modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
 *   ) { Text("12 memories") }
 */
@Composable
fun LiquidGlassPill(
    modifier: Modifier = Modifier,
    cornerRadiusDp: Int = 24,
    noiseDensity: Float = 0.04f,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadiusDp.dp)
    val scheme = MaterialTheme.colorScheme

    val baseGradient = remember(scheme) {
        Brush.verticalGradient(
            colors = listOf(
                scheme.surface.copy(alpha = 0.58f),
                scheme.surface.copy(alpha = 0.34f),
            ),
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(baseGradient)
            .border(1.dp, scheme.onSurface.copy(alpha = 0.08f), shape),
    ) {
        // Procedural noise overlay — keeps the surface reading as glass on every platform.
        Canvas(modifier = Modifier.matchParentSize()) {
            val density = noiseDensity.coerceIn(0.0f, 0.2f)
            val total = (size.width * size.height * density).toInt().coerceAtMost(4_000)
            val rng = Random(seed = (size.width.toInt() xor size.height.toInt()))
            for (i in 0 until total) {
                val x = rng.nextFloat() * size.width
                val y = rng.nextFloat() * size.height
                val dotAlpha = 0.02f + rng.nextFloat() * 0.06f
                drawCircle(
                    color = Color.White.copy(alpha = dotAlpha),
                    radius = 0.5f,
                    center = Offset(x, y),
                )
            }
        }

        Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            content()
        }
    }
}
