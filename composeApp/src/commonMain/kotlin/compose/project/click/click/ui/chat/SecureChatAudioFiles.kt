package compose.project.click.click.ui.chat

/** Writes decrypted audio bytes to a cache file; returns an absolute path suitable for local playback. */
expect fun writeSecureChatAudioTempFile(messageId: String, bytes: ByteArray, extension: String): String?

expect fun deleteSecureChatAudioTempFile(path: String?)
