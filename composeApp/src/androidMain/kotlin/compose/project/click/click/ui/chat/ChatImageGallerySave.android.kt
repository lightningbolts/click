package compose.project.click.click.ui.chat

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal object AndroidChatImageSaveContext {
    lateinit var applicationContext: Context
}

actual suspend fun saveChatImageToGallery(imageUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val ctx = AndroidChatImageSaveContext.applicationContext
        val connection = URL(imageUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 25_000
        connection.readTimeout = 60_000
        connection.connect()
        val bytes = connection.inputStream.use { it.readBytes() }
        connection.disconnect()
        if (bytes.isEmpty()) error("Empty image response")

        val mime = connection.contentType?.substringBefore(";")?.trim()?.takeIf { it.startsWith("image/") }
            ?: "image/jpeg"
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            else -> "jpg"
        }
        val name = "Click_${System.currentTimeMillis()}.$ext"
        val pictures = ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: File(ctx.filesDir, "Pictures").also { it.mkdirs() }
        val dir = File(pictures, "Click").apply { mkdirs() }
        val out = File(dir, name)
        out.writeBytes(bytes)
        MediaScannerConnection.scanFile(
            ctx,
            arrayOf(out.absolutePath),
            arrayOf(mime),
            null,
        )
    }
}
