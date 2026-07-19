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
    val tw = (src.width / 18).coerceAtLeast(1)
    val th = (src.height / 18).coerceAtLeast(1)
    val tiny = Bitmap.createScaledBitmap(src, tw, th, true)
    val up = Bitmap.createScaledBitmap(tiny, src.width.coerceAtLeast(1), src.height.coerceAtLeast(1), true)
    if (tiny !== src && tiny !== up) tiny.recycle()
    return up.asImageBitmap()
}
