package compose.project.click.click.ui.camera

/**
 * Bakes the selected Disposable Roll mood filter into JPEG bytes before upload.
 * Index 0 ([DisposableRollFilters] "Natural") returns the original bytes unchanged.
 */
expect suspend fun applyDisposableRollFilterToJpeg(bytes: ByteArray, filterIndex: Int): ByteArray
