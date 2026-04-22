package compose.project.click.click.ui.chat

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableData
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.memcpy
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHPhotoLibrary
import compose.project.click.click.ui.utils.iosTopViewControllerForPresentation
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIImage
import platform.UIKit.UIImageWriteToSavedPhotosAlbum
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    val m = NSMutableData()
    m.setLength(size.convert())
    m.mutableBytes?.let { dest ->
        memcpy(dest, pinned.addressOf(0), size.convert())
    }
    m
}

actual suspend fun fetchImageBytesFromUrl(imageUrl: String): ByteArray? =
    withContext(Dispatchers.Default) {
        runCatching {
            val client = HttpClient(Darwin)
            try {
                val bytes = client.get(imageUrl).bodyAsBytes()
                bytes.takeIf { it.isNotEmpty() }
            } finally {
                client.close()
            }
        }.getOrNull()
    }

@OptIn(ExperimentalForeignApi::class)
actual fun shareDecryptedImage(imageBytes: ByteArray, fileName: String) {
    if (imageBytes.isEmpty()) return
    val safeName = fileName.trim().ifEmpty { "click_share.jpg" }.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val extFromName = safeName.substringAfterLast('.', "").lowercase()
    val ext = when (extFromName) {
        "jpg", "jpeg", "png", "webp", "heic" -> extFromName
        else -> "jpg"
    }
    val base = safeName.substringBeforeLast(".", safeName).trim('.').ifEmpty { "click_share" }.take(48)
    val path =
        NSTemporaryDirectory().trimEnd('/') + "/click_share_${kotlin.random.Random.nextLong()}_${base}.$ext"
    val f = fopen(path, "wb") ?: return
    val wroteOk = imageBytes.usePinned { pinned ->
        val n = fwrite(pinned.addressOf(0), 1u, imageBytes.size.toULong(), f)
        n == imageBytes.size.toULong()
    }
    fclose(f)
    if (!wroteOk) {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        return
    }
    val fileUrl = NSURL.fileURLWithPath(path) ?: run {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        return
    }
    // Share a file URL so the system sheet can build a proper image thumbnail (UIImage-only items
    // often render as a generic glyph in the activity row on iOS 17+).
    val activityViewController = UIActivityViewController(listOf(fileUrl), null)
    dispatch_async(dispatch_get_main_queue()) {
        val root = iosTopViewControllerForPresentation() ?: return@dispatch_async
        root.presentViewController(activityViewController, animated = true, completion = null)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun saveChatImageToGallery(
    imageUrl: String,
    decryptedImageBytes: ByteArray?,
    mimeTypeHint: String?,
): Result<Unit> = withContext(Dispatchers.Default) {
    runCatching {
        val bytes = if (decryptedImageBytes != null && decryptedImageBytes.isNotEmpty()) {
            decryptedImageBytes
        } else {
            val client = HttpClient(Darwin)
            try {
                client.get(imageUrl).bodyAsBytes()
            } finally {
                client.close()
            }
        }
        if (bytes.isEmpty()) error("Empty image response")

        val ext = when {
            mimeTypeHint?.contains("png", ignoreCase = true) == true -> "png"
            mimeTypeHint?.contains("webp", ignoreCase = true) == true -> "webp"
            else -> "jpg"
        }
        val path = NSTemporaryDirectory().trimEnd('/') + "/click_dl_${kotlin.random.Random.nextLong()}.$ext"
        val f = fopen(path, "wb") ?: error("fopen")
        bytes.usePinned { pinned ->
            val n = fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), f)
            if (n != bytes.size.toULong()) error("short write")
        }
        fclose(f)
        val image = UIImage.imageWithContentsOfFile(path) ?: error("Invalid image data")

        val authorized = suspendCoroutine { cont ->
            val status = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelAddOnly)
            when (status) {
                PHAuthorizationStatusAuthorized, PHAuthorizationStatusLimited -> cont.resume(true)
                else -> {
                    PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { newStatus ->
                        cont.resume(
                            newStatus == PHAuthorizationStatusAuthorized ||
                                newStatus == PHAuthorizationStatusLimited,
                        )
                    }
                }
            }
        }
        if (!authorized) error("Photo library access denied")

        withContext(Dispatchers.Main) {
            UIImageWriteToSavedPhotosAlbum(image, null, null, null)
        }
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        Unit
    }
}
