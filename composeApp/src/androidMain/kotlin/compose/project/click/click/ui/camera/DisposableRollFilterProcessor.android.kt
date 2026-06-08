package compose.project.click.click.ui.camera

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import compose.project.click.click.ui.chat.AndroidChatImageSaveContext
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun renderDisposableRollFilteredPreview(
    sourceBytes: ByteArray,
    filterIndex: Int,
): ImageBitmap? = withContext(Dispatchers.Default) {
    val context = AndroidChatImageSaveContext.applicationContext ?: return@withContext null
    runCatching {
        applyGpuImageFilter(
            context = context,
            sourceBytes = sourceBytes,
            filterIndex = filterIndex,
            previewMaxDimension = DISPOSABLE_ROLL_PREVIEW_MAX_DIMENSION,
        )?.asImageBitmap()
    }.getOrNull()
}

actual suspend fun applyDisposableRollFilterToJpeg(bytes: ByteArray, filterIndex: Int): ByteArray =
    withContext(Dispatchers.Default) {
        if (filterIndex <= 0 || bytes.isEmpty()) return@withContext bytes
        val context = AndroidChatImageSaveContext.applicationContext ?: return@withContext bytes
        runCatching {
            val bitmap = applyGpuImageFilter(context, bytes, filterIndex) ?: return@runCatching bytes
            ByteArrayOutputStream().use { bos ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, bos)) return@runCatching bytes
                bos.toByteArray().takeIf { it.isNotEmpty() } ?: bytes
            }
        }.getOrElse { bytes }
    }
