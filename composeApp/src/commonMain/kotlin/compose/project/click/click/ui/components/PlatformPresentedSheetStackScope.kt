package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable

/**
 * Runs [content] with the currently presented platform sheet as its UIKit parent when available.
 *
 * This is used for root-owned content that must visually stack on an already-presented native
 * sheet without dismissing or replacing the underlying sheet hierarchy.
 */
@Composable
expect fun PlatformPresentedSheetStackScope(content: @Composable () -> Unit)
