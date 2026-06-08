package compose.project.click.click.ui.camera

import androidx.compose.ui.graphics.ImageBitmap
import click.disposableroll.filter.ClickApplyDisposableRollPhotoEffect
import compose.project.click.click.utils.toImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSMutableData
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len == 0) return ByteArray(0)
    val out = ByteArray(len)
    val base = bytes ?: return ByteArray(0)
    out.usePinned { op ->
        memcpy(op.addressOf(0), base, len.toULong())
    }
    return out
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    val m = NSMutableData()
    m.setLength(size.convert())
    m.mutableBytes?.let { dest ->
        memcpy(dest, pinned.addressOf(0), size.convert())
    }
    m
}

internal const val DISPOSABLE_ROLL_PREVIEW_MAX_DIMENSION = 1280

@OptIn(ExperimentalForeignApi::class)
private fun applyCoreImageFilter(
    sourceBytes: ByteArray,
    filterIndex: Int,
    previewMaxDimension: Int = 0,
): ByteArray {
    if (filterIndex <= 0 || sourceBytes.isEmpty()) return sourceBytes
    val filtered = ClickApplyDisposableRollPhotoEffect(
        sourceBytes.toNSData(),
        filterIndex,
        previewMaxDimension,
    )
    return filtered.toByteArray().takeIf { it.isNotEmpty() } ?: sourceBytes
}

actual suspend fun renderDisposableRollFilteredPreview(
    sourceBytes: ByteArray,
    filterIndex: Int,
): ImageBitmap? = withContext(Dispatchers.Default) {
    runCatching {
        applyCoreImageFilter(
            sourceBytes = sourceBytes,
            filterIndex = filterIndex,
            previewMaxDimension = DISPOSABLE_ROLL_PREVIEW_MAX_DIMENSION,
        ).toImageBitmap()
    }.getOrNull()
}

actual suspend fun applyDisposableRollFilterToJpeg(bytes: ByteArray, filterIndex: Int): ByteArray =
    withContext(Dispatchers.Default) {
        runCatching {
            applyCoreImageFilter(bytes, filterIndex)
        }.getOrElse { bytes }
    }
