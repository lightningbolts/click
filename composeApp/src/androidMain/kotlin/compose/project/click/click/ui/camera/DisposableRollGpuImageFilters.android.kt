package compose.project.click.click.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageContrastFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageExposureFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGrayscaleFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSaturationFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSepiaToneFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageVignetteFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageWhiteBalanceFilter
import java.io.ByteArrayInputStream
import kotlin.math.max
import kotlin.math.roundToInt

internal const val DISPOSABLE_ROLL_PREVIEW_MAX_DIMENSION = 1280

internal fun gpuImageFilterForIndex(filterIndex: Int): GPUImageFilter? {
    return when (filterIndex) {
        0 -> null
        1 -> GPUImageWhiteBalanceFilter().apply { setTemperature(6200f) }
        2 -> GPUImageWhiteBalanceFilter().apply { setTemperature(4200f) }
        3 -> GPUImageSepiaToneFilter(0.82f)
        4 -> GPUImageContrastFilter().apply { setContrast(1.45f) }
        5 -> GPUImageFilterGroup(
            listOf(
                GPUImageExposureFilter().apply { setExposure(-0.35f) },
                GPUImageSaturationFilter().apply { setSaturation(0.72f) },
            ),
        )
        6 -> GPUImageGrayscaleFilter()
        7 -> GPUImageSaturationFilter().apply { setSaturation(1.65f) }
        8 -> GPUImageFilterGroup(
            listOf(
                GPUImageSepiaToneFilter(0.45f),
                GPUImageSaturationFilter().apply { setSaturation(1.18f) },
            ),
        )
        9 -> GPUImageFilterGroup(
            listOf(
                GPUImageVignetteFilter().apply {
                    setVignetteStart(0.35f)
                    setVignetteEnd(0.85f)
                },
                GPUImageContrastFilter().apply { setContrast(1.18f) },
            ),
        )
        else -> null
    }
}

internal fun decodeJpegWithExifOrientation(sourceBytes: ByteArray): Bitmap? {
    if (sourceBytes.isEmpty()) return null
    val bitmap = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size) ?: return null
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(sourceBytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    return rotateBitmapForExifOrientation(bitmap, orientation)
}

private fun rotateBitmapForExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        .also { rotated ->
            if (rotated !== bitmap) {
                bitmap.recycle()
            }
        }
}

internal fun downscaleBitmapForPreview(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val largestSide = max(bitmap.width, bitmap.height)
    if (largestSide <= maxDimension) return bitmap
    val scale = maxDimension.toFloat() / largestSide.toFloat()
    val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true).also { scaled ->
        if (scaled !== bitmap) {
            bitmap.recycle()
        }
    }
}

internal fun applyGpuImageFilter(
    context: Context,
    sourceBytes: ByteArray,
    filterIndex: Int,
    previewMaxDimension: Int? = null,
): Bitmap? {
    if (sourceBytes.isEmpty()) return null
    var source = decodeJpegWithExifOrientation(sourceBytes) ?: return null
    if (previewMaxDimension != null) {
        source = downscaleBitmapForPreview(source, previewMaxDimension)
    }
    if (filterIndex <= 0) return source
    val filter = gpuImageFilterForIndex(filterIndex) ?: return source
    val gpuImage = GPUImage(context)
    gpuImage.setImage(source)
    gpuImage.setFilter(filter)
    return gpuImage.bitmapWithFilterApplied
}
