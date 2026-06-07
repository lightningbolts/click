@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package compose.project.click.click.util

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite

private fun profileVaultDirectory(): String? {
    val dirs = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true,
    )
    val docs = dirs.firstOrNull() as? String ?: return null
    val target = "$docs/Click/profile_media_vault"
    val fm = NSFileManager.defaultManager
    if (!fm.fileExistsAtPath(target)) {
        fm.createDirectoryAtPath(
            path = target,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }
    return target
}

private fun profileVaultPath(vaultId: String, extension: String): String? {
    if (vaultId.isBlank()) return null
    val dir = profileVaultDirectory() ?: return null
    val safeExt = extension.trim().trimStart('.').ifBlank { "bin" }
    val safeId = vaultId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    return "$dir/$safeId.$safeExt"
}

actual fun readProfileMediaVaultBytes(vaultId: String, extension: String): ByteArray? {
    val path = profileVaultPath(vaultId, extension) ?: return null
    if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return null
    val file = fopen(path, "rb") ?: return null
    return try {
        fseek(file, 0, SEEK_END)
        val size = ftell(file).toInt()
        if (size <= 0) return null
        fseek(file, 0, SEEK_SET)
        ByteArray(size).also { buffer ->
            buffer.usePinned { pinned ->
                val read = fread(pinned.addressOf(0), 1u, size.toULong(), file)
                if (read.toInt() != size) return null
            }
        }
    } finally {
        fclose(file)
    }
}

actual fun writeProfileMediaVaultBytes(vaultId: String, bytes: ByteArray, extension: String): Boolean {
    if (bytes.isEmpty()) return false
    val path = profileVaultPath(vaultId, extension) ?: return false
    return runCatching {
        val file = fopen(path, "wb") ?: return false
        try {
            val written = bytes.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), file)
            }
            written == bytes.size.toULong()
        } finally {
            fclose(file)
        }
    }.getOrDefault(false)
}

actual fun profileMediaVaultLocalPath(vaultId: String, extension: String): String? {
    val path = profileVaultPath(vaultId, extension) ?: return null
    return path.takeIf { NSFileManager.defaultManager.fileExistsAtPath(it) }
}
