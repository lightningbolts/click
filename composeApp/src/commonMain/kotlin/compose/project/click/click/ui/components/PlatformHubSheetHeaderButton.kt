@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** Sheet-local hub header actions. iOS uses real UIKit Liquid Glass buttons. */
enum class HubSheetHeaderAction {
    Back,
    More,
}

/** Limits native sheet controls to the embedded Event Hub presentation. */
val LocalUseNativeHubSheetHeaderControls = staticCompositionLocalOf { false }

@Composable
expect fun PlatformHubSheetHeaderButton(
    action: HubSheetHeaderAction,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
