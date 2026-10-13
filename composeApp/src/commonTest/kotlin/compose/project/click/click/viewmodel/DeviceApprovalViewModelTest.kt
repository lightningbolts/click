package compose.project.click.click.viewmodel

import compose.project.click.click.chat.DeviceApprovalError
import compose.project.click.click.data.DeviceApprovals
import compose.project.click.click.platform.DEVICE_APPROVAL_AUTH_REASON
import compose.project.click.click.platform.OwnerAuthResult
import compose.project.click.click.viewmodel.DeviceApprovalViewModel.Phase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sheet 10 §A1 (owner authentication), §A2 (sharing outlives the sheet), §A4 (notification cleared). */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceApprovalViewModelTest {
    private val api = FakeDevicesApi()
    private val reasons = mutableListOf<String>()
    private val dismissed = mutableListOf<String>()
    private var userId: String? = "user-1"
    private val background = CoroutineScope(Job() + Dispatchers.Unconfined)

    @AfterTest
    fun tearDown() {
        DeviceApprovals.clear()
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(
        auth: suspend () -> OwnerAuthResult,
        onApproved: suspend () -> Unit = {},
    ): DeviceApprovalViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return DeviceApprovalViewModel(
            requestId = APPROVAL_REQUEST_ID,
            api = api,
            myDeviceId = { "android-device-1" },
            unwrap = { _, _ -> ByteArray(32) { it.toByte() } },
            currentUserId = { userId },
            authenticate = { reason ->
                reasons += reason
                auth()
            },
            backgroundScope = background,
            dismissNotification = { dismissed += it },
            onApproved = onApproved,
        )
    }

    @Test
    fun challengeIsFetchedDuringThePromptButTheProofWaitsForSuccess() =
        runTest {
            val gate = CompletableDeferred<OwnerAuthResult>()
            val vm = viewModel(auth = { gate.await() })
            assertEquals(Phase.Asking, vm.state.value.phase)

            vm.approve()
            assertEquals(Phase.Approving, vm.state.value.phase)
            assertEquals(listOf(DEVICE_APPROVAL_AUTH_REASON), reasons)
            assertTrue("challenge" in api.calls)
            assertTrue(api.decided.isEmpty())

            gate.complete(OwnerAuthResult.Success)
            assertEquals(Phase.Approved, vm.state.value.phase)
            assertEquals("approve", api.decided.single().decision)
            assertEquals(listOf("history", "challenge", "decide"), api.calls)
        }

    @Test
    fun cancelledGoesBackToAskingAndSendsNothing() =
        runTest {
            val vm = viewModel(auth = { OwnerAuthResult.Cancelled })
            vm.approve()
            assertEquals(Phase.Asking, vm.state.value.phase)
            assertTrue(api.decided.isEmpty())
            assertTrue(dismissed.isEmpty())
        }

    @Test
    fun unavailableFailsClosedWithTheAuthenticationCopy() =
        runTest {
            val vm = viewModel(auth = { OwnerAuthResult.Unavailable })
            vm.approve()
            val phase = vm.state.value.phase
            assertEquals(Phase.Failed(DeviceApprovalError.AuthenticationRequired), phase)
            assertEquals("Device authentication is required to approve this request.", (phase as Phase.Failed).error.message)
            assertTrue(api.decided.isEmpty())

            // "Try again" goes back to asking.
            vm.retry()
            assertEquals(Phase.Asking, vm.state.value.phase)
        }

    @Test
    fun anAccountSwitchDuringThePromptNeverPosts() =
        runTest {
            val gate = CompletableDeferred<OwnerAuthResult>()
            val vm = viewModel(auth = { gate.await() })
            vm.approve()
            userId = "user-2"
            gate.complete(OwnerAuthResult.Success)
            assertEquals(Phase.Asking, vm.state.value.phase)
            assertTrue(api.decided.isEmpty())
            assertTrue("decide" !in api.calls)
        }

    @Test
    fun denyNeverAsksForAuthentication() =
        runTest {
            val vm = viewModel(auth = { error("Deny must not authenticate") })
            vm.deny()
            assertEquals(Phase.Denied, vm.state.value.phase)
            assertTrue(reasons.isEmpty())
            assertEquals("deny", api.decided.single().decision)
            assertEquals(listOf(APPROVAL_REQUEST_ID), dismissed)
        }

    @Test
    fun approvedRightAwayWhileSharingRunsInTheAppScope() =
        runTest {
            val sharing = CompletableDeferred<Unit>()
            var shared = false
            val vm =
                viewModel(auth = { OwnerAuthResult.Success }, onApproved = {
                    sharing.await()
                    shared = true
                })
            vm.approve()
            // "Approved" shows before sharing finishes, and the share lives in the app scope, not the sheet's.
            assertEquals(Phase.Approved, vm.state.value.phase)
            assertEquals(1, background.coroutineContext[Job]!!.children.count())
            assertEquals(listOf(APPROVAL_REQUEST_ID), dismissed)
            sharing.complete(Unit)
            assertTrue(shared)
        }
}
