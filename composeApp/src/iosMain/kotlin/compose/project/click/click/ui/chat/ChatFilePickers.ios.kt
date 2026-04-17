package compose.project.click.click.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.uikit.LocalUIViewController
import compose.project.click.click.chat.attachments.ChatAttachmentValidator
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeContent
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

/**
 * iOS implementation of [rememberFilePicker] using `UIDocumentPickerViewController`. We pass an
 * array of UTType identifiers derived from [ChatAttachmentValidator.ALLOWED_MIME_TYPES] so the
 * system picker filters aggressively; unrecognised MIME types silently fall through to the
 * generic "content" type.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberFilePicker(
    onFilePicked: (PickedFile) -> Unit,
    onFilePickFailed: (String) -> Unit,
): () -> Unit {
    val viewController = LocalUIViewController.current
    val onFilePickedState by rememberUpdatedState(onFilePicked)
    val onFilePickFailedState by rememberUpdatedState(onFilePickFailed)

    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>,
            ) {
                controller.dismissViewControllerAnimated(true, completion = null)
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
                // Security-scoped resource access is required for items returned by the picker;
                // we read synchronously into memory so callers never see a path they can't open.
                val accessGranted = url.startAccessingSecurityScopedResource()
                try {
                    val data = NSData.dataWithContentsOfURL(url)
                    if (data == null) {
                        dispatch_async(dispatch_get_main_queue()) {
                            onFilePickFailedState(
                                "Couldn't read that file. Please try another document from the Files app.",
                            )
                        }
                        return
                    }
                    val bytes = data.toByteArray()
                    val name = url.lastPathComponent ?: "attachment"
                    val mime = mimeForFileName(name)
                    dispatch_async(dispatch_get_main_queue()) {
                        if (bytes.isNotEmpty()) {
                            onFilePickedState(PickedFile(bytes = bytes, fileName = name, mimeType = mime))
                        } else {
                            onFilePickFailedState("That file is empty.")
                        }
                    }
                } finally {
                    if (accessGranted) {
                        url.stopAccessingSecurityScopedResource()
                    }
                }
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                controller.dismissViewControllerAnimated(true, completion = null)
            }
        }
    }

    return {
        val allowedTypes: List<UTType> = ChatAttachmentValidator.ALLOWED_MIME_TYPES
            .mapNotNull { UTType.typeWithMIMEType(it) }
            .ifEmpty { listOf(UTTypeContent) }
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = allowedTypes)
        picker.delegate = delegate
        picker.allowsMultipleSelection = false
        viewController.presentViewController(picker, animated = true, completion = null)
    }
}

/**
 * Best-effort MIME lookup from the selected file's extension; iOS's document picker does not hand
 * us a MIME directly for every source. Falls back to `application/octet-stream` so the validator
 * still runs (it normalises unknown MIMEs to a skip).
 */
private fun mimeForFileName(fileName: String): String {
    val ext = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    if (ext.isEmpty()) return "application/octet-stream"
    val byUType = UTType.typeWithFilenameExtension(ext)?.preferredMIMEType
    if (!byUType.isNullOrBlank()) return byUType
    return when (ext) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "txt" -> "text/plain"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "mov" -> "video/quicktime"
        "mp4" -> "video/mp4"
        "zip" -> "application/zip"
        "csv" -> "text/csv"
        else -> "application/octet-stream"
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len == 0) return ByteArray(0)
    val out = ByteArray(len)
    val base = bytes ?: return ByteArray(0)
    memcpy(out.refTo(0), base, len.toULong())
    return out
}
