package compose.project.click.click.ui.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Canvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

actual suspend fun applyDisposableRollFilterToJpeg(bytes: ByteArray, filterIndex: Int): ByteArray =
    withContext(Dispatchers.Default) {
        if (filterIndex <= 0 || bytes.isEmpty()) return@withContext bytes
        runCatching {
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@runCatching bytes
            val matrixValues = DisposableRollFilters.matrixFor(filterIndex).values
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(matrixValues)
            }
            val output = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
            try {
                Canvas(output).drawBitmap(decoded, 0f, 0f, paint)
            } finally {
                if (output != decoded) decoded.recycle()
            }
            try {
                ByteArrayOutputStream().use { bos ->
                    if (!output.compress(Bitmap.CompressFormat.JPEG, 88, bos)) return@runCatching bytes
                    bos.toByteArray().takeIf { it.isNotEmpty() } ?: bytes
                }
            } finally {
                output.recycle()
            }
        }.getOrElse { bytes }
    }
