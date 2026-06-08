package compose.project.click.click.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.refTo
import kotlinx.cinterop.usePinned
import platform.posix.memcpy
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

private const val VAULT_FOLDER = "click_media_vault"

@OptIn(ExperimentalForeignApi::class)
actual fun chatMediaVaultDirectory(): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
    val base = (paths.firstOrNull() as? String)?.trimEnd('/') ?: NSTemporaryDirectory().trimEnd('/')
    val dir = "$base/$VAULT_FOLDER"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
    return dir
}

@OptIn(ExperimentalForeignApi::class)
actual fun writeChatMediaVaultFile(messageId: String, bytes: ByteArray, extension: String): String? {
    if (bytes.isEmpty() || messageId.isBlank()) return null
    return runCatching {
        val safeExt = extension.trim().trimStart('.').ifBlank { "bin" }
        val safeId = messageId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val path = "${chatMediaVaultDirectory()}/click_media_vault_$safeId.$safeExt"
        val f = fopen(path, "wb") ?: error("fopen failed")
        try {
            val written = bytes.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), f)
            }
            if (written != bytes.size.toULong()) error("short write")
        } finally {
            fclose(f)
        }
        "file://$path"
    }.getOrNull()
}

@OptIn(ExperimentalForeignApi::class)
actual fun readChatMediaVaultBytes(messageId: String): ByteArray? {
    if (messageId.isBlank()) return null
    val safeId = messageId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val dirs = listOf(
        chatMediaVaultDirectory(),
        NSTemporaryDirectory().trimEnd('/'),
    ).distinct()
    for (dir in dirs) {
        for (ext in vaultFileExtensionCandidates(preferredExtension = null)) {
            val path = "$dir/click_media_vault_$safeId.$ext"
            val data = NSData.dataWithContentsOfFile(path) as NSData? ?: continue
            return data.toByteArray()
        }
    }
    return null
}

@OptIn(ExperimentalForeignApi::class)
actual fun readFileUriBytes(fileUri: String): ByteArray? {
    val path = fileUri.removePrefix("file://")
    if (path.isBlank()) return null
    val data = NSData.dataWithContentsOfFile(path) as NSData? ?: return null
    return data.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len <= 0) return ByteArray(0)
    val out = ByteArray(len)
    val base = bytes ?: return ByteArray(0)
    memcpy(out.refTo(0), base, len.toULong())
    return out
}
