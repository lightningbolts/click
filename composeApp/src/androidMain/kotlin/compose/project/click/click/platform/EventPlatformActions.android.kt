package compose.project.click.click.platform // pragma: allowlist secret

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.CalendarContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import compose.project.click.click.events.EventCalendarEntry // pragma: allowlist secret
import compose.project.click.click.ui.chat.AndroidChatImageSaveContext // pragma: allowlist secret
import java.io.File

actual object EventPlatformActions {
    private val context: Context get() = AndroidChatImageSaveContext.applicationContext

    private fun start(intent: Intent): Boolean =
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }

    actual fun insertCalendarEvent(entry: EventCalendarEntry): Boolean =
        start(
            Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
                putExtra(CalendarContract.Events.TITLE, entry.title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, entry.startEpochMs)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, entry.endEpochMs)
                entry.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
                putExtra(CalendarContract.Events.DESCRIPTION, entry.details)
            },
        )

    actual fun openUri(uri: String): Boolean = start(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))

    actual fun openGeoChooser(uri: String): Boolean =
        start(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(uri)), "Open with"))

    actual fun copyToClipboard(
        label: String,
        text: String,
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    actual fun savePngToGallery(
        bytes: ByteArray,
        displayName: String,
    ): Boolean =
        runCatching {
            val resolver = context.contentResolver
            val values =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Click")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
            val uri =
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return@runCatching false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        }.getOrDefault(false)

    actual fun sharePng(
        bytes: ByteArray,
        displayName: String,
        text: String,
    ) {
        runCatching {
            val dir = File(context.cacheDir, "shared_flyers").apply { mkdirs() }
            val file = File(dir, "$displayName.png")
            file.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            start(Intent.createChooser(send, "Share"))
        }
    }
}
