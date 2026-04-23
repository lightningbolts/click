package compose.project.click.click

import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import compose.project.click.click.ui.chat.AndroidChatImageSaveContext
import compose.project.click.click.util.isBeaconOriginalSongDeepLinkUrl

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun openBeaconOriginalMediaUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isEmpty() || !isBeaconOriginalSongDeepLinkUrl(trimmed)) return false
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
