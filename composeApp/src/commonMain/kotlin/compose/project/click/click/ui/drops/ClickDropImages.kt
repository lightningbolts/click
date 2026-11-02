@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.drops

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import compose.project.click.click.collaboration.ClickDropBloom
import compose.project.click.click.collaboration.ClickDropDevelopState
import compose.project.click.click.collaboration.ClickDropPixelation
import compose.project.click.click.hapticSmallCommit
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Click Drop rendering (iOS `Click/DesignSystem/Components/ClickDropDevelopEffect.swift`). Pixelation is a tiny
 * bitmap scaled up with `FilterQuality.None` on a plain Canvas — no RenderEffect, which re-rasters
 * every frame and flickers under scroll and swipe.
 */

/** [this] reduced so its longest side has [blocks] pixels (one per block). */
fun ImageBitmap.downscaledForBlocks(blocks: Int = ClickDropPixelation.BLOCKS_PER_SIDE): ImageBitmap {
    val longest = max(width, height).coerceAtLeast(1)
    val w = ClickDropPixelation.blockCount(width, longest, blocks).coerceAtLeast(1)
    val h = ClickDropPixelation.blockCount(height, longest, blocks).coerceAtLeast(1)
    val out = ImageBitmap(w, h)
    Canvas(out).drawImageRect(
        image = this,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(width, height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(w, h),
        paint = Paint().apply { filterQuality = FilterQuality.Medium },
    )
    return out
}

/** A photo shown as 12 blocks a side (pending and ready drops). */
@Composable
fun ClickDropPixelatedImage(
    bitmap: ImageBitmap,
    modifier: Modifier = Modifier,
    blocks: Int = ClickDropPixelation.BLOCKS_PER_SIDE,
    contentDescription: String? = "Click Drop, not developed yet",
) {
    val small = remember(bitmap, blocks) { bitmap.downscaledForBlocks(blocks) }
    Image(
        bitmap = small,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.None,
        modifier = modifier,
    )
}

/**
 * The develop moment: 12/24/48/96-block layers and then the photo bloom from the centre with soft
 * edges, each [ClickDropBloom.STAGGER_MS] after the one before (0.92 s in all, timing in
 * [ClickDropBloom]), with a light haptic. With reduce motion on, the pixels cross-fade to the photo.
 */
@Composable
fun ClickDropDevelopEffect(
    original: ImageBitmap,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit,
) {
    val finished by rememberUpdatedState(onFinished)
    // Milliseconds since the develop started (linear; each stage eases on its own).
    val elapsed = remember(original) { Animatable(0f) }
    val layers = remember(original) { ClickDropBloom.LAYER_BLOCKS.map { original.downscaledForBlocks(it) } }
    LaunchedEffect(original) {
        hapticSmallCommit()
        val total = if (reduceMotion) ClickDropBloom.REDUCED_FADE_MS else ClickDropBloom.DURATION_MS
        elapsed.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
        finished()
    }
    if (reduceMotion) {
        Box(modifier) {
            Image(layers.first(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.None)
            Image(
                original,
                "Photo",
                Modifier.fillMaxSize().graphicsLayer { alpha = elapsed.value / ClickDropBloom.REDUCED_FADE_MS },
                contentScale = ContentScale.Crop,
            )
        }
        return
    }
    Canvas(modifier) {
        val ms = elapsed.value.toLong()
        drawCropped(layers.first(), FilterQuality.None)
        val stages = layers.drop(1) + original
        stages.forEachIndexed { index, image ->
            val radius = ClickDropBloom.stageRadius(index, ms, size.width, size.height)
            if (radius <= 0f) return@forEachIndexed
            val photo = image === original
            // Draw the stage in its own layer, then keep only a soft-edged circle of it.
            drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
            drawCropped(image, if (photo) FilterQuality.Medium else FilterQuality.None)
            drawRect(
                brush =
                    Brush.radialGradient(
                        0f to Color.Black,
                        ClickDropBloom.SOFT_EDGE_START to Color.Black,
                        1f to Color.Transparent,
                        center = center,
                        radius = radius,
                    ),
                blendMode = BlendMode.DstIn,
            )
            drawContext.canvas.restore()
        }
    }
}

/** Draws [image] filling the canvas like `ContentScale.Crop`. */
private fun DrawScope.drawCropped(
    image: ImageBitmap,
    filterQuality: FilterQuality,
) {
    if (size.width <= 0f || size.height <= 0f) return
    val scale = max(size.width / image.width, size.height / image.height)
    val srcW = (size.width / scale).coerceAtMost(image.width.toFloat())
    val srcH = (size.height / scale).coerceAtMost(image.height.toFloat())
    val srcX = ((image.width - srcW) / 2f).roundToInt()
    val srcY = ((image.height - srcH) / 2f).roundToInt()
    drawImage(
        image = image,
        srcOffset = IntOffset(srcX, srcY),
        srcSize = IntSize(srcW.roundToInt().coerceAtLeast(1), srcH.roundToInt().coerceAtLeast(1)),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        filterQuality = filterQuality,
    )
}

/** "Develops in 3h 12m" for a pending drop; null otherwise. */
fun clickDropCountdownLabel(
    state: ClickDropDevelopState?,
    now: Instant = Clock.System.now(),
): String? {
    val pending = state as? ClickDropDevelopState.Pending ?: return null
    val reveal = pending.revealAt ?: return "Developing"
    val totalMin = ((reveal - now).inWholeSeconds.coerceAtLeast(0L) + 59) / 60
    val hours = totalMin / 60
    val mins = totalMin % 60
    return when {
        hours > 0 -> "Develops in ${hours}h ${mins}m"
        mins > 0 -> "Develops in ${mins}m"
        else -> "Develops in a moment"
    }
}

/**
 * The label over a gated chat drop that isn't on screen as a photo yet (iOS `ClickDropImageView.stateLabel`):
 * "Click Drop · develops in 3h 12m" while pending, "Tap to develop" when ready, "Developing…" in flight.
 */
fun chatClickDropLabel(
    state: ClickDropDevelopState,
    developing: Boolean,
    now: Instant = Clock.System.now(),
): String =
    when (state) {
        is ClickDropDevelopState.Pending ->
            if (state.revealAt == null) {
                "Click Drop"
            } else {
                "Click Drop · " + clickDropCountdownLabel(state, now).orEmpty().replaceFirstChar { it.lowercaseChar() }
            }
        ClickDropDevelopState.Ready -> if (developing) "Developing…" else "Tap to develop"
        ClickDropDevelopState.Developed -> "Developing…"
    }
