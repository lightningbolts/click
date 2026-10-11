package compose.project.click.click.data.auth

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionEndCoordinatorTest {
    private class Device(
        var signedIn: String?,
    ) {
        var teardowns = 0
        var credentialClears = 0

        /** Runs during the teardown, e.g. a magic-link sign-in landing mid sign-out. */
        var duringTeardown: () -> Unit = {}

        fun coordinator() =
            SessionEndCoordinator(
                currentUserId = { signedIn },
                teardown = {
                    teardowns++
                    yield()
                    duringTeardown()
                },
                clearCredentials = {
                    credentialClears++
                    signedIn = null
                },
            )
    }

    @Test
    fun aRejectedRefreshRunsTheTeardownOnce() =
        runTest {
            val device = Device(signedIn = "u1")
            val coordinator = device.coordinator()
            // Several requests 401 at once; each reports the rejected refresh.
            val results = (1..5).map { async { coordinator.endRejectedSession("u1") } }.awaitAll()
            assertEquals(1, results.count { it })
            assertEquals(1, device.teardowns)
            assertEquals(1, device.credentialClears)
            assertEquals(null, device.signedIn)
        }

    @Test
    fun aNewUserSigningInMidTeardownSurvives() =
        runTest {
            val device = Device(signedIn = "u1")
            device.duringTeardown = { device.signedIn = "u2" }
            val ended = device.coordinator().endRejectedSession("u1")
            assertFalse(ended)
            assertEquals(1, device.teardowns)
            assertEquals(0, device.credentialClears)
            assertEquals("u2", device.signedIn)
        }

    @Test
    fun aRejectionForAnotherAccountIsIgnored() =
        runTest {
            val device = Device(signedIn = "u2")
            assertFalse(device.coordinator().endRejectedSession("u1"))
            assertFalse(device.coordinator().endRejectedSession(null))
            assertEquals(0, device.teardowns)
        }

    @Test
    fun signingBackInArmsTheNextRejection() =
        runTest {
            val device = Device(signedIn = "u1")
            val coordinator = device.coordinator()
            assertTrue(coordinator.endRejectedSession("u1"))
            device.signedIn = "u1"
            assertFalse(coordinator.endRejectedSession("u1"))
            coordinator.onSignedIn("u1")
            assertTrue(coordinator.endRejectedSession("u1"))
            assertEquals(2, device.teardowns)
        }
}
