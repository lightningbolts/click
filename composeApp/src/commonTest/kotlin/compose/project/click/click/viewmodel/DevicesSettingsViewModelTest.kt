package compose.project.click.click.viewmodel

import compose.project.click.click.data.api.DeviceHistoryRequestDto
import compose.project.click.click.data.api.MyDeviceDto
import compose.project.click.click.data.api.parseClickWebCodedError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Settings › Devices (sheet 10 §A5). */
@OptIn(ExperimentalCoroutinesApi::class)
class DevicesSettingsViewModelTest {
    private val phone = MyDeviceDto("ios-1", "iPhone", "2026-10-01T10:00:00Z", "2026-10-09T08:30:00Z")
    private val me = MyDeviceDto("android-1", "Pixel 9", "2026-09-01T00:00:00Z", null)
    private val web = MyDeviceDto("web-1", null, "2026-09-20T10:00:00Z", null)
    private val online = MutableStateFlow(true)
    private val deferred = mutableListOf<String>()

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(api: FakeDevicesApi): DevicesSettingsViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return DevicesSettingsViewModel(api = api, myDeviceId = { "android-1" }, isOnline = online, deferApproval = { deferred += it })
    }

    @Test
    fun loadsDevicesThisDeviceFirstAndTheWaitingRequests() =
        runTest {
            val api = FakeDevicesApi(devices = { Result.success(listOf(phone, me, web)) })
            val vm = viewModel(api)
            vm.load()
            val state = vm.state.value
            assertEquals(listOf("android-1", "ios-1", "web-1"), state.orderedDevices.map { it.deviceId })
            assertEquals(listOf(APPROVAL_REQUEST_ID), state.incoming.map { it.id })
            assertFalse(state.loading)
        }

    @Test
    fun devicesStillShowWhenTheApprovalsCallFails() =
        runTest {
            val api =
                FakeDevicesApi(
                    historyResult = { Result.failure(IllegalStateException("offline")) },
                    devices = { Result.success(listOf(phone, me)) },
                )
            val vm = viewModel(api)
            vm.load()
            assertEquals(2, vm.state.value.devices?.size)
            assertTrue(vm.state.value.incoming.isEmpty())
            assertFalse(vm.state.value.loadFailed)
        }

    @Test
    fun removeIsOptimisticAndRollsBackWithTheReason() =
        runTest {
            val api =
                FakeDevicesApi(
                    devices = { Result.success(listOf(me, phone, web)) },
                    removeResult = Result.failure(parseClickWebCodedError(500, """{"error":"Could not remove device"}""")),
                )
            api.removeGate = CompletableDeferred()
            val vm = viewModel(api)
            vm.load()

            vm.remove(phone)
            assertEquals(listOf("android-1", "web-1"), vm.state.value.devices?.map { it.deviceId })
            api.removeGate!!.complete(Unit)

            assertEquals(listOf("android-1", "ios-1", "web-1"), vm.state.value.devices?.map { it.deviceId })
            assertEquals("Couldn't remove iPhone. Could not remove device", vm.state.value.error)
            assertEquals(listOf("ios-1"), api.removed)
        }

    @Test
    fun anUnlabelledDeviceThatCantBeReachedSaysThatDevice() =
        runTest {
            val api =
                FakeDevicesApi(
                    devices = { Result.success(listOf(me, web)) },
                    removeResult = Result.failure(IllegalStateException("Unable to resolve host")),
                )
            val vm = viewModel(api)
            vm.load()
            vm.remove(web)
            assertEquals(listOf("android-1", "web-1"), vm.state.value.devices?.map { it.deviceId })
            assertEquals("Couldn't remove that device. Check your connection and try again.", vm.state.value.error)
        }

    @Test
    fun removeSucceedsAndNeverTouchesThisDevice() =
        runTest {
            val api = FakeDevicesApi(devices = { Result.success(listOf(me, phone)) })
            val vm = viewModel(api)
            vm.load()
            vm.remove(me)
            assertTrue(api.removed.isEmpty())
            vm.remove(phone)
            assertEquals(listOf("android-1"), vm.state.value.devices?.map { it.deviceId })
            assertNull(vm.state.value.error)
        }

    @Test
    fun reviewDefersTheGlobalPromptAndADecisionDropsTheRow() =
        runTest {
            val api = FakeDevicesApi(devices = { Result.success(listOf(me)) })
            val vm = viewModel(api)
            vm.load()
            vm.review(APPROVAL_REQUEST_ID)
            assertEquals(listOf(APPROVAL_REQUEST_ID), deferred)
            assertEquals(APPROVAL_REQUEST_ID, vm.state.value.reviewing)

            vm.reviewDecided(APPROVAL_REQUEST_ID)
            assertTrue(vm.state.value.incoming.isEmpty())

            api.incoming = emptyList<DeviceHistoryRequestDto>()
            val before = api.devicesCalls
            vm.reviewDismissed()
            assertNull(vm.state.value.reviewing)
            assertEquals(before + 1, api.devicesCalls)
        }

    @Test
    fun comingBackOnlineReloadsWhenThereIsNoListYet() =
        runTest {
            online.value = false
            var fail = true
            val api = FakeDevicesApi(devices = { if (fail) Result.failure(IllegalStateException("offline")) else Result.success(listOf(me)) })
            val vm = viewModel(api)
            vm.load()
            assertTrue(vm.state.value.loadFailed)
            assertFalse(vm.state.value.online)

            fail = false
            online.value = true
            assertEquals(2, api.devicesCalls)
            assertEquals(listOf("android-1"), vm.state.value.devices?.map { it.deviceId })
            assertTrue(vm.state.value.online)
        }
}
