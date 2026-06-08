package compose.project.click.click.util

import compose.project.click.click.ui.chat.AndroidChatImageSaveContext
import java.io.File

private const val VAULT_FOLDER = "click_media_vault"

actual fun chatMediaVaultDirectory(): String {
    val ctx = AndroidChatImageSaveContext.applicationContext
    return File(ctx.filesDir, VAULT_FOLDER).apply { mkdirs() }.absolutePath
}

actual fun writeChatMediaVaultFile(messageId: String, bytes: ByteArray, extension: String): String? {
    if (bytes.isEmpty() || messageId.isBlank()) return null
    return runCatching {
        val safeExt = extension.trim().trimStart('.').ifBlank { "bin" }
        val safeId = messageId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val out = File(chatMediaVaultDirectory(), "$safeId.$safeExt")
        out.writeBytes(bytes)
        "file://${out.absolutePath}"
    }.getOrNull()
}

actual fun readChatMediaVaultBytes(messageId: String, preferredExtension: String?): ByteArray? {
    val path = chatMediaVaultLocalPath(messageId, preferredExtension) ?: return null
    val file = File(path)
    if (!file.isFile || file.length() <= 0L) return null
    return runCatching { file.readBytes() }.getOrNull()
}

actual fun chatMediaVaultLocalPath(messageId: String, preferredExtension: String?): String? {
    if (messageId.isBlank()) return null
    val safeId = messageId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val dir = File(chatMediaVaultDirectory())
    for (ext in vaultFileExtensionCandidates(preferredExtension)) {
        val candidate = File(dir, "$safeId.$ext")
        if (candidate.isFile && candidate.length() > 0L) {
            return candidate.absolutePath
        }
        val legacy = File(dir, "click_media_vault_$safeId.$ext")
        if (legacy.isFile && legacy.length() > 0L) {
            return legacy.absolutePath
        }
    }
    return null
}

actual fun readFileUriBytes(fileUri: String): ByteArray? {
    val path = fileUri.removePrefix("file://")
    if (path.isBlank()) return null
    val file = File(path)
    if (!file.isFile || file.length() <= 0L) return null
    return runCatching { file.readBytes() }.getOrNull()
}
