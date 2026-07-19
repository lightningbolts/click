package compose.project.click.click.utils

import androidx.compose.ui.graphics.ImageBitmap

expect fun ByteArray.toImageBitmap(): ImageBitmap

/**
 * One-shot soft blur for locked Click Drops.
 *
 * Must NOT use [androidx.compose.ui.draw.blur] while the bubble translates (reply swipe) —
 * live RenderEffect blur re-rasters every frame and flickers. This returns a static bitmap.
 */
expect fun ImageBitmap.softBlurredForLockedDrop(): ImageBitmap
