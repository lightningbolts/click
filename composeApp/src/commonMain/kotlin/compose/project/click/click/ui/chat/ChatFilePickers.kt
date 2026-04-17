package compose.project.click.click.ui.chat

import androidx.compose.runtime.Composable

/**
 * Result of a successful "pick arbitrary file" interaction (C4 — Phase 2).
 *
 * Both platforms surface the raw bytes + a best-effort MIME type + the original filename so the
 * validator (`ChatAttachmentValidator`) can gate the selection client-side before we spend cycles
 * encrypting + uploading. Bytes are read into memory because attachments are capped at 2 MiB — if
 * that ceiling ever grows we should switch to a streaming pipeline.
 */
data class PickedFile(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PickedFile) return false
        if (!bytes.contentEquals(other.bytes)) return false
        if (fileName != other.fileName) return false
        if (mimeType != other.mimeType) return false
        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}

/**
 * Opens the native "pick a document" UI. Returns a `() -> Unit` launcher so the caller can fire it
 * from an `onClick`. The [onFilePicked] callback fires on a main-safe coroutine with the decoded
 * bytes; [onFilePickFailed] surfaces a user-readable message (permissions, unreadable URI, etc.).
 *
 * Caller is responsible for validating the [PickedFile] via `ChatAttachmentValidator.validate`
 * **before** encrypting or uploading anything.
 */
@Composable
expect fun rememberFilePicker(
    onFilePicked: (PickedFile) -> Unit,
    onFilePickFailed: (String) -> Unit = {},
): () -> Unit
