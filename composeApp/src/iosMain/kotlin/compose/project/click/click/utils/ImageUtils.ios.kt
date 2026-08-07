package compose.project.click.click.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import kotlin.math.max

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    return Image.makeFromEncoded(this).toComposeImageBitmap()
}

actual fun ByteArray.toChatDisplayImageBitmap(maxEdgePx: Int): ImageBitmap {
    val edge = maxEdgePx.coerceAtLeast(64)
    val full = Image.makeFromEncoded(this)
    val srcEdge = max(full.width, full.height).coerceAtLeast(1)
    if (srcEdge <= edge) {
        return full.toComposeImageBitmap()
    }
    val scale = edge.toFloat() / srcEdge.toFloat()
    val tw = (full.width * scale).toInt().coerceAtLeast(1)
    val th = (full.height * scale).toInt().coerceAtLeast(1)
    val surface = Surface.makeRasterN32Premul(tw, th)
    surface.canvas.drawImageRect(
        full,
        Rect.makeWH(full.width.toFloat(), full.height.toFloat()),
        Rect.makeWH(tw.toFloat(), th.toFloat()),
        SamplingMode.LINEAR,
        null,
        true,
    )
    return surface.makeImageSnapshot().toComposeImageBitmap()
}

actual fun ImageBitmap.softBlurredForLockedDrop(): ImageBitmap {
    val src = asSkiaBitmap()
    // Hard pixelation — nearest-neighbor downscale then upscale so locked drops stay unreadable
    // and match Android (filter=false). Aggressive factor keeps warm/cold cache appearance consistent.
    val tw = (src.width / 128).coerceAtLeast(1)
    val th = (src.height / 128).coerceAtLeast(1)
    val nearest: SamplingMode = FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE)
    val srcImage = Image.makeFromBitmap(src)
    val tinySurface = Surface.makeRasterN32Premul(tw, th)
    tinySurface.canvas.drawImageRect(
        srcImage,
        Rect.makeWH(src.width.toFloat(), src.height.toFloat()),
        Rect.makeWH(tw.toFloat(), th.toFloat()),
        nearest,
        null,
        true,
    )
    val tiny = tinySurface.makeImageSnapshot()
    val upSurface = Surface.makeRasterN32Premul(src.width.coerceAtLeast(1), src.height.coerceAtLeast(1))
    upSurface.canvas.drawImageRect(
        tiny,
        Rect.makeWH(tw.toFloat(), th.toFloat()),
        Rect.makeWH(src.width.toFloat(), src.height.toFloat()),
        nearest,
        null,
        true,
    )
    return upSurface.makeImageSnapshot().toComposeImageBitmap()
}
