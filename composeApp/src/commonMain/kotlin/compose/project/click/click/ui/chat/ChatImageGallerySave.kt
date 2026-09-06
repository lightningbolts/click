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
expect fun shareDecryptedImage(
    imageBytes: ByteArray,
    fileName: String,
)

/** Download image bytes from a URL (e.g. signed Supabase URL for non-E2EE profile media). */
expect suspend fun fetchImageBytesFromUrl(imageUrl: String): ByteArray?

internal fun lightboxImageFileExtension(mimeTypeHint: String?): String =
    when {
        mimeTypeHint?.contains("png", ignoreCase = true) == true -> "png"
        mimeTypeHint?.contains("webp", ignoreCase = true) == true -> "webp"
        else -> "jpg"
    }

suspend fun persistLightboxImageToGallery(
    imageUrl: String,
    decryptedBytes: ByteArray?,
    mimeTypeHint: String?,
) {
    if (decryptedBytes != null && decryptedBytes.isNotEmpty()) {
        saveChatImageToGallery(
            imageUrl = imageUrl.ifBlank { "photo.${lightboxImageFileExtension(mimeTypeHint)}" },
            decryptedImageBytes = decryptedBytes,
            mimeTypeHint = mimeTypeHint,
        )
    } else if (imageUrl.isNotBlank()) {
        saveChatImageToGallery(imageUrl)
    }
}

suspend fun shareLightboxImage(
    imageUrl: String,
    decryptedBytes: ByteArray?,
    mimeTypeHint: String?,
) {
    val ext = lightboxImageFileExtension(mimeTypeHint)
    val bytes = decryptedBytes?.takeIf { it.isNotEmpty() } ?: fetchImageBytesFromUrl(imageUrl)
    if (bytes != null && bytes.isNotEmpty()) {
        shareDecryptedImage(bytes, "click_share.$ext")
    }
}
