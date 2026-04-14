package compose.project.click.click.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSMutableData
import platform.Foundation.NSData
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy
import kotlin.math.max
import kotlin.math.roundToInt

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

@OptIn(ExperimentalForeignApi::class)
actual suspend fun compressOutgoingChatImageForUpload(bytes: ByteArray, mimeType: String): ByteArray =
    withContext(Dispatchers.Default) {
        if (bytes.isEmpty()) return@withContext bytes
        runCatching {
            val data = bytes.toNSData()
            val image = UIImage.imageWithData(data) ?: return@runCatching bytes
            val w = image.size.useContents { width }
            val h = image.size.useContents { height }
            if (w <= 0.0 || h <= 0.0) return@runCatching bytes
            val longEdge = max(w, h)
            val target = MAX_OUTGOING_CHAT_IMAGE_PX.toDouble()
            val scale = if (longEdge <= target) 1.0 else target / longEdge
            val nw = max(1.0, w * scale)
            val nh = max(1.0, h * scale)
            val rw = nw.roundToInt().toDouble()
            val rh = nh.roundToInt().toDouble()

            val rendered = if (scale >= 1.0 - 1e-6) {
                image
            } else {
                UIGraphicsBeginImageContextWithOptions(
                    CGSizeMake(rw, rh),
                    false,
                    1.0,
                )
                try {
                    image.drawInRect(CGRectMake(0.0, 0.0, rw, rh))
                    UIGraphicsGetImageFromCurrentImageContext() ?: image
                } finally {
                    UIGraphicsEndImageContext()
                }
            }

            val jpeg = UIImageJPEGRepresentation(
                rendered,
                OUTGOING_CHAT_JPEG_QUALITY_PERCENT / 100.0,
            ) ?: return@runCatching bytes
            jpeg.toByteArray().takeIf { it.isNotEmpty() } ?: bytes
        }.getOrElse { bytes }
    }
