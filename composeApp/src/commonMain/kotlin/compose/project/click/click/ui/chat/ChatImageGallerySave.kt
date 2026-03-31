package compose.project.click.click.ui.chat

/**
 * Download image bytes from [imageUrl] and save to the device photo gallery / Pictures.
 */
expect suspend fun saveChatImageToGallery(imageUrl: String): Result<Unit>
