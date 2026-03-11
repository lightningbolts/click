package compose.project.click.click.calls

expect object PlatformIncomingCallUi {
    fun showIncomingCall(invite: CallInvite)

    fun dismissIncomingCall(callId: String, reason: String? = null)
}