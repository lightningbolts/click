package compose.project.click.click.ui.chat

/**
 * Save a chat image to the device gallery. When [decryptedImageBytes] is set, [imageUrl] is only
 * used for logging / MIME fallback — bytes are written directly (E2EE path).
 */
expect suspend fun saveChatImageToGallery(
    imageUrl: String,
    decryptedImageBytes: ByteArray? = null,
    mimeTypeHint: String? = null,
): Result<Unit>

/** Share decrypted image bytes via the system sheet (cache-scoped temp file on Android). */
expect fun shareDecryptedImage(imageBytes: ByteArray, fileName: String)
