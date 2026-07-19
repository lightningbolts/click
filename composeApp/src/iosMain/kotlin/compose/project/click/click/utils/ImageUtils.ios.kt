package compose.project.click.click.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    return Image.makeFromEncoded(this).toComposeImageBitmap()
}

actual fun ImageBitmap.softBlurredForLockedDrop(): ImageBitmap {
    val src = asSkiaBitmap()
    // Aggressive pixelation — locked drops must not retain recognizable detail.
    val tw = (src.width / 56).coerceAtLeast(1)
    val th = (src.height / 56).coerceAtLeast(1)
    val tinySurface = Surface.makeRasterN32Premul(tw, th)
    tinySurface.canvas.apply {
        scale(tw.toFloat() / src.width.toFloat(), th.toFloat() / src.height.toFloat())
        drawImage(Image.makeFromBitmap(src), 0f, 0f)
    }
    val tiny = tinySurface.makeImageSnapshot()
    val midW = (src.width / 12).coerceAtLeast(1)
    val midH = (src.height / 12).coerceAtLeast(1)
    val midSurface = Surface.makeRasterN32Premul(midW, midH)
    midSurface.canvas.apply {
        scale(midW.toFloat() / tw.toFloat(), midH.toFloat() / th.toFloat())
        drawImage(tiny, 0f, 0f)
    }
    val mid = midSurface.makeImageSnapshot()
    val upSurface = Surface.makeRasterN32Premul(src.width.coerceAtLeast(1), src.height.coerceAtLeast(1))
    upSurface.canvas.apply {
        scale(src.width.toFloat() / midW.toFloat(), src.height.toFloat() / midH.toFloat())
        drawImage(mid, 0f, 0f)
    }
    return upSurface.makeImageSnapshot().toComposeImageBitmap()
}
