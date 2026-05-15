package compose.project.click.click.util

import compose.project.click.click.ui.chat.AndroidChatImageSaveContext
import java.io.File

actual fun writeChatMediaVaultFile(messageId: String, bytes: ByteArray, extension: String): String? {
    if (bytes.isEmpty() || messageId.isBlank()) return null
    return runCatching {
        val ctx = AndroidChatImageSaveContext.applicationContext
        val dir = File(ctx.cacheDir, "click_media_vault").apply { mkdirs() }
        val safeExt = extension.trim().trimStart('.').ifBlank { "bin" }
        val safeId = messageId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val out = File(dir, "$safeId.$safeExt")
        out.writeBytes(bytes)
        "file://${out.absolutePath}"
    }.getOrNull()
}
