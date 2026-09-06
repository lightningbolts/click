package compose.project.click.click.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties

internal actual fun fullscreenMediaDialogProperties(): DialogProperties =
    DialogProperties(
        dismissOnBackPress = true,
        dismissOnClickOutside = false,
        usePlatformDefaultWidth = false,
        usePlatformInsets = false,
        scrimColor = Color.Black,
    )
