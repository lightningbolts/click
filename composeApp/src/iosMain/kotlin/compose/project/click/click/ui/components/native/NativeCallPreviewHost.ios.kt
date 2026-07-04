package compose.project.click.click.ui.components.native

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.uikit.LocalUIViewController
import compose.project.click.click.calls.CallOverlayState
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIAlertAction
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertActionStyleDestructive
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun NativeCallPreviewHost(
    overlayState: CallOverlayState,
    callerName: String,
    isVideo: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onDismissEnded: () -> Unit,
) {
    val viewController = LocalUIViewController.current
    var presentedState by remember { mutableStateOf<CallOverlayState>(CallOverlayState.Idle) }

    LaunchedEffect(overlayState) {
        if (overlayState is CallOverlayState.Idle) {
            if (presentedState !is CallOverlayState.Idle) {
                dispatch_async(dispatch_get_main_queue()) {
                    viewController.dismissViewControllerAnimated(true, completion = null)
                }
                presentedState = CallOverlayState.Idle
            }
            return@LaunchedEffect
        }
        if (presentedState == overlayState) return@LaunchedEffect
        presentedState = overlayState

        val title = when (overlayState) {
            is CallOverlayState.Outgoing -> if (isVideo) "Starting video call" else "Starting voice call"
            is CallOverlayState.Incoming -> if (isVideo) "Incoming video call" else "Incoming voice call"
            is CallOverlayState.Connecting -> if (isVideo) "Joining video call" else "Joining voice call"
            is CallOverlayState.Ended -> "Call ended"
            CallOverlayState.Idle -> return@LaunchedEffect
        }
        val message = when (overlayState) {
            is CallOverlayState.Ended -> overlayState.reason
            else -> callerName
        }

        val alert = UIAlertController.alertControllerWithTitle(
            title = title,
            message = message,
            preferredStyle = UIAlertControllerStyleAlert,
        )

        when (overlayState) {
            is CallOverlayState.Incoming -> {
                alert.addAction(
                    UIAlertAction.actionWithTitle(
                        title = "Decline",
                        style = UIAlertActionStyleDestructive,
                        handler = { onDecline() },
                    ),
                )
                alert.addAction(
                    UIAlertAction.actionWithTitle(
                        title = "Accept",
                        style = UIAlertActionStyleDefault,
                        handler = { onAccept() },
                    ),
                )
            }
            is CallOverlayState.Outgoing,
            is CallOverlayState.Connecting,
            -> {
                alert.addAction(
                    UIAlertAction.actionWithTitle(
                        title = "Cancel",
                        style = UIAlertActionStyleCancel,
                        handler = { onCancel() },
                    ),
                )
            }
            is CallOverlayState.Ended -> {
                alert.addAction(
                    UIAlertAction.actionWithTitle(
                        title = "OK",
                        style = UIAlertActionStyleDefault,
                        handler = { onDismissEnded() },
                    ),
                )
            }
            CallOverlayState.Idle -> Unit
        }

        dispatch_async(dispatch_get_main_queue()) {
            viewController.presentViewController(alert, animated = true, completion = null)
        }
    }
}
