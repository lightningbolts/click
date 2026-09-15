@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Media uses the same global native-chrome host as the underlying route, but publishes its own
 * exclusive semantic state for the lifetime of the lightbox. This is deliberately route-shaped
 * rather than a second mutable "media override" layered onto an existing route state: the latter
 * allowed close/presence recompositions to briefly republish stale xmark/share semantics after the
 * chat was already visible.
 *
 * Publishing an exclusive state also matters for media opened from a profile sheet. Those previews
 * are portaled into their own full-screen Compose host above the native sheet; binding the media
 * chrome in that host keeps Close/Save/Share visible instead of retargeting controls that remain
 * underneath the sheet.
 */
@Composable
internal actual fun ApplyStableOverlayMediaChrome(
    active: Boolean,
    onClose: () -> Unit,
    trailing: List<NativeChromeAction>,
) {
    val latestOnClose = rememberUpdatedState(onClose)
    val dismissingState = remember { mutableStateOf(false) }
    val stableClose =
        remember {
            {
                if (!dismissingState.value) {
                    // Tombstone this media composition before navigation state changes. Even if the
                    // caller recomposes during the exit transition, this owner cannot be rebound.
                    dismissingState.value = true
                    latestOnClose.value.invoke()
                }
            }
        }

    if (!active || dismissingState.value) return

    BindPlatformNativeNavigationBar(
        title = "",
        subtitle = null,
        presenceOnline = null,
        identity = null,
        onNavigateBack = stableClose,
        onOpenSearch = null,
        nativeTrailingActions = trailing,
        collapseFraction = 1f,
        leadingClose = true,
    )
}
