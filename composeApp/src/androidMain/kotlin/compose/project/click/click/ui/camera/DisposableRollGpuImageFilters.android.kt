package compose.project.click.click.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

internal fun applyGpuImageFilter(
    context: Context,
    sourceBytes: ByteArray,
    filterIndex: Int,
): Bitmap? {
    if (sourceBytes.isEmpty()) return null
    val source = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size) ?: return null
    if (filterIndex <= 0) return source
    val filter = gpuImageFilterForIndex(filterIndex) ?: return source
    val gpuImage = GPUImage(context)
    gpuImage.setImage(source)
    gpuImage.setFilter(filter)
    return gpuImage.bitmapWithFilterApplied
}
