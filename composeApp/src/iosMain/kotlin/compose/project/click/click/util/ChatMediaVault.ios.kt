package compose.project.click.click.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSTemporaryDirectory
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

@OptIn(ExperimentalForeignApi::class)
actual fun writeChatMediaVaultFile(messageId: String, bytes: ByteArray, extension: String): String? {
    if (bytes.isEmpty() || messageId.isBlank()) return null
    return runCatching {
        val safeExt = extension.trim().trimStart('.').ifBlank { "bin" }
        val safeId = messageId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val dir = NSTemporaryDirectory().trimEnd('/')
        val path = "$dir/click_media_vault_$safeId.$safeExt"
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
