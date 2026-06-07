package compose.project.click.click.ui.camera

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.ColorMatrix
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface

actual suspend fun applyDisposableRollFilterToJpeg(bytes: ByteArray, filterIndex: Int): ByteArray =
    withContext(Dispatchers.Default) {
        if (filterIndex <= 0 || bytes.isEmpty()) return@withContext bytes
        runCatching {
            val image = Image.makeFromEncoded(bytes) ?: return@runCatching bytes
            val surface = Surface.makeRasterN32Premul(image.width, image.height)
            try {
                val canvas = surface.canvas
                val composeMatrix = DisposableRollFilters.matrixFor(filterIndex).values
                val skiaMatrix = ColorMatrix(
                    composeMatrix[0], composeMatrix[1], composeMatrix[2], composeMatrix[3], composeMatrix[4],
                    composeMatrix[5], composeMatrix[6], composeMatrix[7], composeMatrix[8], composeMatrix[9],
                    composeMatrix[10], composeMatrix[11], composeMatrix[12], composeMatrix[13], composeMatrix[14],
                    composeMatrix[15], composeMatrix[16], composeMatrix[17], composeMatrix[18], composeMatrix[19],
                )
                val paint = Paint().apply {
                    colorFilter = ColorFilter.makeMatrix(skiaMatrix)
                }
                canvas.drawImage(image, 0f, 0f, paint)
                val snapshot = surface.makeImageSnapshot()
                val encoded = snapshot.encodeToData(EncodedImageFormat.JPEG, 88)
                encoded?.bytes?.takeIf { it.isNotEmpty() } ?: bytes
            } finally {
                surface.close()
                image.close()
            }
        }.getOrElse { bytes }
    }
