package compose.project.click.click.util

/** Native directory for decrypted chat media vault files. */
expect fun chatMediaVaultDirectory(): String

/** Writes decrypted chat media to the native cache and returns a file:// URI for UI playback. */
expect fun writeChatMediaVaultFile(messageId: String, bytes: ByteArray, extension: String): String?

/** Reads decrypted bytes from the on-device vault when present. */
expect fun readChatMediaVaultBytes(messageId: String): ByteArray?

/** Reads bytes from a file:// URI produced by the vault or message metadata. */
expect fun readFileUriBytes(fileUri: String): ByteArray?

private val VAULT_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp", "heic", "gif")

fun readChatMediaVaultBytesForMessage(messageId: String, mediaUrl: String? = null): ByteArray? {
    if (messageId.isNotBlank()) {
        readChatMediaVaultBytes(messageId)?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    val uri = mediaUrl?.trim().orEmpty()
    if (uri.startsWith("file://", ignoreCase = true)) {
        readFileUriBytes(uri)?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return null
}

internal fun vaultFileExtensionCandidates(preferredExtension: String?): List<String> {
    val preferred = preferredExtension
        ?.trim()
        ?.trimStart('.')
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() }
    return buildList {
        if (preferred != null) add(preferred)
        VAULT_EXTENSIONS.forEach { ext ->
            if (ext !in this) add(ext)
        }
    }
}
