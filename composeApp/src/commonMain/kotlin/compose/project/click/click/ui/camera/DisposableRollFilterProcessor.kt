package compose.project.click.click.ui.camera

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Renders a filtered preview bitmap for post-capture editing using platform photo libraries.
 */
expect suspend fun renderDisposableRollFilteredPreview(
    sourceBytes: ByteArray,
    filterIndex: Int,
): ImageBitmap?

/**
 * Bakes the selected Disposable Roll mood filter into JPEG bytes before upload.
 * Index 0 ("Natural") returns the original bytes unchanged.
 */
expect suspend fun applyDisposableRollFilterToJpeg(bytes: ByteArray, filterIndex: Int): ByteArray
