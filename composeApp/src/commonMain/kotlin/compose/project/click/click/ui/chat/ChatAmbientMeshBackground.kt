package compose.project.click.click.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.isSystemInDarkTheme
import compose.project.click.click.data.models.Connection

/** Test tag for Compose UI tests (visibility / bounds). */
internal const val ChatAmbientMeshTestTag = "chat_ambient_mesh_layer"

/** Test tag for the blurred chrome plate behind the connection chat header. */
internal const val ChatGlassHeaderPlateTestTag = "chat_glass_header_plate"

/** Test tag for the blurred chrome plate behind the message composer. */
internal const val ChatGlassComposerPlateTestTag = "chat_glass_composer_plate"

private const val MeshTintAlphaLight = 0.18f
private const val MeshTintAlphaDark = 0.22f
private const val MeshAnimDurationMs = 26_000

/**
 * Extremely subtle animated radial gradient tint over [MaterialTheme.colorScheme.background].
 * Offset drifts slowly via [rememberInfiniteTransition] + [graphicsLayer] so brush rebuilds
 * stay tied to a single float driver.
 */
@Composable
internal fun ChatAmbientMeshBackground(
    connection: Connection?,
    isHubNeutral: Boolean,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val base = MaterialTheme.colorScheme.background
    val triples = remember(connection?.id, isHubNeutral) {
        if (isHubNeutral) ChatAmbientColorSeeds.hubNeutralRgbTriples()
        else ChatAmbientColorSeeds.rgbTriples01(connection)
    }
    val c0 = remember(triples, dark) { tripleToTintColor(triples[0], dark) }
    val c1 = remember(triples, dark) { tripleToTintColor(triples[1], dark) }
    val c2 = remember(triples, dark) { tripleToTintColor(triples[2], dark) }
    val alpha = if (dark) MeshTintAlphaDark else MeshTintAlphaLight

    val transition = rememberInfiniteTransition(label = "chat_ambient_mesh")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(MeshAnimDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mesh_shift",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(ChatAmbientMeshTestTag)
            .graphicsLayer {
                // Isolate mesh tint from parent recompositions where possible.
                clip = false
            }
            .drawBehind {
                drawRect(base)
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@drawBehind
                val cx = w * (0.32f + 0.36f * shift)
                val cy = h * (0.28f + 0.44f * (1f - shift))
                val r = (w.coerceAtLeast(h)) * 0.92f
                val brush = Brush.radialGradient(
                    colors = listOf(
                        c0.copy(alpha = alpha),
                        c1.copy(alpha = alpha * 0.65f),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = r,
                )
                drawRect(brush)
                val cx2 = w * (0.78f - 0.2f * shift)
                val cy2 = h * (0.72f - 0.3f * shift)
                val brush2 = Brush.radialGradient(
                    colors = listOf(
                        c2.copy(alpha = alpha * 0.55f),
                        Color.Transparent,
                    ),
                    center = Offset(cx2, cy2),
                    radius = r * 0.75f,
                )
                drawRect(brush2)
            },
    ) {}
}

private fun tripleToTintColor(t: Triple<Float, Float, Float>, dark: Boolean): Color {
    val boost = if (dark) 0.08f else 0f
    return Color(
        red = (t.first + boost).coerceIn(0f, 1f),
        green = (t.second + boost).coerceIn(0f, 1f),
        blue = (t.third + boost).coerceIn(0f, 1f),
    )
}
