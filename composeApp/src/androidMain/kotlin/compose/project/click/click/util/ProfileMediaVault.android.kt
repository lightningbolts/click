package compose.project.click.click.util

import compose.project.click.click.ui.chat.AndroidChatImageSaveContext
import java.io.File

private fun profileVaultFile(vaultId: String, extension: String): File {
    val ctx = AndroidChatImageSaveContext.applicationContext
    val dir = File(ctx.filesDir, "click_profile_media_vault").apply { mkdirs() }
    val safeExt = extension.trim().trimStart('.').ifBlank { "bin" }
    val safeId = vaultId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    return File(dir, "$safeId.$safeExt")
}

actual fun readProfileMediaVaultBytes(vaultId: String, extension: String): ByteArray? {
    if (vaultId.isBlank()) return null
    val file = profileVaultFile(vaultId, extension)
    if (!file.isFile || file.length() <= 0L) return null
    return runCatching { file.readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() }
}

actual fun writeProfileMediaVaultBytes(vaultId: String, bytes: ByteArray, extension: String): Boolean {
    if (vaultId.isBlank() || bytes.isEmpty()) return false
    return runCatching {
        profileVaultFile(vaultId, extension).writeBytes(bytes)
        true
    }.getOrDefault(false)
}

actual fun profileMediaVaultLocalPath(vaultId: String, extension: String): String? {
    if (vaultId.isBlank()) return null
    val file = profileVaultFile(vaultId, extension)
    return file.takeIf { it.isFile && it.length() > 0L }?.absolutePath
}
