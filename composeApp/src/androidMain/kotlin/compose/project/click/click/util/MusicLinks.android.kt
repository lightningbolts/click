package compose.project.click.click.util // pragma: allowlist secret

import android.content.Intent
import androidx.core.net.toUri
import compose.project.click.click.ui.chat.AndroidChatImageSaveContext // pragma: allowlist secret

actual fun openMusicStreamingUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return false
    if (!isValidStreamingUrl(trimmed)) return false
    val ctx = AndroidChatImageSaveContext.applicationContext
    return try {
        val uri = trimmed.toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}
