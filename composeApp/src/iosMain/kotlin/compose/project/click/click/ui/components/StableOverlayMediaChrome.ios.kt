@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable

/**
 * Keep media chrome under the same route-owned overlay coordinator used by the conversation.
 *
 * The previous implementation directly snapshotted and repainted UIKit buttons from a second
 * Compose effect while the underlying route binder was still active. That allowed presence/title
 * recompositions and media dismissal to fight over the same leading/trailing controls, producing
 * xmark/chevron oscillation and repeated share/menu glyph changes on physical devices.
 *
 * [ApplyOverlayMediaChrome] instead publishes one semantic media state into [IosNavChrome]. The
 * already-mounted route binder remains the sole writer to the persistent UIKit controls, so chat
 * -> media -> chat changes are serialized through one owner and retain the same glass/button
 * instances. It also preserves the complete trailing action list, including Save/Download.
 */
@Composable
internal actual fun ApplyStableOverlayMediaChrome(
    active: Boolean,
    onClose: () -> Unit,
    trailing: List<NativeChromeAction>,
) {
    ApplyOverlayMediaChrome(
        active = active,
        onClose = onClose,
        trailing = trailing,
    )
}
