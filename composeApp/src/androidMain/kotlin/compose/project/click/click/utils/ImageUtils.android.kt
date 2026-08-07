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
    // Hard pixelation (nearest-neighbor upscale) — locked drops must not retain recognizable detail.
    // /128 keeps cold-start and warm-cache appearance equally blocky (old soft paths looked too clear).
    val tw = (src.width / 128).coerceAtLeast(1)
    val th = (src.height / 128).coerceAtLeast(1)
    val tiny = Bitmap.createScaledBitmap(src, tw, th, /* filter= */ false)
    val up = Bitmap.createScaledBitmap(
        tiny,
        src.width.coerceAtLeast(1),
        src.height.coerceAtLeast(1),
        /* filter= */ false,
    )
    if (tiny !== src && !tiny.isRecycled) tiny.recycle()
    return up.asImageBitmap()
}
