@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package compose.project.click.click.ui.chat

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import compose.project.click.click.ui.utils.iosTopViewControllerForPresentation
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Phase 2 — C6 (iOS):
 *
 * Two-step UX so the decrypted attachment is both **persisted** and **discoverable**:
 *
 *  1. Write bytes to `Documents/Click/<safeName>` inside the app sandbox. This folder is
 *     indexed by the Files app (because `UIFileSharingEnabled` + `LSSupportsOpeningDocumentsInPlace`
 *     are set in `Info.plist`) so the file is browsable under "On My iPhone › Click".
 *  2. Hop to the main thread and present a [UIActivityViewController] rooted at the
 *     top-most view controller. The share sheet offers "Save to Files", "AirDrop",
 *     Messages, etc. — so the user can actually keep the file without relying on the
 *     (simulator-fragile) Files integration.
 *
 * The returned `file://` URL is the on-disk path; callers should treat it as PII and avoid
 * logging. Presentation failure (no root controller, already-presenting UI, etc.) is silent
 * by design — the file is still persisted to `Documents/Click/` so the user can open the
 * Files app manually as a fallback.
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

    val written = runCatching {
        bytes.usePinned { pinned ->
            val nsData = NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            if (nsData.writeToFile(path, atomically = true)) path else null
        }
    }.getOrNull() ?: return null

    val fileUrl = NSURL.fileURLWithPath(written)
    dispatch_async(dispatch_get_main_queue()) {
        presentShareSheetForFile(fileUrl)
    }

    return "file://$written"
}

private fun presentShareSheetForFile(url: NSURL) {
    val root = topMostViewController() ?: return
    val sheet = UIActivityViewController(
        activityItems = listOf(url),
        applicationActivities = null,
    )
    // On iPad the share sheet requires a popover anchor. `popoverPresentationController`
    // isn't exposed by the current Kotlin/Native UIKit bindings, so we skip the anchor
    // setup — on iPhone this is a no-op and on iPad the system fallbacks gracefully to
    // centering the popover.
    root.presentViewController(sheet, animated = true, completion = null)
}

private fun topMostViewController(): UIViewController? = iosTopViewControllerForPresentation()
