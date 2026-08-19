package compose.project.click.click.calls

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallPermissionResultPolicyTest {

    private val mic = "android.permission.RECORD_AUDIO"
    private val camera = "android.permission.CAMERA"

    @Test
    fun emptyLauncherMap_resumesWhenOsGranted() {
        assertTrue(
            CallPermissionResultPolicy.shouldResumeCall(
                requiredPermissions = listOf(mic),
                launcherResults = emptyMap(),
                osGrantedPermissions = setOf(mic),
            ),
        )
    }

    @Test
    fun emptyLauncherMap_doesNotResumeWhenOsDenied() {
        assertFalse(
            CallPermissionResultPolicy.shouldResumeCall(
                requiredPermissions = listOf(mic),
                launcherResults = emptyMap(),
                osGrantedPermissions = emptySet(),
            ),
        )
    }

    @Test
    fun launcherGrant_resumesEvenIfOsCheckRaces() {
        assertTrue(
            CallPermissionResultPolicy.shouldResumeCall(
                requiredPermissions = listOf(mic),
                launcherResults = mapOf(mic to true),
                osGrantedPermissions = emptySet(),
            ),
        )
    }

    @Test
    fun videoRequiresBothMicAndCamera() {
        assertFalse(
            CallPermissionResultPolicy.shouldResumeCall(
                requiredPermissions = listOf(mic, camera),
                launcherResults = mapOf(mic to true, camera to false),
                osGrantedPermissions = setOf(mic),
            ),
        )
        assertTrue(
            CallPermissionResultPolicy.shouldResumeCall(
                requiredPermissions = listOf(mic, camera),
                launcherResults = emptyMap(),
                osGrantedPermissions = setOf(mic, camera),
            ),
        )
    }

    @Test
    fun bluetoothDenialDoesNotBlockRequiredMic() {
        assertTrue(
            CallPermissionResultPolicy.shouldResumeCall(
                requiredPermissions = listOf(mic),
                launcherResults = mapOf(
                    mic to true,
                    "android.permission.BLUETOOTH_CONNECT" to false,
                ),
                osGrantedPermissions = setOf(mic),
            ),
        )
    }
}
