@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable

/**
 * Temporarily removes platform-presented sheet controllers while preserving their controller
 * instances and Compose-owned state so a root-hosted full-screen route can reuse the same native
 * navigation and tab chrome as ordinary app routes.
 *
 * Returns true once the presented sheet stack is fully out of the way and the root route may be
 * shown. When [active] becomes false, the exact controller stack is restored.
 */
@Composable
expect fun PlatformPresentedSheetSuspension(active: Boolean): Boolean
