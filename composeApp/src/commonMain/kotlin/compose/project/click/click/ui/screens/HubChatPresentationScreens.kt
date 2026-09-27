@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import compose.project.click.click.ui.components.InteractiveSwipeBackRightToLeftPeek // pragma: allowlist secret
import compose.project.click.click.utils.LocationResult // pragma: allowlist secret

/**
 * Connections/search owns a pushed Hub route. It keeps interactive Back, timestamp-peek gesture
 * integration, and the standard native chevron chrome. This contract must stay independent from
 * Event/Nearby modal presentation behavior.
 */
@Composable
internal fun ConnectionsHubChatScreen(
    args: HubChatNavArgs,
    currentUserId: String,
    targetMessageId: String? = null,
    onNavigateBack: () -> Unit,
    resolveHubGatekeeperLocation: suspend () -> LocationResult? = { null },
    onRegisterSwipeBackRightToLeftPeek: (InteractiveSwipeBackRightToLeftPeek?) -> Unit = {},
    parentInteractiveBackSwipePx: MutableFloatState? = null,
) {
    HubChatScreen(
        args = args,
        currentUserId = currentUserId,
        targetMessageId = targetMessageId,
        onNavigateBack = onNavigateBack,
        resolveHubGatekeeperLocation = resolveHubGatekeeperLocation,
        integrateTimestampPeekWithSwipeBackContainer = true,
        onRegisterSwipeBackRightToLeftPeek = onRegisterSwipeBackRightToLeftPeek,
        parentInteractiveBackSwipePx = parentInteractiveBackSwipePx,
        embeddedInSheet = false,
    )
}

/** Event Hub chats use a sheet-local presentation and Compose header. */
@Composable
internal fun EventHubBottomSheetChatScreen(
    args: HubChatNavArgs,
    currentUserId: String,
    onClose: () -> Unit,
) {
    HubChatScreen(
        args = args,
        currentUserId = currentUserId,
        onNavigateBack = onClose,
        embeddedInSheet = true,
    )
}
