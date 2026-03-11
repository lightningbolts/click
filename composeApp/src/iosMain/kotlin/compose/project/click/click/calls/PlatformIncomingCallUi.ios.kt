package compose.project.click.click.calls

import platform.Foundation.NSNotificationCenter

private const val IOS_NATIVE_INCOMING_CALL_NOTIFICATION = "ClickNativeIncomingCall"
private const val IOS_NATIVE_END_CALL_NOTIFICATION = "ClickNativeEndCall"

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
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = IOS_NATIVE_END_CALL_NOTIFICATION,
            `object` = null,
            userInfo = mapOf(
                "callId" to callId,
                "reason" to (reason ?: "ended"),
            )
        )
    }
}