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
    participantId: String,
    modifier: Modifier,
    mirror: Boolean,
) {
    UIKitView(
        modifier = modifier,
        factory = {
            UIView().apply {
                backgroundColor = UIColor.clearColor
                clipsToBounds = true
                registerForCallVideo(participantId = participantId, mirror = mirror)
            }
        },
        update = { view ->
            view.registerForCallVideo(participantId = participantId, mirror = mirror)
        },
        onRelease = { view ->
            NSNotificationCenter.defaultCenter.postNotificationName(
                aName = CALL_UNREGISTER_VIDEO_VIEW_NOTIFICATION,
                `object` = view,
                userInfo = mapOf("participantId" to participantId),
            )
        },
    )
}

private fun UIView.registerForCallVideo(participantId: String, mirror: Boolean) {
    NSNotificationCenter.defaultCenter.postNotificationName(
        aName = CALL_REGISTER_VIDEO_VIEW_NOTIFICATION,
        `object` = this,
        userInfo = mapOf(
            "participantId" to participantId,
            "mirror" to mirror,
        ),
    )
}
