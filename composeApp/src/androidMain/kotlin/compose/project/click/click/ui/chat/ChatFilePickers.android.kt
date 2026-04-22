package compose.project.click.click.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import compose.project.click.click.chat.attachments.ChatAttachmentValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android implementation of [rememberFilePicker] using the Storage Access Framework
 * (`ActivityResultContracts.OpenDocument`). We ask for the allow-listed MIME types directly so the
 * system picker filters aggressively — the user still sees a "All files" toggle but defaults to
 * our safe set.
 */
@Composable
actual fun rememberFilePicker(
    onFilePicked: (PickedFile) -> Unit,
    onFilePickFailed: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = withContext(Dispatchers.IO) { readPickedFile(context, uri) }
            if (picked == null) {
                onFilePickFailed(
                    "Couldn't read that file. If access was denied, reselect the document from the Files app.",
                )
                return@launch
            }
            onFilePicked(picked)
        }
    }

    return {
        val accept = ChatAttachmentValidator.ALLOWED_MIME_TYPES.toTypedArray()
        // Some Android OEM pickers ignore an empty array; fall back to "all" so the picker opens.
        launcher.launch(if (accept.isEmpty()) arrayOf("*/*") else accept)
    }
}

private fun readPickedFile(context: Context, uri: Uri): PickedFile? = runCatching {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else {
                null
            }
        }
        ?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
        ?: "attachment"
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
    PickedFile(bytes = bytes, fileName = name, mimeType = mime)
}.getOrNull()
