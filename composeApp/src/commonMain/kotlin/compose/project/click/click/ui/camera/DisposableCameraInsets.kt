package compose.project.click.click.ui.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.components.rememberTabBarOverlayHeight

/**
 * Extra bottom inset for Disposable Roll when the main tab bar may still overlap controls.
 * Chat opens the camera with the tab bar already hidden, so no extra lift is needed there.
 */
fun disposableRollExtraBottomPadding(isOpenedFromChat: Boolean, tabBarOverlayHeight: Dp): Dp =
    if (isOpenedFromChat) 0.dp else tabBarOverlayHeight

@Composable
fun rememberDisposableRollExtraBottomPadding(isOpenedFromChat: Boolean): Dp =
    disposableRollExtraBottomPadding(isOpenedFromChat, rememberTabBarOverlayHeight())
