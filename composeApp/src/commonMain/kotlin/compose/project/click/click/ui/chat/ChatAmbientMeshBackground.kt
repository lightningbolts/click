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

/** Frozen mesh phase for [animateMesh] = false (chat surfaces — avoids N× infinite transitions in lists). */
private const val StaticMeshShift = 0.5f

/**
 * Extremely subtle radial gradient tint over [MaterialTheme.colorScheme.background].
 *
 * @param animateMesh When false (default for chat), uses a fixed phase — critical for keyboard
 * performance because each visible sent bubble used to run its own 26s infinite animation.
 */
@Composable
fun ChatAmbientMeshBackground(
    connection: Connection?,
    isHubNeutral: Boolean,
    modifier: Modifier = Modifier,
    animateMesh: Boolean = false,
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

    if (animateMesh) {
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
        ChatAmbientMeshLayer(
            modifier = modifier,
            base = base,
            c0 = c0,
            c1 = c1,
            c2 = c2,
            alpha = alpha,
            shift = shift,
        )
    } else {
        ChatAmbientMeshLayer(
            modifier = modifier,
            base = base,
            c0 = c0,
            c1 = c1,
            c2 = c2,
            alpha = alpha,
            shift = StaticMeshShift,
        )
    }
}

@Composable
private fun ChatAmbientMeshLayer(
    modifier: Modifier,
    base: Color,
    c0: Color,
    c1: Color,
    c2: Color,
    alpha: Float,
    shift: Float,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(ChatAmbientMeshTestTag)
            .graphicsLayer {
                clip = true
            }
            .drawBehind {
                drawChatAmbientMesh(
                    base = base,
                    c0 = c0,
                    c1 = c1,
                    c2 = c2,
                    alpha = alpha,
                    shift = shift,
                    width = size.width,
                    height = size.height,
                )
            },
    ) {}
}

/**
 * Static tint behind outbound bubbles — same palette as [ChatAmbientMeshBackground] without
 * [rememberInfiniteTransition] (one per LazyColumn row was a major keyboard-jank source).
 */
@Composable
internal fun ChatBubbleSentMeshTint(
    connection: Connection?,
    isHubNeutral: Boolean,
    modifier: Modifier = Modifier,
) {
    ChatAmbientMeshBackground(
        connection = connection,
        isHubNeutral = isHubNeutral,
        modifier = modifier,
        animateMesh = false,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChatAmbientMesh(
    base: Color,
    c0: Color,
    c1: Color,
    c2: Color,
    alpha: Float,
    shift: Float,
    width: Float,
    height: Float,
) {
    drawRect(base)
    val w = width
    val h = height
    if (w <= 0f || h <= 0f) return
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
    val floorBrush = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            c1.copy(alpha = alpha * 0.35f),
            c2.copy(alpha = alpha * 0.28f),
            c0.copy(alpha = alpha * 0.18f),
        ),
        startY = h * 0.45f,
        endY = h * 1.08f,
    )
    drawRect(floorBrush)
}

private fun tripleToTintColor(t: Triple<Float, Float, Float>, dark: Boolean): Color {
    val boost = if (dark) 0.08f else 0f
    return Color(
        red = (t.first + boost).coerceIn(0f, 1f),
        green = (t.second + boost).coerceIn(0f, 1f),
        blue = (t.third + boost).coerceIn(0f, 1f),
    )
}
