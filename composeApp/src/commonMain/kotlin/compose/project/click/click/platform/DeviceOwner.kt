package compose.project.click.click.platform

/** What kind of device this is ("Android phone"), never its name. Shown when another device approves it. */
expect fun deviceKindLabel(): String

/**
 * Asks the device owner to confirm with biometrics or the screen lock before an approval goes out
 * (iOS `DeviceApprovalSheet.authenticateOwner`). Returns normally only on success.
 *
 * @throws DeviceOwnerAuthCancelled when the person backs out (nothing should change).
 * @throws DeviceOwnerAuthUnavailable when there is no screen lock or no way to prompt.
 */
expect suspend fun authenticateDeviceOwner(reason: String)

class DeviceOwnerAuthCancelled : Exception("Device authentication cancelled")

class DeviceOwnerAuthUnavailable(
    message: String = "Device authentication unavailable",
) : Exception(message)
