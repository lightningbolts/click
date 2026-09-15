@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable

/**
 * Keeps [content] in the current composition normally, but on iOS can portal it above an already
 * presented native sheet stack without dismissing or hiding those sheets.
 *
 * Event hub chat needs this because Nearby/Event are UISheetPresentationController layers, which
 * otherwise sit above the app's root Compose view. Hiding their container views leaves UIKit's
 * modal presentation state active (dimmed tint and hit-testing interception). A real overlay view
 * mounted above the top presentation container preserves the exact sheet stack and remains fully
 * interactive.
 */
@Composable
expect fun PlatformOverlayAbovePresentedSheets(
    liftAbovePresentedSheets: Boolean,
    content: @Composable () -> Unit,
)
