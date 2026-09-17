package compose.project.click.click // pragma: allowlist secret

import compose.project.click.click.ui.components.InteractiveBackHostState // pragma: allowlist secret

/**
 * Shared draw-only state for the Connections/search Hub route.
 *
 * [AppHubChatHost] owns the foreground drag. [AppPrimaryTabsHost] mirrors the same offset onto its
 * already-mounted tab tree so Hub -> Connections uses the identical persistent-underlay parallax as
 * normal chat, rather than sliding the Hub over a stationary destination.
 */
internal object HubChatInteractiveBackBridge {
    val state = InteractiveBackHostState()
}
