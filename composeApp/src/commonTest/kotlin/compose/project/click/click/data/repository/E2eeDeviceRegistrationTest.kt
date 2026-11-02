package compose.project.click.click.data.repository // pragma: allowlist secret

import compose.project.click.click.crypto.DeviceIdentityInfo
import compose.project.click.click.data.api.ChatDeviceAlreadyRegisteredException
import compose.project.click.click.data.api.ChatDeviceRevokedException
import compose.project.click.click.data.api.chatDeviceRegistrationFailure
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class E2eeDeviceRegistrationTest {
    @Test
    fun mapsServerResponses() {
        // Bodies from click-web app/api/chat/devices/route.ts.
        assertIs<ChatDeviceRevokedException>(
            chatDeviceRegistrationFailure(409, """{"error":"This device was removed from your account","code":"DEVICE_REVOKED"}"""),
        )
        assertIs<ChatDeviceAlreadyRegisteredException>(chatDeviceRegistrationFailure(409, """{"error":"Device already registered"}"""))
        val other = chatDeviceRegistrationFailure(500, "<html>")
        assertFalse(other is ChatDeviceRevokedException || other is ChatDeviceAlreadyRegisteredException)
    }

    /** A fake keystore: discarding the current key makes the next load create a new one. */
    private class Keys {
        var generation = 1
        val discarded = mutableListOf<String>()

        fun load() = DeviceIdentityInfo(deviceId = "device-$generation", publicKeySpkiBase64 = "pk-$generation")

        fun discard(id: String) {
            discarded += id
            if (id == "device-$generation") generation++
        }
    }

    @Test
    fun alreadyRegisteredIsSuccessWithoutRekey() =
        runTest {
            val keys = Keys()
            var replaced = 0
            val result =
                registerE2eeDeviceRekeyingIfRevoked(keys::load, { it }, keys::discard, { replaced++ }) {
                    Result.failure<Unit>(ChatDeviceAlreadyRegisteredException())
                }
            assertTrue(result.registered)
            assertEquals("device-1", result.identity.deviceId)
            assertEquals(0, replaced)
            assertTrue(keys.discarded.isEmpty())
        }

    @Test
    fun revokedKeyIsReplacedOnceAndRegistered() =
        runTest {
            val keys = Keys()
            var replaced = 0
            val calls = mutableListOf<String>()
            val result =
                registerE2eeDeviceRekeyingIfRevoked(keys::load, { it }, keys::discard, { replaced++ }) { info ->
                    calls += info.deviceId
                    if (info.deviceId == "device-1") Result.failure<Unit>(ChatDeviceRevokedException()) else Result.success(Unit)
                }
            assertTrue(result.registered)
            assertFalse(result.revoked)
            assertEquals("device-2", result.identity.deviceId)
            assertEquals(listOf("device-1", "device-2"), calls)
            assertEquals(listOf("device-1"), keys.discarded)
            assertEquals(1, replaced)
        }

    @Test
    fun aNewKeyTurnedAwayTooIsNotRetriedAgain() =
        runTest {
            val keys = Keys()
            var calls = 0
            val result =
                registerE2eeDeviceRekeyingIfRevoked(keys::load, { it }, keys::discard, {}) {
                    calls++
                    Result.failure<Unit>(ChatDeviceRevokedException())
                }
            assertEquals(2, calls)
            assertFalse(result.registered)
            assertTrue(result.revoked)
        }

    @Test
    fun keyThatCannotBeDiscardedIsReportedRevoked() =
        runTest {
            val stuck = DeviceIdentityInfo("device-1", "pk")
            var calls = 0
            val result =
                registerE2eeDeviceRekeyingIfRevoked({ stuck }, { it }, {}, {}) {
                    calls++
                    Result.failure<Unit>(ChatDeviceRevokedException())
                }
            assertEquals(1, calls)
            assertTrue(result.revoked)
        }
}
