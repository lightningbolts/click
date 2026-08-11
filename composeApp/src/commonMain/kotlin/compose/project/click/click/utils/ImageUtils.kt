package compose.project.click.click.utils

import androidx.compose.ui.graphics.ImageBitmap

expect fun ByteArray.toImageBitmap(): ImageBitmap

/**
 * Decode for chat bubble display — downsamples so a full-res camera JPEG does not
 * allocate a multi‑megapixel bitmap on the UI path (scroll / interactive-back hitch).
 */
expect fun ByteArray.toChatDisplayImageBitmap(maxEdgePx: Int = 720): ImageBitmap

/**
 * One-shot soft blur for locked Click Drops.
 *
 * Must NOT use [androidx.compose.ui.draw.blur] while the bubble translates (reply swipe) —
 * live RenderEffect blur re-rasters every frame and flickers. This returns a static bitmap.
 */
expect fun ImageBitmap.softBlurredForLockedDrop(): ImageBitmap
