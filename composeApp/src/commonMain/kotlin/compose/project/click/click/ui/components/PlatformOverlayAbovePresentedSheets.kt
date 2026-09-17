@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable

/**
 * Keeps [content] in the current composition normally, but on iOS can portal it above an already
 * presented native sheet stack without dismissing or hiding those sheets.
 *
 * [dismissing] lets the iOS host fade the complete UIKit portal — including native Liquid Glass
 * controls attached to that controller — before the presentation is torn down.
 *
 * [revealUnderlyingPresentation] keeps the portal host itself transparent. Use this for interactive
 * full-screen routes whose back gesture must expose the still-mounted native sheet stack below.
 */
@Composable
expect fun PlatformOverlayAbovePresentedSheets(
    liftAbovePresentedSheets: Boolean,
    dismissing: Boolean = false,
    revealUnderlyingPresentation: Boolean = false,
    content: @Composable () -> Unit,
)
