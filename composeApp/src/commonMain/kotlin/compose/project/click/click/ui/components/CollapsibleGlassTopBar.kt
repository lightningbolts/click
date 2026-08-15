package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable

/**
 * Shared tab-root header: borderless large title at rest, native glass once scroll exceeds 20 dp.
 *
 * Used by Home, Add Click, Chats, Map/Discovery, and Settings via [AppScreenScaffold] /
 * [AppScreenWithFloatingHeader] / [ConnectionsFloatingHeader].
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
