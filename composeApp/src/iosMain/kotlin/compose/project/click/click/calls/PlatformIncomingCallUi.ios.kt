package compose.project.click.click.calls

import platform.Foundation.NSNotificationCenter

private const val IOS_NATIVE_INCOMING_CALL_NOTIFICATION = "ClickNativeIncomingCall"
private const val IOS_NATIVE_END_CALL_NOTIFICATION = "ClickNativeEndCall"
private const val IOS_NATIVE_ANSWER_CALL_NOTIFICATION = "ClickNativeAnswerCall"

actual object PlatformIncomingCallUi {
    actual fun showIncomingCall(invite: CallInvite) {
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = IOS_NATIVE_INCOMING_CALL_NOTIFICATION,
            `object` = null,
            userInfo = mapOf(
                "callId" to invite.callId,
                "connectionId" to invite.connectionId,
                "roomName" to invite.roomName,
                "callerId" to invite.callerId,
                "callerName" to invite.callerName,
                "calleeId" to invite.calleeId,
                "calleeName" to invite.calleeName,
                "videoEnabled" to invite.videoEnabled,
                "createdAt" to invite.createdAt,
            )
        )
    }

    actual fun dismissIncomingCall(callId: String, reason: String?) {
        if (reason == null) {
            // CallKit must receive CXAnswerCallAction when the user accepts from the in-app UI;
            // otherwise the system keeps ringing and Decline on the native sheet can cancel the call.
            NSNotificationCenter.defaultCenter.postNotificationName(
                aName = IOS_NATIVE_ANSWER_CALL_NOTIFICATION,
                `object` = null,
                userInfo = mapOf("callId" to callId),
            )
            return
        }

        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = IOS_NATIVE_END_CALL_NOTIFICATION,
            `object` = null,
            userInfo = mapOf(
                "callId" to callId,
                "reason" to reason,
            )
        )
    }
}