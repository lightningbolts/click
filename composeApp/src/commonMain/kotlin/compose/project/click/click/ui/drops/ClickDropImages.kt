@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.drops

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import compose.project.click.click.collaboration.ClickDropDevelopState
import compose.project.click.click.collaboration.ClickDropPixelation
import compose.project.click.click.hapticSmallCommit
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Click Drop rendering (sheet 07 G2-2; iOS `ClickDropDevelopEffect.swift`). Pixelation is a tiny
 * bitmap scaled up with `FilterQuality.None` on a plain Canvas — no RenderEffect, which re-rasters
 * every frame and flickers under scroll and swipe.
 */

/** Develop layers, coarse to fine, that bloom from the centre before the photo itself. */
private val DEVELOP_LAYERS = listOf(12, 24, 48, 96)
private const val DEVELOP_DURATION_MS = 900

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
 * The develop moment: 12/24/48/96-block layers and then the photo bloom from the centre over about
 * 0.9 s with a light haptic. With reduce motion on, the pixels cross-fade to the photo instead.
 */
@Composable
fun ClickDropDevelopEffect(
    original: ImageBitmap,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit,
) {
    val finished by rememberUpdatedState(onFinished)
    val progress = remember(original) { Animatable(0f) }
    val layers = remember(original) { DEVELOP_LAYERS.map { original.downscaledForBlocks(it) } }
    LaunchedEffect(original) {
        hapticSmallCommit()
        progress.animateTo(1f, tween(if (reduceMotion) 300 else DEVELOP_DURATION_MS, easing = LinearOutSlowInEasing))
        finished()
    }
    if (reduceMotion) {
        Box(modifier) {
            Image(layers.first(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.None)
            Image(
                original,
                "Photo",
                Modifier.fillMaxSize().graphicsLayer { alpha = progress.value },
                contentScale = ContentScale.Crop,
            )
        }
        return
    }
    Canvas(modifier) {
        val p = progress.value
        drawCropped(layers.first(), FilterQuality.None)
        val maxRadius = hypot(size.width, size.height) / 2f
        val stages = layers.drop(1) + original
        stages.forEachIndexed { index, image ->
            // Each finer stage starts a little after the coarser one and grows to cover the frame.
            val start = index * 0.16f
            val t = ((p - start) / (1f - start)).coerceIn(0f, 1f)
            if (t <= 0f) return@forEachIndexed
            val path = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, maxRadius * t)) }
            clipPath(path) { drawCropped(image, if (image === original) FilterQuality.Medium else FilterQuality.None) }
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
