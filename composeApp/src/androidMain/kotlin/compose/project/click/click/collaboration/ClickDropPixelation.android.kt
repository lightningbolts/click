package compose.project.click.click.collaboration

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

actual suspend fun clickDropPreviewJpeg(bytes: ByteArray): ByteArray? =
    withContext(Dispatchers.Default) {
        runCatching {
            val (w, h) = encodedImageSize(bytes) ?: return@runCatching null
            var sample = 1
            while (max(w / (sample * 2), h / (sample * 2)) >= ClickDropPixelation.PREVIEW_MAX_DIMENSION) sample *= 2
            val decoded =
                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    },
                ) ?: return@runCatching null
            val (pw, ph) = ClickDropPixelation.previewSize(decoded.width, decoded.height)
            val small = if (pw == decoded.width && ph == decoded.height) decoded else Bitmap.createScaledBitmap(decoded, pw, ph, true)
            if (small !== decoded) decoded.recycle()
            val pixels = IntArray(pw * ph)
            small.getPixels(pixels, 0, pw, 0, 0, pw, ph)
            small.recycle()
            val out = Bitmap.createBitmap(ClickDropPixelation.pixelate(pixels, pw, ph), pw, ph, Bitmap.Config.ARGB_8888)
            try {
                ByteArrayOutputStream().use { bos ->
                    if (!out.compress(
                            Bitmap.CompressFormat.JPEG,
                            ClickDropPixelation.PREVIEW_JPEG_QUALITY,
                            bos,
                        )
                    ) {
                        null
                    } else {
                        bos.toByteArray()
                    }
                }
            } finally {
                out.recycle()
            }
        }.getOrNull()
    }

actual fun encodedImageSize(bytes: ByteArray): Pair<Int, Int>? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    return if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth to bounds.outHeight else null
}

