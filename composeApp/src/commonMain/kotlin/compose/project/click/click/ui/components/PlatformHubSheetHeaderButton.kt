package compose.project.click.click.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Sheet-local hub header actions. iOS uses real UIKit Liquid Glass buttons. */
enum class HubSheetHeaderAction {
    Back,
    More,
}

@Composable
expect fun PlatformHubSheetHeaderButton(
    action: HubSheetHeaderAction,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
