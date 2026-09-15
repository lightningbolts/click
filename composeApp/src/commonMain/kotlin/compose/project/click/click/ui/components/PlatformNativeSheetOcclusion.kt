@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable

/**
 * Temporarily removes already-presented native page-sheet containers from hit testing/rendering
 * while a Compose full-screen destination is above them. The native sheets stay presented and keep
 * their exact navigation/detail state, then become visible again when the overlay closes.
 *
 * This is intentionally not a dismiss/re-present cycle: rebuilding UISheetPresentationController
 * stacks loses detent/selection state and is the reason event hub entry previously required users
 * to manually swipe the Nearby and Event sheets away.
 */
@Composable
expect fun PlatformNativeSheetOcclusion(active: Boolean)
