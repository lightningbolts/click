package compose.project.click.click.util

/** Writes decrypted chat media to the native cache and returns a file:// URI for UI playback. */
expect fun writeChatMediaVaultFile(messageId: String, bytes: ByteArray, extension: String): String?
