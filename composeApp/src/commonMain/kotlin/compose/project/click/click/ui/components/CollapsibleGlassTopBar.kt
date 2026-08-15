@file:Suppress(
    "ktlint:standard:function-naming",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable

/**
 * Header chrome is platform-native ([NativeCollapsingScaffold]). This Compose title is only a
 * fallback for surfaces that have not migrated yet — do not fold tab-root chrome back into it.
 */
@Composable
fun CollapsibleGlassTopBar(
    title: String,
    subtitle: String? = null,
    presenceOnline: Boolean? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
    collapseFraction: Float = 0f,
) {
    LiquidGlassPageHeader(
        title = title,
        subtitle = subtitle,
        presenceOnline = presenceOnline,
        navigationIcon = navigationIcon,
        actions = actions,
        collapseFraction = collapseFraction,
    )
}
