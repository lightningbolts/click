@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Semi-translucent header backdrop for collapsed tab-root chrome.
 * Rest state (collapseFraction ≈ 0) should omit this entirely.
 */
@Composable
expect fun HeaderGlassBackdrop(
    modifier: Modifier = Modifier,
    collapseFraction: Float,
)
