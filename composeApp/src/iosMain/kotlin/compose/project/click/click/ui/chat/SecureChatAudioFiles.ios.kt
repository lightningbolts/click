package compose.project.click.click.ui.chat

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

@OptIn(ExperimentalForeignApi::class)
actual fun writeSecureChatAudioTempFile(messageId: String, bytes: ByteArray, extension: String): String? {
    if (bytes.isEmpty()) return null
    return runCatching {
        val safeExt = extension.trimStart('.').ifBlank { "m4a" }
        val name = "click_secure_${messageId}.$safeExt"
        val dir = NSTemporaryDirectory().trimEnd('/')
        val path = "$dir/$name"
        val f = fopen(path, "wb") ?: error("fopen failed")
        try {
            val written = bytes.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), f)
            }
            if (written != bytes.size.toULong()) error("short write")
        } finally {
            fclose(f)
        }
        path
    }.getOrNull()
}

@OptIn(ExperimentalForeignApi::class)
actual fun deleteSecureChatAudioTempFile(path: String?) {
    val p = path?.trim().orEmpty()
    if (p.isEmpty()) return
    runCatching {
        NSFileManager.defaultManager.removeItemAtPath(p, null)
    }
}
