@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable

@Composable
@Suppress("UNUSED_PARAMETER")
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
