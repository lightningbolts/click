package compose.project.click.click

import android.content.Intent
import android.os.Build
import android.os.Bundle
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

actual fun openInAppBrowser(url: String): Boolean {
    val trimmed = url.trim()
    if (!trimmed.startsWith("https://")) return false
    val ctx = AndroidChatImageSaveContext.applicationContext
    return try {
        // The session extra (even null) asks the browser for a Custom Tab without the androidx.browser library.
        val intent =
            Intent(Intent.ACTION_VIEW, trimmed.toUri())
                .putExtras(Bundle().apply { putBinder("android.support.customtabs.extra.SESSION", null) })
                .putExtra("android.support.customtabs.extra.SHARE_STATE", 1)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}

actual fun appVersionLabel(): String =
    runCatching {
        val ctx = AndroidChatImageSaveContext.applicationContext
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        "${info.versionName.orEmpty()} ($code)".trim()
    }.getOrDefault("")
