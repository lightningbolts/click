package compose.project.click.click.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

actual suspend fun compressOutgoingChatImageForUpload(bytes: ByteArray, mimeType: String): ByteArray =
    withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching bytes

            var sample = 1
            while (
                max(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_OUTGOING_CHAT_IMAGE_PX * 2
            ) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                ?: return@runCatching bytes

            val bitmapToEncode = try {
                val w = decoded.width
                val h = decoded.height
                val longEdge = max(w, h)
                if (longEdge <= MAX_OUTGOING_CHAT_IMAGE_PX) {
                    decoded
                } else {
                    val scale = MAX_OUTGOING_CHAT_IMAGE_PX.toFloat() / longEdge
                    val nw = max(1, (w * scale).roundToInt())
                    val nh = max(1, (h * scale).roundToInt())
                    val resized = Bitmap.createScaledBitmap(decoded, nw, nh, true)
                    if (resized != decoded) decoded.recycle()
                    resized
                }
            } catch (t: Throwable) {
                decoded.recycle()
                throw t
            }

            try {
                ByteArrayOutputStream().use { bos ->
                    val ok = bitmapToEncode.compress(
                        Bitmap.CompressFormat.JPEG,
                        OUTGOING_CHAT_JPEG_QUALITY_PERCENT,
                        bos,
                    )
                    if (!ok) return@runCatching bytes
                    bos.toByteArray().takeIf { it.isNotEmpty() } ?: bytes
                }
            } finally {
                bitmapToEncode.recycle()
            }
        }.getOrElse { bytes }
    }
