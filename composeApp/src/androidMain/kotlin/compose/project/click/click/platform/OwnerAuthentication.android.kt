package compose.project.click.click.platform

actual suspend fun authenticateDeviceOwner(reason: String): OwnerAuthResult {
    val context = AndroidActivityRuntime.currentActivity() ?: AndroidActivityRuntime.appContext() ?: return OwnerAuthResult.Unavailable
    return OwnerAuthActivity.authenticate(context, reason)
}
