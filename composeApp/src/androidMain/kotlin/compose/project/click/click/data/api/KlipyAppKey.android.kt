package compose.project.click.click.data.api // pragma: allowlist secret

import android.content.pm.PackageManager
import compose.project.click.click.ui.chat.AndroidChatImageSaveContext // pragma: allowlist secret

/** Manifest meta-data filled from `KLIPY_APP_KEY` by the secrets Gradle plugin. */
private const val KLIPY_META_DATA = "compose.project.click.click.KLIPY_APP_KEY"

actual fun klipyAppKey(): String? =
    runCatching {
        val ctx = AndroidChatImageSaveContext.applicationContext
        val info = ctx.packageManager.getApplicationInfo(ctx.packageName, PackageManager.GET_META_DATA)
        // `get` (not `getString`): an all-digit value would be parsed as a number.
        info.metaData
            ?.get(KLIPY_META_DATA)
            ?.toString()
            ?.trim()
    }.getOrNull()?.takeIf { it.isNotEmpty() }
