package compose.project.click.click.calls

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIColor
import platform.UIKit.UIView

private const val CALL_REGISTER_VIDEO_VIEW_NOTIFICATION = "ClickCallRegisterVideoView"
private const val CALL_UNREGISTER_VIDEO_VIEW_NOTIFICATION = "ClickCallUnregisterVideoView"

@Composable
actual fun CallVideoSurface(
    callManager: CallManager,
    isLocal: Boolean,
    modifier: Modifier,
) {
    UIKitView(
        modifier = modifier,
        factory = {
            UIView().apply {
                backgroundColor = UIColor.clearColor
                clipsToBounds = true
                registerForCallVideo(isLocal = isLocal)
            }
        },
        update = { view ->
            view.registerForCallVideo(isLocal = isLocal)
        },
        onRelease = { view ->
            NSNotificationCenter.defaultCenter.postNotificationName(
                aName = CALL_UNREGISTER_VIDEO_VIEW_NOTIFICATION,
                `object` = view,
                userInfo = mapOf("isLocal" to isLocal)
            )
        }
    )
}

private fun UIView.registerForCallVideo(isLocal: Boolean) {
    NSNotificationCenter.defaultCenter.postNotificationName(
        aName = CALL_REGISTER_VIDEO_VIEW_NOTIFICATION,
        `object` = this,
        userInfo = mapOf("isLocal" to isLocal)
    )
}