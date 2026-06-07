package compose.project.click.click.ui.chat

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * Phase 2 — C6: write a decrypted attachment into a user-visible `Downloads/Click/` folder.
 *
 * * API 29+ uses `MediaStore.Downloads` so the file shows up in the system Files app without
 *   needing `WRITE_EXTERNAL_STORAGE`.
 * * Older APIs fall back to `getExternalFilesDir(DIRECTORY_DOWNLOADS)` (app-scoped but still
 *   visible via the system file picker when sharing is not available).
 *
 * The caller already decrypted + verified the SHA-256 — this function only performs IO and
 * must not leak the filename in unredacted logs.
 */
actual fun saveDecryptedAttachmentToDownloads(
    bytes: ByteArray,
    fileName: String,
    mimeType: String,
): String? {
    if (bytes.isEmpty() || fileName.isBlank()) return null
    val ctx = AndroidChatImageSaveContext.applicationContext
    val safeMime = mimeType.ifBlank { "application/octet-stream" }
    val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = ctx.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, safeMime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Click")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching null
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: run {
                    resolver.delete(uri, null, null)
                    return@runCatching null
                }
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            openSavedAttachment(uri, safeMime)
            uri.toString()
        } else {
            @Suppress("DEPRECATION")
            val baseDir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: return@runCatching null
            val targetDir = File(baseDir, "Click").apply { mkdirs() }
            val file = File(targetDir, safeName)
            file.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            openSavedAttachment(uri, safeMime)
            file.absolutePath
        }
    }.getOrNull()
}

private fun openSavedAttachment(uri: Uri, mimeType: String) {
    val ctx = AndroidChatImageSaveContext.applicationContext
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType.ifBlank { "application/octet-stream" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { ctx.startActivity(intent) }
}
