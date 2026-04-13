package compose.project.click.click.ui.chat

import java.io.File

actual fun writeSecureChatAudioTempFile(messageId: String, bytes: ByteArray, extension: String): String? {
    if (bytes.isEmpty()) return null
    return runCatching {
        val ctx = AndroidChatImageSaveContext.applicationContext
        val dir = File(ctx.cacheDir, "click_secure_audio").apply { mkdirs() }
        val safeExt = extension.trim('.').ifBlank { "m4a" }
        val out = File(dir, "${messageId}.${safeExt}")
        out.writeBytes(bytes)
        out.absolutePath
    }.getOrNull()
}

actual fun deleteSecureChatAudioTempFile(path: String?) {
    val p = path?.trim().orEmpty()
    if (p.isEmpty()) return
    runCatching { File(p).delete() }
}
