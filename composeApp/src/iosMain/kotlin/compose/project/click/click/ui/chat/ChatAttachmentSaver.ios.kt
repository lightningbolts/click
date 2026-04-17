@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package compose.project.click.click.ui.chat

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile

/**
 * Phase 2 — C6: drop the decrypted attachment into the app's Documents directory so the
 * caller can present a `UIActivityViewController` for share/save.
 *
 * iOS does not have a global "Downloads" folder — writing to the sandbox Documents folder
 * keeps the file accessible from the Files app (under "On My iPhone › Click") and lets the
 * user re-share it without re-downloading.
 */
actual fun saveDecryptedAttachmentToDownloads(
    bytes: ByteArray,
    fileName: String,
    mimeType: String,
): String? {
    if (bytes.isEmpty() || fileName.isBlank()) return null
    val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    val dirs = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true,
    )
    val docsDir = dirs.firstOrNull() as? String ?: return null
    val targetDir = "$docsDir/Click"
    val fm = NSFileManager.defaultManager
    if (!fm.fileExistsAtPath(targetDir)) {
        fm.createDirectoryAtPath(
            path = targetDir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }
    val path = "$targetDir/$safeName"

    return runCatching {
        bytes.usePinned { pinned ->
            val nsData = NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            if (nsData.writeToFile(path, atomically = true)) path else null
        }
    }.getOrNull()
}
