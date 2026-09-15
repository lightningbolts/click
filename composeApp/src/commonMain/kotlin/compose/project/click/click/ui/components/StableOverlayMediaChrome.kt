@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable

/**
 * Media-lightbox chrome that preserves the native navigation controls already mounted by the
 * underlying route. On iOS, an existing overlay binder is mutated in place so back -> close and
 * trailing actions reuse the same UIKit button instances and glass container. Android delegates to
 * the existing platform media-chrome implementation.
 *
 * The [active] transition is intentionally separate from composition lifetime. Lightboxes remain
 * composed while their exit animation runs, so setting [active] false at dismissal start lets the
 * native controls morph back to the route chrome during the fade instead of snapping after the
 * overlay disappears.
 */
@Composable
internal expect fun ApplyStableOverlayMediaChrome(
    active: Boolean,
    onClose: () -> Unit,
    trailing: List<NativeChromeAction>,
)
