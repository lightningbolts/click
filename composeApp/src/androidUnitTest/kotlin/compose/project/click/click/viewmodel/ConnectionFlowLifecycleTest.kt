package compose.project.click.click.viewmodel

import androidx.test.core.app.ApplicationProvider
import compose.project.click.click.data.repository.ConnectionRepository
import compose.project.click.click.data.storage.FakeTokenStorage
import compose.project.click.click.data.storage.initTokenStorage
import compose.project.click.click.proximity.MockProximityManager
import compose.project.click.click.utils.LocationService
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionFlowLifecycleTest {
    @Before
    fun setup() {
        initTokenStorage(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun resetDuringHandshakeCannotPublishALateResult() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val client = HttpClient()
            try {
                val vm = ConnectionViewModel(repository = ConnectionRepository(tokenStorage = FakeTokenStorage()))
                vm.startTapProximityHandshake(
                    httpClient = client,
                    proximityManager = MockProximityManager(),
                    jwt = "test-session",
                    currentUserId = "viewer",
                    locationService = LocationService(),
                    skipLocation = true,
                )
                runCurrent()
                assertEquals(ConnectionState.ProximityHandshaking, vm.connectionState.value)
                vm.resetConnectionState()
                advanceUntilIdle()
                assertTrue(vm.proximityHandshakeJob?.isCancelled == true)
                assertEquals(ConnectionState.Idle, vm.connectionState.value)
            } finally {
                client.close()
                Dispatchers.resetMain()
            }
        }

    @Test
    fun leavingPendingMatchCancelsPollingAndPreservesCompletedContext() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val vm = ConnectionViewModel(repository = ConnectionRepository(tokenStorage = FakeTokenStorage()))
                vm._connectionState.value = ConnectionState.ProximityHandshakePendingMatch()
                vm.startPendingProximityRecovery("pending", "test-session", "viewer")
                runCurrent()
                vm.resetConnectionState()
                advanceUntilIdle()
                assertTrue(vm.proximityRecoveryJob?.isCancelled == true)
                assertEquals(ConnectionState.Idle, vm.connectionState.value)
                val context = ConnectionState.TaggingContext(emptyList(), emptyList())
                vm._connectionState.value = context
                vm.cancelProximityHandshake()
                assertEquals(context, vm.connectionState.value)
            } finally {
                Dispatchers.resetMain()
            }
        }
}
