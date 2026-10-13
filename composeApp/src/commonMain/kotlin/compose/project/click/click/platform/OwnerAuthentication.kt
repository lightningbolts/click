package compose.project.click.click.platform // pragma: allowlist secret

/** How asking the device owner to confirm it's them (biometrics, or the screen lock) ended. */
enum class OwnerAuthResult {
    Success,

    /** The owner (or the system) dismissed the prompt: nothing happens, and nothing is shown. */
    Cancelled,

    /** No screen lock is set up, or authentication failed for another reason: fail closed. */
    Unavailable,
}

/** The prompt's reason when approving another device (iOS `DeviceApprovalSheet.authenticateOwner`). */
const val DEVICE_APPROVAL_AUTH_REASON = "Confirm that you want to share Click message history with this device."

/**
 * Asks the device owner to confirm it's them: biometrics with the screen-lock PIN, pattern or
 * password as the fallback (iOS `.deviceOwnerAuthentication`). Never throws.
 */
expect suspend fun authenticateDeviceOwner(reason: String): OwnerAuthResult
