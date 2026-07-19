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
    val tw = (src.width / 18).coerceAtLeast(1)
    val th = (src.height / 18).coerceAtLeast(1)
    val surface = Surface.makeRasterN32Premul(tw, th)
    val canvas = surface.canvas
    canvas.scale(tw.toFloat() / src.width.toFloat(), th.toFloat() / src.height.toFloat())
    canvas.drawImage(Image.makeFromBitmap(src), 0f, 0f)
    val tiny = surface.makeImageSnapshot()
    val upSurface = Surface.makeRasterN32Premul(src.width.coerceAtLeast(1), src.height.coerceAtLeast(1))
    val upCanvas = upSurface.canvas
    upCanvas.scale(
        src.width.toFloat() / tw.toFloat(),
        src.height.toFloat() / th.toFloat(),
    )
    upCanvas.drawImage(tiny, 0f, 0f)
    return upSurface.makeImageSnapshot().toComposeImageBitmap()
}
