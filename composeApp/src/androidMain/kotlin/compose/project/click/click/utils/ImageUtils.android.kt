package compose.project.click.click.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    return BitmapFactory.decodeByteArray(this, 0, size).asImageBitmap()
}

actual fun ImageBitmap.softBlurredForLockedDrop(): ImageBitmap {
    val src = asAndroidBitmap()
    // Aggressive pixelation — locked drops must not retain recognizable detail.
    val tw = (src.width / 56).coerceAtLeast(1)
    val th = (src.height / 56).coerceAtLeast(1)
    val tiny = Bitmap.createScaledBitmap(src, tw, th, true)
    val midW = (src.width / 12).coerceAtLeast(1)
    val midH = (src.height / 12).coerceAtLeast(1)
    val mid = Bitmap.createScaledBitmap(tiny, midW, midH, true)
    val up = Bitmap.createScaledBitmap(mid, src.width.coerceAtLeast(1), src.height.coerceAtLeast(1), true)
    if (tiny !== src && !tiny.isRecycled) tiny.recycle()
    if (mid !== tiny && mid !== up && !mid.isRecycled) mid.recycle()
    return up.asImageBitmap()
}
