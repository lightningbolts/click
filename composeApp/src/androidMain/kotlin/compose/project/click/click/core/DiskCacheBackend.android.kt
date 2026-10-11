package compose.project.click.click.core

import compose.project.click.click.data.storage.androidStorageContextOrThrow
import java.io.File

private const val CACHE_ROOT = "user_cache"

/** `noBackupFilesDir/user_cache/<uid>/<key>.json`; one root, so the sign-out wipe is one delete. */
private class AndroidDiskCacheBackend(
    private val root: File,
) : DiskCacheBackend {
    private fun file(
        userId: String,
        key: String,
    ) = File(File(root, userId), "$key.json")

    override fun read(
        userId: String,
        key: String,
    ): String? = file(userId, key).takeIf { it.isFile }?.readText()

    override fun write(
        userId: String,
        key: String,
        text: String,
    ) {
        val target = file(userId, key)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    override fun delete(
        userId: String,
        key: String,
    ) {
        file(userId, key).delete()
    }

    override fun wipeAll() {
        root.deleteRecursively()
    }
}

private val memoryFallback = InMemoryDiskCacheBackend()

actual fun platformDiskCacheBackend(): DiskCacheBackend {
    val context = runCatching { androidStorageContextOrThrow() }.getOrNull() ?: return memoryFallback
    return AndroidDiskCacheBackend(File(context.noBackupFilesDir, CACHE_ROOT))
}
