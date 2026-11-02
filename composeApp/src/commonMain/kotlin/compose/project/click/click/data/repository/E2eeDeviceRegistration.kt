package compose.project.click.click.data.repository

import compose.project.click.click.crypto.DeviceIdentityInfo
import compose.project.click.click.data.api.ChatDeviceRevokedException
import compose.project.click.click.data.api.isChatDeviceRegistered

/** Result of registering this device's E2EE v2 identity. */
internal data class E2eeDeviceRegistration<I>(
    /** The identity to use from now on (a new one when the old key was revoked). */
    val identity: I,
    val registered: Boolean,
    /** The server turned away a brand-new key too; this device can't take part in E2EE v2 now. */
    val revoked: Boolean = false,
)

/**
 * Registers this device (`POST /api/chat/devices`). If the server says the key was removed from the
 * account, discards it, makes a new one, and registers that **once** (iOS #81, web
 * `e2eeV2Client.registerDevice`). A new key turned away too is the server's answer, not a loop.
 */
internal suspend fun <I> registerE2eeDeviceRekeyingIfRevoked(
    loadIdentity: () -> I,
    infoOf: (I) -> DeviceIdentityInfo,
    discardIdentity: (deviceId: String) -> Unit,
    onIdentityReplaced: () -> Unit,
    register: suspend (DeviceIdentityInfo) -> Result<*>,
): E2eeDeviceRegistration<I> {
    val identity = loadIdentity()
    val first = register(infoOf(identity))
    if (first.exceptionOrNull() !is ChatDeviceRevokedException) {
        return E2eeDeviceRegistration(identity, first.isChatDeviceRegistered())
    }
    val revokedId = infoOf(identity).deviceId
    discardIdentity(revokedId)
    val replacement = loadIdentity()
    if (infoOf(replacement).deviceId == revokedId) {
        return E2eeDeviceRegistration(identity, registered = false, revoked = true)
    }
    onIdentityReplaced()
    val second = register(infoOf(replacement))
    return E2eeDeviceRegistration(
        identity = replacement,
        registered = second.isChatDeviceRegistered(),
        revoked = second.exceptionOrNull() is ChatDeviceRevokedException,
    )
}
